package ru.kladovka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.kladovka.data.ApiClient
import ru.kladovka.data.AppSettings
import ru.kladovka.data.SettingsStore
import ru.kladovka.data.SqliteDatabase
import ru.kladovka.data.ThemeMode
import ru.kladovka.ui.KladovkaTheme
import ru.kladovka.ui.MainScreen
import ru.kladovka.ui.SettingsScreen
import ru.kladovka.ui.SyncScreen

/**
 * Кладовка Desktop — порт Android-приложения на Compose Desktop.
 *
 * Единственная точка входа проекта (mainClass = "ru.kladovka.MainKt").
 */
fun main() = application {
    val dataDir = remember { SettingsStore.defaultDataDir() }
    val db = remember { SqliteDatabase(dataDir.resolve("kladovka.db")) }
    val api = remember { ApiClient() }
    var settings by remember { mutableStateOf(SettingsStore.load(dataDir)) }
    api.baseUrl = settings.serverUrl

    Window(
        onCloseRequest = ::exitApplication,
        title = "Кладовка",
        // Без этого параметра окно показывает дефолтный значок Windows:
        // у окна свой значок, не тот, что вшит в Kladovka.exe (окно принадлежит
        // порождённому java.exe, а не самой заглушке). См. stageAppIcon.
        icon = painterResource("kladovka-256.png"),
        state = rememberWindowState(size = DpSize(1180.dp, 820.dp))
    ) {
        KladovkaApp(
            db = db,
            api = api,
            settings = settings,
            onSettings = { updated ->
                settings = updated
                SettingsStore.save(dataDir, updated)
                api.baseUrl = updated.serverUrl
            }
        )
    }
}

@Composable
private fun KladovkaApp(
    db: SqliteDatabase,
    api: ApiClient,
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit
) {
    var showSync by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val data by db.data.collectAsState()

    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    KladovkaTheme(darkTheme = dark) {
        MainScreen(
            db = db,
            data = data,
            onOpenSync = { showSync = true },
            onOpenSettings = { showSettings = true }
        )

        if (showSync) {
            SyncScreen(
                db = db,
                api = api,
                settings = settings,
                onSettingsChange = onSettings,
                onClose = { showSync = false }
            )
        }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                onChange = onSettings,
                db = db,
                onClose = { showSettings = false }
            )
        }
    }
}
