package ru.kladovka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.kladovka.data.UpdateChecker
import ru.kladovka.data.UpdateState
import ru.kladovka.data.ApiClient
import ru.kladovka.data.AppSettings
import ru.kladovka.data.SettingsStore
import ru.kladovka.data.SqliteDatabase
import ru.kladovka.data.ThemeMode
import ru.kladovka.ui.KladovkaTheme
import ru.kladovka.ui.MainScreen
import ru.kladovka.ui.SettingsScreen
import java.io.File
import ru.kladovka.ui.SyncScreen
import ru.kladovka.ui.UpdateDialog

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
    // Адрес сервера больше не настраивается: он зашит в клиент. Значение из
    // старых настроек игнорируем, иначе у кого-то остался бы адрес, введённый
    // до удаления поля, и данные уходили бы туда.
    api.baseUrl = ApiClient.DEFAULT_URL

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
    var showUpdate by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Unknown) }
    val checker = remember { UpdateChecker(api) }
    val scope = rememberCoroutineScope()

    val data by db.data.collectAsState()

    // Тихий автологин при запуске — как на Android (AppViewModel вызывает
    // syncLogin(silent = true) в init). Там хранится логин с паролем, и токен
    // каждый раз берётся заново; здесь токен кэшируется, но сервер может его
    // отозвать в любой момент — в том числе при выходе из аккаунта на сайте.
    // Без автологина после этого пользователь был бы заперт: токен мёртв, а
    // пароль уже негде взять.
    var autoLoginTried by remember { mutableStateOf(false) }
    LaunchedEffect(settings.username, settings.password, settings.token) {
        if (autoLoginTried) return@LaunchedEffect
        if (settings.token.isNotEmpty()) {
            autoLoginTried = true
            return@LaunchedEffect
        }
        if (settings.username.isEmpty() && settings.password.isEmpty()) {
            autoLoginTried = true
            return@LaunchedEffect
        }
        autoLoginTried = true
        val u = settings.username
        val p = settings.password
        val token = runCatching {
            if (u.isEmpty()) api.login(p) else api.loginUser(u, p).first
        }.getOrNull()
        if (token != null) onSettings(settings.copy(token = token))
    }

    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Проверка обновления — один раз на запуск, молча и в фоне. Ошибка сети здесь
    // не должна ни показываться, ни мешать: человек звал приложение работать, а
    // не проверять интернет. Результат всплывёт, когда он сам откроет окно
    // обновления, поэтому игнорировать пустой результат нельзя — он затирает
    // «не проверяли» на «не знаю» и человек увидит устаревшее состояние.
    LaunchedEffect(Unit) {
        val fresh = checker.check()
        if (fresh is UpdateState.Available || fresh is UpdateState.UpToDate) {
            updateState = fresh
        }
    }

    KladovkaTheme(darkTheme = dark) {
        MainScreen(
            db = db,
            data = data,
            photoDir = File(settings.dataDir, "photos"),
            onOpenSync = { showSync = true },
            onOpenSettings = { showSettings = true },
            updateAvailable = updateState is UpdateState.Available,
            onOpenUpdate = { showUpdate = true }
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

        if (showUpdate) {
            UpdateDialog(
                state = updateState,
                onCheck = {
                    updateState = UpdateState.Checking
                    scope.launch { updateState = checker.check() }
                },
                onClose = { showUpdate = false }
            )
        }
    }
}
