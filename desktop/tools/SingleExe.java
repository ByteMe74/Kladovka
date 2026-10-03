import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Упаковщик единого .exe для «Кладовки».
 *
 * К заглушке, собранной rustc, приклеивается payload — jlink-рантайм и uber-jar.
 * Итоговый файл не требует ни папки рядом, ни установленной Java.
 *
 * Раскладка файла:
 *   [заглушка] [данные файлов] [индекс] [u64 LE — абсолютное смещение индекса]
 *
 * Индекс: u32 LE «сколько файлов», затем на каждый u32 LE «длина пути»,
 * путь в UTF-8, u64 LE смещение и u64 LE длина. Путь у каждой записи свой —
 * общий путь означал бы, что все файлы пишутся поверх одного и того же.
 *
 * Запуск:
 *   java SingleExe hash   &lt;каталог рантайма&gt; &lt;jar&gt;
 *   java SingleExe pack   &lt;заглушка.exe&gt; &lt;выход.exe&gt; &lt;каталог рантайма&gt; &lt;jar&gt;
 *   java SingleExe verify &lt;собранный.exe&gt; &lt;каталог рантайма&gt; &lt;jar&gt;
 */
public class SingleExe {

    private static final String RUNTIME_ENTRY = "app/" + "kladovka.jar";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("нужен режим: hash | pack");
            System.exit(2);
        }
        switch (args[0]) {
            case "hash" -> {
                if (args.length != 3) {
                    System.err.println("hash: <каталог рантайма> <jar>");
                    System.exit(2);
                }
                System.out.println(hash(List.of(new FileSpec(Path.of(args[1]), null), new FileSpec(Path.of(args[2]), RUNTIME_ENTRY))));
            }
            case "pack" -> {
                // «pack» + заглушка + результат + каталог рантайма + jar
                if (args.length != 5) {
                    System.err.println("pack: <заглушка.exe> <выход.exe> <каталог рантайма> <jar>");
                    System.exit(2);
                }
                pack(Path.of(args[1]), Path.of(args[2]), List.of(new FileSpec(Path.of(args[3]), null), new FileSpec(Path.of(args[4]), RUNTIME_ENTRY)));
            }
            case "verify" -> {
                // verify: <собранный.exe> <каталог рантайма> <jar>
                if (args.length != 4) {
                    System.err.println("verify: <собранный.exe> <каталог рантайма> <jar>");
                    System.exit(2);
                }
                boolean ok = verify(Path.of(args[1]), List.of(new FileSpec(Path.of(args[2]), null), new FileSpec(Path.of(args[3]), RUNTIME_ENTRY)));
                if (!ok) System.exit(1);
            }
            default -> {
                System.err.println("неизвестный режим: " + args[0]);
                System.exit(2);
            }
        }
    }

    // ---------------------------------------------------------------- verify

    private record IndexEntry(String path, long offset, long len) {}

    /**
     * Перечитывает индекс из собранного exe и сверяет каждый файл с исходником.
     *
     * Нужна потому, что упаковка умеет завершиться «успешно», оставив в индексе
     * неверные смещения: сборка зелёная, а exe не запускается. Ловим это здесь.
     */
    private static boolean verify(Path exe, List<FileSpec> roots) throws Exception {
        byte[] all = Files.readAllBytes(exe);
        if (all.length < 16) {
            System.err.println("ПРОВАЛ: файл меньше 16 байт");
            return false;
        }
        long indexOff = readLongLE(all, all.length - 8);
        if (indexOff < 0 || indexOff + 8 > all.length) {
            System.err.println("ПРОВАЛ: смещение индекса " + indexOff + " вне файла (" + all.length + ")");
            return false;
        }

        long pos = indexOff;
        int count = (int) readIntLE(all, pos);
        pos += 4;

        List<IndexEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int pathLen = (int) readIntLE(all, pos);
            pos += 4;
            if (pathLen < 0 || pos + pathLen + 16 > all.length) {
                System.err.println("ПРОВАЛ: запись " + i + " — длина пути " + pathLen + " не влезает");
                return false;
            }
            String path = new String(all, (int) pos, pathLen, StandardCharsets.UTF_8);
            pos += pathLen;
            long off = readLongLE(all, pos);
            long len = readLongLE(all, pos + 8);
            pos += 16;
            if (off < 0 || len < 0 || off + len > all.length) {
                System.err.println("ПРОВАЛ: " + path + " — данные [" + off + ", +" + len + ") вне файла");
                return false;
            }
            entries.add(new IndexEntry(path, off, len));
        }

        // Ожидаемый набор: те же пути, что и при упаковке.
        List<String> expected = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();
        for (FileSpec spec : roots) {
            for (Path p : walk(spec.local())) {
                String archive = spec.archivePath() != null && Files.isRegularFile(spec.local())
                    ? spec.archivePath()
                    : spec.local().relativize(p).toString().replace('\\', '/');
                expected.add(archive);
                sources.put(archive, p);
            }
        }

        List<String> got = entries.stream().map(IndexEntry::path).toList();
        if (!got.equals(expected)) {
            System.err.println("ПРОВАЛ: состав файлов не совпал.");
            System.err.println("  ожидалось " + expected.size() + " файлов, в индексе " + got.size());
            for (String s : expected) {
                if (!got.contains(s)) System.err.println("  нет в индексе: " + s);
            }
            for (String s : got) {
                if (!expected.contains(s)) System.err.println("  лишний в индексе: " + s);
            }
            return false;
        }

        // Сверяем содержимое по хэшу — смещение может быть верным по длине, но не тем.
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        int bad = 0;
        for (IndexEntry e : entries) {
            md.reset();
            md.update(all, (int) e.offset(), (int) e.len());
            String inExe = HexFormat.of().formatHex(md.digest());
            String source = sha256(Files.readAllBytes(sources.get(e.path())));
            if (!inExe.equals(source)) {
                System.err.println("ПРОВАЛ: содержимое не совпало для " + e.path()
                        + " (в exe " + inExe.substring(0, 12) + ", исходник " + source.substring(0, 12) + ")");
                bad++;
            }
        }
        if (bad > 0) {
            System.err.println("ПРОВАЛ: файлов с неверными данными — " + bad);
            return false;
        }

        System.out.println("  проверено файлов: " + entries.size()
                + ", байт: " + entries.stream().mapToLong(IndexEntry::len).sum());
        System.out.println("  все данные в exe совпали с исходниками");
        return true;
    }

    private static int readIntLE(byte[] b, long pos) {
        return (b[(int) pos] & 0xFF)
                | ((b[(int) pos + 1] & 0xFF) << 8)
                | ((b[(int) pos + 2] & 0xFF) << 16)
                | ((b[(int) pos + 3] & 0xFF) << 24);
    }

    private static long readLongLE(byte[] b, long pos) {
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (b[(int) pos + i] & 0xFFL);
        }
        return v;
    }

    /** Локальный файл и его путь внутри распакованной директории (null — сохранить как есть). */
    private record FileSpec(Path local, String archivePath) {}

    // ------------------------------------------------------------------ hash

    /**
     * Стабильный хэш содержимого. Один и тот же набор файлов всегда даёт одно
     * значение — это то, что отличает одну распаковку от другой, поэтому
     * нельзя включать сюда время модификации.
     */
    private static String hash(List<FileSpec> roots) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        List<String> lines = new ArrayList<>();
        for (FileSpec spec : roots) {
            for (Path p : walk(spec.local())) {
                String archive = spec.archivePath() != null && Files.isRegularFile(spec.local())
                    ? spec.archivePath()
                    : spec.local().relativize(p).toString().replace('\\', '/');
                lines.add(archive + "  " + sha256(Files.readAllBytes(p)));
            }
        }
        lines.sort(Comparator.naturalOrder());
        for (String l : lines) {
            md.update(l.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
        }
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }

    private static List<Path> walk(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of(root);
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    // ------------------------------------------------------------------ pack

    private static void pack(Path stub, Path out, List<FileSpec> roots) throws IOException {
        byte[] stubBytes = Files.readAllBytes(stub);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.deleteIfExists(out);

        // Собираем список (путь внутри архива, данные) в стабильном порядке —
        // от этого зависит воспроизводимость сборки.
        List<String> archivePaths = new ArrayList<>();
        List<byte[]> blobs = new ArrayList<>();
        for (FileSpec spec : roots) {
            for (Path p : walk(spec.local())) {
                String archive = spec.archivePath() != null && Files.isRegularFile(spec.local())
                    ? spec.archivePath()
                    : spec.local().relativize(p).toString().replace('\\', '/');
                archivePaths.add(archive);
                blobs.add(Files.readAllBytes(p));
            }
        }

        if (archivePaths.size() != blobs.size()) {
            throw new IOException("внутренняя ошибка: расхождение числа путей и данных");
        }

        long dataBytes = 0;
        for (byte[] b : blobs) {
            dataBytes += b.length;
        }

        try (OutputStream os = Files.newOutputStream(out)) {
            os.write(stubBytes);
            long cursor = stubBytes.length;

            // Данные
            for (byte[] b : blobs) {
                os.write(b);
                cursor += b.length;
            }
            long indexOffset = cursor;

            // Индекс: у каждого файла свой путь, смещение и длина.
            ByteBuffer head = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            head.putInt(blobs.size());
            os.write(head.array());

            long entryOffset = stubBytes.length;
            for (int i = 0; i < blobs.size(); i++) {
                byte[] pathBytes = archivePaths.get(i).getBytes(StandardCharsets.UTF_8);
                ByteBuffer rec = ByteBuffer.allocate(4 + pathBytes.length + 16).order(ByteOrder.LITTLE_ENDIAN);
                rec.putInt(pathBytes.length);
                rec.put(pathBytes);
                rec.putLong(entryOffset);
                rec.putLong(blobs.get(i).length);
                os.write(rec.array());
                entryOffset += blobs.get(i).length;
            }

            ByteBuffer tail = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            tail.putLong(indexOffset);
            os.write(tail.array());
        }

        long finalSize = Files.size(out);
        ByteArrayOutputStream note = new ByteArrayOutputStream();
        note.write(("  заглушка: " + stubBytes.length + " байт\n").getBytes(StandardCharsets.UTF_8));
        note.write(("  файлов в payload: " + blobs.size() + "\n").getBytes(StandardCharsets.UTF_8));
        note.write(("  данные: " + dataBytes + " байт\n").getBytes(StandardCharsets.UTF_8));
        note.write(("  итог: " + finalSize + " байт\n").getBytes(StandardCharsets.UTF_8));
        System.out.print(note.toString(StandardCharsets.UTF_8));
        System.out.println("  записано: " + out.toAbsolutePath());
    }
}
