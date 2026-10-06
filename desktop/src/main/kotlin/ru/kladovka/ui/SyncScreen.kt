package ru.kladovka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.kladovka.data.ApiClient
import ru.kladovka.data.ApiException
import ru.kladovka.data.AppSettings
import ru.kladovka.data.Backup
import ru.kladovka.data.SqliteDatabase
import ru.kladovka.data.SyncReport
import ru.kladovka.data.ThemeMode
import ru.kladovka.data.UserProfile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Экран «Сервер»: вход, обмен данными, бэкап в файл, профиль и совместный доступ.
 *
 * Действия соответствуют боевому api.php: отправка — `import`, загрузка — `export`.
 * Токен хранится в settings.properties рядом с базой — иначе после закрытия этого
 * окна пришлось бы вводить логин и пароль заново, а на Android сессия
 * восстанавливается автоматически. Кнопка «Выйти» стирает токен и вызывает
 * серверный `logout`, который отзывает вход по-настоящему: токен у пользователя
 * один и выводится из употребления через revoked_users, а не подменяется.
 * Проверено на сервере: после выхода прежний токен отвечает 401, а новый вход
 * снова его выдаёт и снимает отзыв.
 */
@Composable
fun SyncScreen(
    db: SqliteDatabase,
    api: ApiClient,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Адрес сервера больше не настраивается: используется зашитый в клиент.
    val url = ApiClient.DEFAULT_URL
    // Поля предзаполнены сохранёнными учётными данными: пароль нужен для тихого
    // автологина при следующем запуске, как в Android-приложении.
    var password by remember { mutableStateOf(settings.password) }
    var username by remember { mutableStateOf(settings.username) }
    var token by remember { mutableStateOf(settings.token.takeIf { it.isNotEmpty() }) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Не авторизованы") }
    var lastReport by remember { mutableStateOf<SyncReport?>(null) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var shareUser by remember { mutableStateOf("") }
    var given by remember { mutableStateOf<List<String>>(emptyList()) }
    var received by remember { mutableStateOf<List<String>>(emptyList()) }
    var showProfileEdit by remember { mutableStateOf(false) }

    // Профиль и списки доступа запрашиваются сразу после входа: на Android они
    // тянутся при открытии диалога синхронизации, здесь — раз в открытие,
    // иначе блоки висели бы пустыми до первого ручного действия.
    LaunchedEffect(token) {
        val t = token ?: return@LaunchedEffect
        run {
            profile = runCatching { api.profile(t) }.getOrNull()
            runCatching { api.shares(t) }.getOrNull()?.let { (g, r) ->
                given = g
                received = r
            }
        }
    }

    /**
     * Действие с общим отчётом об ошибке.
     *
     * На 401 (вход отозван на сервере) одного сообщения мало: токен больше не
     * работает, и каждая следующая попытка упрётся в то же самое. Поэтому сессия
     * сбрасывается, а раз логин с паролем сохранены — выполняется тихий повторный
     * вход, и действие выполняется заново один раз. Второй отказ той же причины
     * уже показывается как есть.
     */
    fun run(block: suspend () -> String) {
        // Пароль берём из сохранённых настроек, а не из поля ввода: после успешного
        // входа поле очищается, и тихий повторный вход иначе был бы без пароля.
        val storedPassword = settings.password
        fun attempt(retryOnRevoked: Boolean) {
            scope.launch {
                busy = true
                try {
                    status = block()
                } catch (e: Exception) {
                    val revoked = e is ApiException && e.code == 401
                    if (revoked && retryOnRevoked && storedPassword.isNotEmpty()) {
                        val fresh = runCatching {
                            if (settings.username.isEmpty()) api.login(storedPassword)
                            else api.loginUser(settings.username, storedPassword).first
                        }.getOrNull()
                        if (fresh != null) {
                            token = fresh
                            onSettingsChange(settings.copy(token = fresh))
                            attempt(retryOnRevoked = false)
                            return@launch
                        }
                    }
                    if (revoked) {
                        token = null
                        onSettingsChange(settings.copy(token = ""))
                    }
                    status = "Ошибка: ${e.message}"
                } finally {
                    busy = false
                }
            }
        }
        attempt(retryOnRevoked = true)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Сервер") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Адрес сервера убран: сервер один и он зашит в приложение.
                // Поле давало человеку вписать любой адрес — и ошибиться, и увести
                // свои данные и пароль на посторонний хост одним опечаткой.
                Text(
                    "Сервер: ${ApiClient.DEFAULT_URL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (token == null) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя (оставьте пустым для администратора)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !busy,
                        onClick = {
                            val u = username.trim()
                            val p = password
                            password = ""
                            run {
                                api.baseUrl = url
                                if (u.isEmpty()) {
                                    val t = api.login(p)
                                    token = t
                                    onSettingsChange(settings.copy(token = t, username = "", password = p))
                                    "Вход выполнен (администратор)"
                                } else {
                                    val (t, verified) = api.loginUser(u, p)
                                    token = t
                                    onSettingsChange(settings.copy(token = t, username = u, password = p))
                                    if (verified) "Вход выполнен: $u" else "Вход выполнен: $u (почта не подтверждена)"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Подождите…" else "Войти") }
                } else {
                    Text("Авторизация активна", style = MaterialTheme.typography.titleSmall)

                    Button(
                        enabled = !busy,
                        onClick = {
                            val t = token ?: return@Button
                            run {
                                // Фото уезжают на сервер первыми, в JSON попадают их URL —
                                // иначе на телефоне остались бы пути от компьютера.
                                val r = api.push(t, db.data.value, File(settings.dataDir, "photos"))
                                lastReport = SyncReport(pushed = true)
                                buildString {
                                    append("Отправлено на сервер: вещей ${db.data.value.items.size}")
                                    if (r.uploadedPhotos > 0) append("; фото загружено: ${r.uploadedPhotos}")
                                    if (r.failedPhotos > 0) append("; фото не отправилось: ${r.failedPhotos}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отправить на сервер (заменить данные)") }

                    Button(
                        enabled = !busy,
                        onClick = {
                            val t = token ?: return@Button
                            run {
                                val pulled = api.pull(t)
                                db.replaceAll(pulled)
                                lastReport = SyncReport(pulled = true)
                                "Загружено с сервера: вещей ${pulled.items.size}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Загрузить с сервера (заменить локальные)") }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            token = null
                            profile = null
                            given = emptyList()
                            received = emptyList()
                            onSettingsChange(settings.copy(token = "", username = "", password = ""))
                            status = "Вышли из аккаунта"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Выйти") }

                    profile?.let { p ->
                        HorizontalDivider()
                        Text("Аккаунт", style = MaterialTheme.typography.titleSmall)
                        Text(
                            p.username.ifBlank { settings.username.ifBlank { "Администратор" } },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (p.email.isNotBlank()) {
                            Text("Почта: ${p.email}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (p.emailVerified) {
                                "✓ Почта подтверждена"
                            } else {
                                "⚠ Почта не подтверждена — перейдите по ссылке из письма"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.emailVerified) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        // Смены данных не было на компьютере, хотя серверное
                        // действие и телефон поддерживали её давно: чтобы поменять
                        // имя или пароль, приходилось с телефона.
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { showProfileEdit = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Изменить имя, почту или пароль") }
                    }

                    HorizontalDivider()
                    Text("Совместный доступ", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Откройте доступ другому пользователю — и ведите учёт вместе.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = shareUser,
                        onValueChange = { shareUser = it },
                        label = { Text("Логин пользователя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy && shareUser.isNotBlank(),
                            onClick = {
                                val t = token ?: return@OutlinedButton
                                run {
                                    val who = shareUser.trim()
                                    api.share(t, who)
                                    shareUser = ""
                                    runCatching { api.shares(t).first }.getOrNull()?.let { given = it }
                                    "Доступ открыт для $who"
                                }
                            }
                        ) { Text("Дать доступ") }
                        OutlinedButton(
                            enabled = !busy && shareUser.isNotBlank(),
                            onClick = {
                                val t = token ?: return@OutlinedButton
                                run {
                                    val who = shareUser.trim()
                                    api.unshare(t, who)
                                    shareUser = ""
                                    runCatching { api.shares(t).first }.getOrNull()?.let { given = it }
                                    "Доступ отозван для $who"
                                }
                            }
                        ) { Text("Отозвать") }
                    }
                    if (given.isNotEmpty()) {
                        Text("Доступ дан: ${given.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (received.isNotEmpty()) {
                        Text("Доступ от: ${received.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()

                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val target = File(settings.dataDir, "kladovka-backup.json")
                        run {
                            target.writeText(Backup.export(db.data.value))
                            "Бэкап сохранён: ${target.absolutePath}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить бэкап в файл") }

                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val picker = javax.swing.JFileChooser().apply {
                            dialogTitle = "Выберите файл бэкапа"
                        }
                        val ok = picker.showOpenDialog(null)
                        if (ok == javax.swing.JFileChooser.APPROVE_OPTION) {
                            run {
                                val parsed = Backup.parse(picker.selectedFile.readText())
                                db.replaceAll(parsed)
                                "Импортировано: вещей ${parsed.items.size}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Загрузить бэкап из файла") }

                // ---------- Смена своих данных ----------
    // Имя, почта и пароль меняются через updateProfile. Пустое поле означает
    // «не менять»: сервер разбирает только присланные поля, поэтому пароль
    // можно поменять, не вписывая имя заново. Для смены пароля сервер требует
    // текущий — иначе чужой, кто завёл телефон к вашему компьютеру, сменил бы
    // пароль и отобрал бы склад.
    if (showProfileEdit && token != null) {
        val t = token ?: return@AlertDialog
        EditProfileDialog(
            current = profile,
            onClose = { showProfileEdit = false },
            onSave = { newName, newEmail, newPass, curPass ->
                run {
                    busy = true
                    profile = api.updateProfile(t, newName, newEmail, newPass, curPass)
                    // Имя изменилось — приводим и логин в настройках, иначе при
                    // следующем входе приложение подставит старое имя и получит
                    // отказ, хотя на сервере всё верно.
                    val savedName = profile?.username.orEmpty()
                    if (savedName.isNotBlank() && savedName != settings.username) {
                        onSettingsChange(settings.copy(username = savedName))
                    }
                    showProfileEdit = false
                    busy = false
                    "Изменения сохранены"
                }
            },
            onError = { msg -> busy = false; status = msg }
        )
    }

                if (busy) {
                    Text("Выполняется…", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.startsWith("Ошибка")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Внимание: «Отправить» и «Загрузить» полностью заменяют данные " +
                        "на той стороне, с которой идёт обмен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } }
    )
}

/**
 * Диалог смены своих данных: имя, почта, пароль.
 *
 * Поля показывают текущие значения, но отправляются только изменённые — иначе
 * при каждом открытии уходит запрос с тем же самым, а при смене почты, например,
 * сервер заново требует подтверждение. Пустое поле означает «не трогать».
 */
@Composable
private fun EditProfileDialog(
    current: UserProfile?,
    onClose: () -> Unit,
    onSave: (name: String, email: String, newPassword: String, currentPassword: String) -> Unit,
    onError: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var curPass by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val passChanged = newPass.isNotEmpty()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Изменить данные") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Сейчас: ${current?.username.orEmpty().ifBlank { "—" }} · " +
                        current?.email.orEmpty().ifBlank { "почта не указана" } +
                        if (current?.emailVerified == true) " (подтверждена)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Новое имя пользователя") },
                    supportingText = { Text("Латиница, цифры и _; 3–30 символов. Пусто — не менять") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Новая почта") },
                    supportingText = { Text("После смены придёт письмо и склад снова придётся подтвердить") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Новый пароль") },
                    supportingText = { Text("Пусто — не менять") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (passChanged) {
                    OutlinedTextField(
                        value = curPass,
                        onValueChange = { curPass = it },
                        label = { Text("Текущий пароль — обязательно") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                localError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (passChanged && curPass.isEmpty()) {
                    localError = "Чтобы поменять пароль, введите текущий"
                    return@TextButton
                }
                if (name.isBlank() && email.isBlank() && !passChanged) {
                    onClose()
                    return@TextButton
                }
                localError = null
                onSave(name, email, newPass, curPass)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}

/** Настройки приложения: тема и сведения о каталоге данных. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    db: SqliteDatabase,
    onClose: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Настройки") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Тема", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        androidx.compose.material3.FilterChip(
                            selected = settings.themeMode == m,
                            onClick = { onChange(settings.copy(themeMode = m)) },
                            label = { Text(
                                when (m) {
                                    ThemeMode.SYSTEM -> "Системная"
                                    ThemeMode.LIGHT -> "Светлая"
                                    ThemeMode.DARK -> "Тёмная"
                                }
                            ) }
                        )
                    }
                }

                HorizontalDivider()

                Text("Данные", style = MaterialTheme.typography.labelLarge)
                Text(
                    "База: ${db.data.value.items.size} вещей, " +
                        "${db.data.value.containers.size} контейнеров, " +
                        "${db.data.value.shelves.size} стеллажей",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    settings.dataDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Формат базы совпадает с Android-приложением — файл kladovka.db " +
                        "можно переносить между телефоном и компьютером.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onChange(settings)
                onClose()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}
