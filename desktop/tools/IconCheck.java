import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

/**
 * Проверяет, что каждая иконка, на которую ссылается код, реально отрисовывается
 * из того uber-jar, который уйдёт в exe.
 *
 * Зачем это нужно отдельно от проверки имён в фильтре. Отсутствующий класс
 * иконки не ломает сборку и не ломает запуск — он выстреливает
 * NoClassDefFoundError в момент отрисовки вкладки, то есть уже у пользователя.
 * А если проверять по тому же списку keptIcons, которым пользуется фильтр, то
 * вычёркивание строки из списка пройдёт обе проверки разом: список валидировал
 * бы сам себя.
 *
 * Поэтому источник истины здесь — исходники. Сканируются import'ы вида
 * androidx.compose.material.icons.*, к ним добавляются иконки, которые
 * material3 использует внутри своих компонентов (их в наших файлах не видно,
 * jdeps нашёл их в байткоде), и всё это прогоняется: класс должен найтись,
 * геттер должен вызваться, вектор должен быть непустым.
 *
 * Запуск: java -cp <uber-jar> IconCheck.java <каталог проекта> <иконка>...
 */
public class IconCheck {

    /**
     * Иконки, которые material3 зовёт изнутри и которых нет ни в одном нашем
     * import'е. Список взят из jdeps -verbose:class по исходному uber-jar.
     * Убраны — и SegmentedButton, ExposedDropdownMenu, DatePicker и Snackbar
     * падут NoClassDefFoundError при открытии соответствующего компонента.
     */
    private static final String[] INTERNAL = {
        "filled/ArrowDropDown", "filled/Check", "filled/Close", "filled/DateRange",
        "automirrored/filled/KeyboardArrowLeft",
        "automirrored/filled/KeyboardArrowRight",
    };

    private static final Pattern IMPORT = Pattern.compile(
        "^\\s*import\\s+androidx\\.compose\\.material\\.icons\\.([\\w./]+)\\s*$",
        Pattern.MULTILINE);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("нужны: <каталог проекта> <иконка>...");
            System.exit(2);
        }
        Path root = Path.of(args[0]);

        // Иконки из наших исходников — по import'ам, а не по упоминаниям в коде:
        // импорт обязателен для компиляции, значит его наличие проверяемо.
        Set<String> required = new LinkedHashSet<>();
        for (String s : scanImports(root)) {
            required.add(s);
        }
        for (String s : INTERNAL) {
            required.add(s);
        }

        // Что оставлено в jar — передаёт build.gradle.kts тем же списком, что
        // использует фильтр. Сравниваем, чтобы лишнее не везлось молча.
        Set<String> kept = new LinkedHashSet<>();
        for (int i = 1; i < args.length; i++) {
            kept.add(args[i]);
        }

        Set<String> dead = new LinkedHashSet<>(kept);
        dead.removeAll(required);
        if (!dead.isEmpty()) {
            System.out.println("в keptIcons есть то, на что никто не ссылается: "
                + dead);
        }

        Class<?> imageVector = Class.forName(
            "androidx.compose.ui.graphics.vector.ImageVector");
        Method getRoot = imageVector.getMethod("getRoot");
        Method viewport = imageVector.getMethod("getViewportWidth");

        int failed = 0;
        for (String icon : required) {
            try {
                String report = check(icon, imageVector, getRoot, viewport);
                System.out.printf("  ok  %-42s %s%n", icon, report);
            } catch (Throwable t) {
                String why = t instanceof ClassNotFoundException
                    ? "НЕТ КЛАССА в jar"
                    : (t instanceof NoSuchMethodException
                        ? "НЕТ МЕТОДА " + t.getMessage()
                        : t.getClass().getSimpleName() + ": " + t.getMessage());
                System.out.printf("  ПРОВАЛ %-38s %s%n", icon, why);
                failed++;
            }
        }

        System.out.printf("%nтребуется иконок: %d (из исходников: %d, " +
                "внутренних material3: %d), оставлено в jar: %d, ошибок: %d%n",
            required.size(), required.size() - INTERNAL.length,
            INTERNAL.length, kept.size(), failed);
        if (failed > 0) {
            System.exit(1);
        }
        System.out.println("все иконки, на которые ссылается код, отрисовываются");
    }

    private static String check(String icon, Class<?> imageVector,
                                Method getRoot, Method viewport) throws Exception {
        String[] parts = icon.split("/");
        String name = parts[parts.length - 1];
        String cls = "androidx.compose.material.icons."
            + icon.replace('/', '.') + "Kt";

        // filled/Add -> Icons$Filled
        // automirrored/filled/Add -> Icons$AutoMirrored$Filled
        StringBuilder style = new StringBuilder("Icons");
        for (int i = 0; i < parts.length - 1; i++) {
            String seg = parts[i];
            style.append('$').append(seg.equals("automirrored")
                ? "AutoMirrored"
                : Character.toUpperCase(seg.charAt(0)) + seg.substring(1));
        }

        Class<?> kt = Class.forName(cls);
        Class<?> receiver = Class.forName(
            "androidx.compose.material.icons." + style);
        Object instance = receiver.getField("INSTANCE").get(null);
        Object vector = kt.getMethod("get" + name, receiver)
            .invoke(null, instance);

        Object root = getRoot.invoke(vector);
        int nodes = ((Number) root.getClass()
            .getMethod("getSize").invoke(root)).intValue();
        float vw = ((Number) viewport.invoke(vector)).floatValue();
        if (nodes <= 0) {
            throw new IllegalStateException("пустой вектор, 0 узлов");
        }
        if (vw <= 0f) {
            throw new IllegalStateException("пустой viewport " + vw);
        }
        return String.format("%d узлов, viewport %s", nodes, vw);
    }

    private static List<String> scanImports(Path root) throws IOException {
        List<String> found = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                String s = p.toString();
                if (!s.endsWith(".kt")) continue;
                if (s.contains("\\build\\") || s.contains("/build/")) continue;
                if (s.contains("\\deskforce\\") || s.contains("/deskforce/")) continue;
                String text = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = IMPORT.matcher(text);
                while (m.find()) {
                    // Группа — это остаток после "icons.", то есть "filled.Add"
                    // через точку. Превращаем в путь "filled/Add": иначе
                    // проверка на наличие "/" всегда ложна и из исходников не
                    // читается ни одной иконки.
                    String path = m.group(1).replace('.', '/');
                    // "Icons" без вложенности — это сам объект, не иконка.
                    if (path.contains("/") && !found.contains(path)) {
                        found.add(path);
                    }
                }
            }
        }
        found.sort(String::compareTo);
        return found;
    }
}
