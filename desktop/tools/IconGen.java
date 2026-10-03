import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Генератор иконок «Кладовки» для Desktop.
 *
 * Геометрия ниже в точности повторяет вектор Android-приложения
 * (app/src/main/res/drawable/ic_launcher_foreground.xml) и цвет подложки
 * (values/colors.xml → ic_launcher_background), поэтому значок на компьютере
 * выглядит так же, как на телефоне.
 *
 * Запуск:
 *   javac -d tools/out tools/IconGen.java
 *   java -cp tools/out IconGen kladovka-desktop/icons
 *
 * Форматы: PNG (Linux/универсальный), ICO (Windows), ICNS (macOS).
 */
public class IconGen {

    // ---- палитра: та же, что в Theme.kt и в colors.xml ------------------------
    private static final Color BACKGROUND = new Color(0x00, 0x69, 0x6B);
    private static final Color WHITE      = new Color(0xFF, 0xFF, 0xFF);
    private static final Color AMBER      = new Color(0xFF, 0xB8, 0x76);

    /** Сетка исходного вектора. */
    private static final double GRID = 108.0;

    /**
     * Радиус скругления иконки. Android наносит маску сам (у launchers она своя),
     * на компьютере скругление рисуется здесь — 22% от стороны, как у squircle.
     */
    private static final double CORNER_RATIO = 0.22;

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        double s = size / GRID;

        // Подложка
        double radius = size * CORNER_RATIO;
        g.setColor(BACKGROUND);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2));

        // Крышка: скруглённый прямоугольник x 26..82, y 45..53, радиус 2
        g.setColor(WHITE);
        g.fill(roundRect(26, 45, 56, 8, 2, s));

        // Замок на крышке (янтарный).
        // В Android-векторе (ic_launcher_foreground.xml) этот круг нарисован ДО крышки,
        // и она закрывает его целиком: наружу выходит меньше пикселя — в Android
        // замок не виден вовсе. По замыслу автора («ярлык/замок на крышке») он должен
        // быть виден, поэтому здесь рисуется поверх крышки. Расхождение с телефоном
        // осознанное; если нужно точное совпадение — верните блок выше крышки.
        g.setColor(AMBER);
        g.fill(new Ellipse2D.Double((54 - 4) * s, (48.8 - 4) * s, 8 * s, 8 * s));

        // Ручка-скобка: x 44..64, y 40.5..44, радиус 2
        g.setColor(WHITE);
        g.fill(roundRect(44, 40.5, 20, 3.5, 2, s));

        // Тело коробки: x 30..78, y 58..82, радиус 2
        g.fill(roundRect(30, 58, 48, 24, 2, s));

        // Вещи внутри коробки (янтарные): три прямоугольника y 67..74
        g.setColor(AMBER);
        g.fill(rect(37, 67, 11, 7, s));
        g.fill(rect(51, 67, 11, 7, s));
        g.fill(rect(65, 67, 8, 7, s));

        g.dispose();
        return img;
    }

    private static Shape roundRect(double x, double y, double w, double h, double r, double s) {
        return new RoundRectangle2D.Double(x * s, y * s, w * s, h * s, r * 2 * s, r * 2 * s);
    }

    private static Shape rect(double x, double y, double w, double h, double s) {
        return new Rectangle2D.Double(x * s, y * s, w * s, h * s);
    }

    // ---------------------------------------------------------------- ICO

    /**
     * ICO с PNG-записями (поддерживается начиная с Windows Vista).
     * Размер 256 кодируется нулём в байте ширины/высоты.
     */
    private static void writeIco(Path out, int[] sizes) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int sz : sizes) {
            images.add(toPng(render(sz)));
        }

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0);            // reserved
        head.putShort((short) 1);            // type: icon
        head.putShort((short) sizes.length); // count

        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int sz = sizes[i];
            head.put((byte) (sz == 256 ? 0 : sz));
            head.put((byte) (sz == 256 ? 0 : sz));
            head.put((byte) 0);              // палитра не используется
            head.put((byte) 0);              // reserved
            head.putShort((short) 1);        // planes
            head.putShort((short) 32);       // bits per pixel
            head.putInt(images.get(i).length);
            head.putInt(offset);
            offset += images.get(i).length;
        }

        outBytes.write(head.array());
        for (byte[] img : images) outBytes.write(img);
        Files.write(out, outBytes.toByteArray());
        System.out.println("  " + out.getFileName() + "  (" + sizes.length + " размеров, " + outBytes.size() / 1024 + " КБ)");
    }

    // ---------------------------------------------------------------- ICNS

    /**
     * ICNS для macOS. Записи ic11..ic14 — это, соответственно, 32, 64, 256 и 512
     * пикселей в @2x-варианте, ic07..ic10 — обычные размеры.
     */
    private static void writeIcns(Path out) throws IOException {
        // тип -> размер в пикселях
        Object[][] entries = {
            {"ic11", 32}, {"ic12", 64}, {"ic13", 256}, {"ic14", 512},
            {"ic07", 128}, {"ic08", 256}, {"ic09", 512}, {"ic10", 1024},
        };

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Object[] e : entries) {
            String type = (String) e[0];
            int sz = (Integer) e[1];
            byte[] png = toPng(render(sz));
            ByteBuffer h = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
            h.put(type.getBytes("US-ASCII"));
            h.putInt(png.length + 8);
            body.write(h.array());
            body.write(png);
        }

        ByteBuffer file = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        file.put("icns".getBytes("US-ASCII"));
        file.putInt(body.size() + 8);

        Files.write(out, concat(file.array(), body.toByteArray()));
        System.out.println("  " + out.getFileName() + "  (" + entries.length + " записей, " + (body.size() + 8) / 1024 + " КБ)");
    }

    // ---------------------------------------------------------------- util

    private static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "icons");
        Files.createDirectories(dir);

        System.out.println("Иконки → " + dir.toAbsolutePath());

        for (int sz : new int[]{256, 512}) {
            Path p = dir.resolve("kladovka-" + sz + ".png");
            ImageIO.write(render(sz), "png", p.toFile());
            System.out.println("  " + p.getFileName() + "  (" + sz + "×" + sz + ")");
        }

        writeIco(dir.resolve("kladovka.ico"),
            new int[]{16, 24, 32, 48, 64, 128, 256});

        writeIcns(dir.resolve("kladovka.icns"));

        System.out.println("Готово.");
    }
}