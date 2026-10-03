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
import ru.kladovka.data.AppSettings
import ru.kladovka.data.Backup
import ru.kladovka.data.SqliteDatabase
import ru.kladovka.data.SyncReport
import ru.kladovka.data.ThemeMode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Экран «Сервер»: вход, обмен данными, бэкап в файл.
 *
 * Действия соответствуют боевому api.php: отправка — `import`, загрузка — `export`.
 * Токен хранится в памяти процесса и намеренно не записывается на диск.
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

    var url by remember { mutableStateOf(api.baseUrl) }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Не авторизованы") }
    var lastReport by remember { mutableStateOf<SyncReport?>(null) }

    fun run(block: suspend () -> String) {
        scope.launch {
            busy = true
            try {
                status = block()
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Сервер") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; api.baseUrl = it; onSettingsChange(settings.copy(serverUrl = it)) },
                    label = { Text("Адрес сервера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                                    "Вход выполнен (администратор)"
                                } else {
                                    val (t, verified) = api.loginUser(u, p)
                                    token = t
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
                                api.push(t, Backup.export(db.data.value))
                                lastReport = SyncReport(pushed = true)
                                "Отправлено на сервер: вещей ${db.data.value.items.size}"
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

/** Настройки приложения: тема, адрес сервера, каталог данных. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    db: SqliteDatabase,
    onClose: () -> Unit
) {
    var url by remember { mutableStateOf(settings.serverUrl) }

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

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Адрес сервера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                onChange(settings.copy(serverUrl = url.trim()))
                onClose()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } }
    )
}
