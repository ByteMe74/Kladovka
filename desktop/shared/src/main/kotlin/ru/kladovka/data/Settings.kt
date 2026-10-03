package ru.kladovka.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/** Настройки приложения. Хранятся рядом с базой, в `settings.properties`. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val serverUrl: String = ApiClient.DEFAULT_URL,
    val dataDir: String = ""
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
            dataDir = dataDir.toString()
        )
    }

    fun save(dataDir: Path, s: AppSettings) {
        val p = Properties()
        p.setProperty("themeMode", s.themeMode.name)
        p.setProperty("serverUrl", s.serverUrl)
        Files.newBufferedWriter(dataDir.resolve("settings.properties")).use { p.store(it, "Кладовка") }
    }

    /**
     * Каталог данных: системный каталог пользователя, а не рабочая папка.
     * Иначе база оказывалась бы рядом с exe и терялась при переустановке.
     */
    fun defaultDataDir(): Path =
        Paths.get(System.getProperty("user.home"), ".kladovka").also { Files.createDirectories(it) }
}
