package ru.kladovka.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/** Настройки приложения. Хранятся рядом с базой, в `settings.properties`. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val serverUrl: String = ApiClient.DEFAULT_URL,
    val dataDir: String = "",
    /**
     * Токен авторизации.
     *
     * Хранится, чтобы сессия переживала перезапуск без лишнего входа. Но токен
     * сервер может отозвать в любой момент — в том числе при выходе на сайте, —
     * и тогда одних его данных мало: нужен пароль, чтобы получить новый.
     */
    val token: String = "",
    /** Под кем вошли — нужно для списка совместного доступа и профиля. */
    val username: String = "",
    /**
     * Пароль от сервера — как в Android-приложении, где он тоже лежит в
     * SharedPreferences и используется для тихого автологина при запуске.
     * Без него после отзыва токена пришлось бы вводить пароль заново.
     * Файл настроек лежит рядом с базой в каталоге пользователя; это пароль
     * от собственного сервера пользователя, не от стороннего сервиса.
     */
    val password: String = ""
)

/** Режим темы продублирован здесь, чтобы слой данных не зависел от Compose. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Загрузка и сохранение настроек. */
object SettingsStore {

    fun load(dataDir: Path): AppSettings {
        val f = dataDir.resolve("settings.properties")
        if (!Files.exists(f)) return AppSettings(dataDir = dataDir.toString())
        val p = Properties()
        Files.newBufferedReader(f).use { p.load(it) }
        return AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p.getProperty("themeMode", "SYSTEM")) }
                .getOrDefault(ThemeMode.SYSTEM),
            serverUrl = p.getProperty("serverUrl", ApiClient.DEFAULT_URL),
            dataDir = dataDir.toString(),
            token = p.getProperty("token", ""),
            username = p.getProperty("username", ""),
            password = p.getProperty("password", "")
        )
    }

    fun save(dataDir: Path, s: AppSettings) {
        val p = Properties()
        p.setProperty("themeMode", s.themeMode.name)
        p.setProperty("serverUrl", s.serverUrl)
        p.setProperty("token", s.token)
        p.setProperty("username", s.username)
        p.setProperty("password", s.password)
        Files.newBufferedWriter(dataDir.resolve("settings.properties")).use { p.store(it, "Кладовка") }
    }

    /**
     * Каталог данных: системный каталог пользователя, а не рабочая папка.
     * Иначе база оказывалась бы рядом с exe и терялась при переустановке.
     */
    fun defaultDataDir(): Path =
        Paths.get(System.getProperty("user.home"), ".kladovka").also { Files.createDirectories(it) }
}
