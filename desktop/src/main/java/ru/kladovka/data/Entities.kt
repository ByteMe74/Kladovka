// Настройки приложения
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSync: Boolean = true,
    val syncInterval: Int = 15, // минут
    val serverUrl: String = ""
)

// Мутация настроек (для локальных изменений в UI)
data class SettingsMutation(
    val themeMode: ThemeMode? = null,
    val autoSync: Boolean? = null,
    val syncInterval: Int? = null,
    val serverUrl: String? = null
)
