package ru.kladovka.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kladovka.data.Repository
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Диалог синхронизации с дружелюбным интерфейсом:
 * статус-карта (связаны ли телефон и сервер), понятные кнопки «отправить/загрузить»,
 * живая строка прогресса и человеческие объяснения ошибок.
 */
@Composable
fun SyncDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    // Адрес сервера больше не настраивается: он зашит в клиент. Раньше здесь
    // было состояние с адресом из скрытого поля — и старый адрес из настроек
    // продолжал уходить в сеть вместе с паролем.
    val url = AppViewModel.SERVER_URL
    var username by rememberSaveable { mutableStateOf(vm.syncServerUsername()) }
    var password by rememberSaveable { mutableStateOf(vm.syncServerPassword()) }
    var showPushConfirm by remember { mutableStateOf(false) }
    var showPullConfirm by remember { mutableStateOf(false) }
    // Совместный доступ
    var shareUsername by rememberSaveable { mutableStateOf("") }
    var shareMessage by remember { mutableStateOf<String?>(null) }
    var given by remember { mutableStateOf(listOf<String>()) }
    var received by remember { mutableStateOf(listOf<String>()) }
    val refreshShares: () -> Unit = {
        vm.syncShares { g, r -> given = g; received = r }
    }
    LaunchedEffect(Unit) {
        refreshShares()
        vm.refreshProfile() // при открытии диалога подтягиваем данные аккаунта
        vm.checkForUpdates() // при открытии диалога тихо проверяем обновление
    }

    val busy by vm.syncBusy.collectAsStateWithLifecycle()
    val busyText by vm.syncBusyText.collectAsStateWithLifecycle()
    val synced by vm.synced.collectAsStateWithLifecycle()
    val message by vm.syncMessage.collectAsStateWithLifecycle()
    val autoSync by vm.autoSync.collectAsStateWithLifecycle()
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val updateInfo by vm.updateInfo.collectAsStateWithLifecycle()
    val updateUrl by vm.updateUrl.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val downloadContext = LocalContext.current

    // Регистрация нового аккаунта
    var showRegister by remember { mutableStateOf(false) }
    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regMessage by remember { mutableStateOf<String?>(null) }
    // Личный кабинет: поля изменения имени/почты/пароля
    var editUsername by rememberSaveable { mutableStateOf("") }
    var editEmail by rememberSaveable { mutableStateOf("") }
    var curPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var profileMessage by remember { mutableStateOf<String?>(null) }

    // Сколько записей на телефоне — показываем, чтобы понять, что переезжает на сервер
    val totalRecords = data.places.size + data.shelves.size + data.polki.size +
        data.containers.size + data.items.size

    val lastSyncText = if (lastSyncAt > 0L) {
        remember(lastSyncAt) {
            java.text.SimpleDateFormat("dd.MM.yyyy в HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastSyncAt))
        }
    } else null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).imePadding().padding(16.dp)
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Синхронизация",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (synced)
                        "Телефон и сервер связаны. Изменения будут сохраняться автоматически."
                    else
                        "Подключите телефон к серверу, чтобы не потерять данные даже при смене устройства или переустановке.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---------- Статус-карта ----------
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (synced) Color(0xFF123C2A) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        } else {
                            Box(
                                Modifier.size(32.dp).background(
                                    color = if (synced) Color(0xFF3DDC84) else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                            ) {
                                Icon(
                                    imageVector = if (synced) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    busy -> busyText ?: "Синхронизация…"
                                    synced -> "Данные в безопасности"
                                    else -> "Ещё не подключено"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    busy -> "Это обычно занимает несколько секунд"
                                    synced && lastSyncText != null -> "Последняя синхронизация: $lastSyncText"
                                    synced -> "На сервере: ${syncedHost(url)}"
                                    else -> "Записей на телефоне: $totalRecords"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ---------- Что ответил сервер ----------
                // Сообщение стоит сразу под статусом, а не внизу окна. Раньше оно
                // было после блока «Выход из аккаунта» — то есть за пределами
                // видимой части на телефоне: человек нажимал «Подключиться», не
                // видел ничего и нажимал снова. Проверено на живом аккаунте без
                // подтверждённой почты: вход не проходит, и на экране было
                // ровно то же самое, что и до нажатия.
                message?.let { (isError, text) ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isError) MaterialTheme.colorScheme.errorContainer
                        else Color(0xFF123C2A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            ErrorText.human(text),
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                            else Color(0xFFB7F5D6),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                HorizontalDivider()

                // ---------- Параметры подключения ----------
                // По умолчанию показываем только логин/пароль — адрес сервера
                // скрыт (клиенту менять его не нужно, он уже заполнен).
                //
                // Вход и регистрация переключаются, а не раскрываются одна под
                // другой. Раскрывающаяся панель оказалась хуже: на телефоне её
                // поля уходили за нижний край окна, и до них нельзя было дотянуться
                // — проверено на живой установке, окно дальше не прокручивается.
                // Переключатель держит три поля над сгибом экрана при любом
                // содержимом окна.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !showRegister,
                        onClick = { showRegister = false },
                        label = { Text("Вход", maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = showRegister,
                        onClick = {
                            showRegister = true
                            if (regUsername.isEmpty()) regUsername = username
                        },
                        label = { Text("Регистрация", maxLines = 1, softWrap = false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!showRegister) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя пользователя") },
                        supportingText = { Text("Пусто — вход администратора") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Метки короткие и строго в одну строку. Длинные метки
                    // («Почта — на неё придёт ссылка») переносились на две строки,
                    // и каждое поле становилось вдвое выше обычного: 88dp вместо
                    // 64dp. Из-за этого кнопка «Создать аккаунт» уезжала за нижний
                    // край окна — на экране 1080×1920 от неё оставалось 9 пикселей,
                    // то есть её не было видно совсем, и человек не догадывался, что
                    // окно прокручивается. Замерено на эмуляторе Android 9.
                    //
                    // Пояснения из меток вынесены отдельным мелким текстом под поля
                    // (две строки на всю форму), а внутри блока отступы уменьшены с
                    // 14dp до 8dp: без этого вынесенные пояснения снова вытолкнули
                    // бы кнопку за край. На Android 15 (1080×2400) дефекта не было
                    // — там всё помещалось и раньше.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = { regUsername = it },
                            label = { Text("Имя пользователя", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = { regEmail = it },
                            label = { Text("Почта", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            // Метка «Почта — на неё придёт ссылка» была в две строки.
                            // Пояснение вынесено под поле, но обязано уместиться в одну
                            // строку: вторая строка съедала 42 пикселя и снова уводила
                            // кнопку под сгиб экрана.
                            "На почту придёт ссылка.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            label = { Text("Пароль (6+ символов)", maxLines = 1, softWrap = false) },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { vm.syncRegister(regUsername.trim(), regPassword, regEmail.trim()) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Person, null, Modifier.size(18.dp))
                            Text("  Создать аккаунт", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Почту лучше подтвердить.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Адрес сервера убран из UI: сервер один и он зашит в приложение. Поле
                // давало человеку вписать любой адрес — и ошибиться, и увести свои
                // данные и пароль на посторонний хост одной опечаткой.
                Text(
                    "Сервер: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        vm.saveSyncSettings(username, password)
                        vm.syncLogin()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Sync, null, Modifier.size(18.dp))
                    Text(
                        "  " + if (synced) "Переподключиться" else "Подключиться к серверу",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Перенос данных", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                vm.saveSyncSettings(username, password)
                                showPushConfirm = true
                            },
                            enabled = synced && !busy,
                            // Половина ширины узкого AlertDialog минус стандартные
                            // отступы кнопки не вмещали «Отправить»: слово рвалось
                            // посередине на «Отправи» / «ть», и кнопка выглядела
                            // сломанной. Замечено на эмуляторе.
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CloudUpload, null, Modifier.size(20.dp))
                                Text("Отправить", maxLines = 1, softWrap = false)
                                // Подпись с направлением короче прежней: «телефон →
                                // сервер» не помещалась в половину узкого диалога и
                                // вылезала за кнопку. Стрелка и слово «На» читаются
                                // однозначно, а смысл («куда» подразумевается из
                                // подписи под рядом) уже объяснён строкой ниже.
                                Text(
                                    "На сервер",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                vm.saveSyncSettings(username, password)
                                showPullConfirm = true
                            },
                            enabled = synced && !busy,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CloudDownload, null, Modifier.size(20.dp))
                                Text("Загрузить", maxLines = 1, softWrap = false)
                                Text(
                                    "С сервера",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        "Отправка копирует ваши записи на сервер. Загрузка возвращает копию с сервера на телефон.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // ---------- Автосинхронизация ----------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Автосинхронизация", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Изменения сами сохраняются на сервере после ввода и при сворачивании приложения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoSync, onCheckedChange = { vm.setAutoSync(it) }, enabled = !busy)
                }

                // ---------- Выход из аккаунта ----------
                // Кнопки не было вообще: выйти можно было только закрыв приложение.
                // Теперь сервер отзывает вход по-настоящему, поэтому без неё отозвать
                // его можно было только на сайте — а на телефоне вход всё равно
                // оставался, и при следующем запуске тихий автологин заходил обратно.
                HorizontalDivider()
                OutlinedButton(
                    onClick = { vm.syncLogout() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Logout, null, Modifier.size(18.dp))
                    Text("  Выйти из аккаунта")
                }
                Text(
                    "Выход отзывает доступ на сервере и стирает сохранённый пароль с этого телефона. " +
                        "Данные в приложении остаются на месте. Чтобы вернуться, нажмите «Подключиться к серверу».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // ---------- Обновление приложения ----------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Обновление приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { vm.checkForUpdates() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Update, null, Modifier.size(18.dp))
                        Text("  Проверить обновление", fontWeight = FontWeight.SemiBold)
                    }
                    updateInfo?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateUrl != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    updateUrl?.let { url ->
                        Button(
                            onClick = { openDownloadUrl(downloadContext, url) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скачать и установить", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Скачанный файл откроется сам — нажмите «Установить». Данные при этом сохранятся.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // ---------- Личный кабинет ----------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Личный кабинет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (profile != null) {
                        val p = profile!!
                        Text(
                            "Вы вошли как «${p.username}»" +
                                if (p.email.isNotBlank()) "\nПочта: ${p.email}" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (p.email.isNotBlank()) {
                            Text(
                                if (p.emailVerified) " ✓ Почта подтверждена"
                                else " ⚠ Почта не подтверждена — перейдите по ссылке из письма",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.emailVerified) Color(0xFF3DDC84) else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            "Измените имя, почту или пароль. При смене почты придёт письмо с подтверждением.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = editUsername,
                            onValueChange = { editUsername = it },
                            label = { Text("Новое имя пользователя") },
                            placeholder = { Text(p.username) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editEmail,
                            onValueChange = { editEmail = it },
                            label = { Text("Новая почта") },
                            placeholder = { Text(p.email.ifBlank { "не указана" }) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = curPassword,
                            onValueChange = { curPassword = it },
                            label = { Text("Текущий пароль") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Новый пароль (мин. 6 символов)") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                vm.updateProfile(
                                    editUsername.trim().ifEmpty { null },
                                    editEmail.trim().ifEmpty { null },
                                    curPassword.ifEmpty { null },
                                    newPassword.ifEmpty { null }
                                ) { ok, msg ->
                                    if (ok) {
                                        editUsername = ""
                                        editEmail = ""
                                        curPassword = ""
                                        newPassword = ""
                                    }
                                    profileMessage = msg
                                }
                            },
                            enabled = !busy &&
                                (editUsername.isNotBlank() || editEmail.isNotBlank() || newPassword.isNotBlank()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сохранить изменения", fontWeight = FontWeight.SemiBold)
                        }
                        profileMessage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.contains("ошиб") || it.contains("неверн") || it.contains("занят") || it.contains("минимум"))
                                    MaterialTheme.colorScheme.error else Color(0xFF3DDC84)
                            )
                        }
                    } else if (!synced) {
                        Text(
                            "Войдите на сервер, чтобы управлять данными аккаунта (имя, почта, пароль).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Аккаунт недоступен (вход администратора).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // ---------- Совместный доступ ----------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Совместный доступ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Откройте свой склад другому пользователю — он увидит ваши записи и сможет вести учёт вместе с вами",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = shareUsername,
                        onValueChange = { shareUsername = it },
                        label = { Text("Логин пользователя") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Ряд из двух кнопок: AlertDialog на телефоне уже узкий, места
                    // по половине на каждую не хватало, и «Дать доступ»
                    // переносилась на две строки — кнопки получались разной
                    // высоты. Замечено на эмуляторе.
                    //
                    // Уменьшаем внутренние отступы и запрещаем перенос: слова
                    // оставляем как есть, потому что раздел уже называется
                    // «Совместный доступ» и «Отозвать» без «доступ» читается
                    // как отзыв самой учётной записи, а не доступа.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { vm.shareWith(shareUsername) { ok, msg ->
                                shareMessage = ErrorText.human(msg)
                                if (ok) { shareUsername = ""; refreshShares() }
                            } },
                            enabled = shareUsername.isNotBlank() && !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Дать доступ", maxLines = 1, softWrap = false) }
                        OutlinedButton(
                            onClick = { vm.unshareWith(shareUsername) { ok, msg ->
                                shareMessage = ErrorText.human(msg)
                                if (ok) { shareUsername = ""; refreshShares() }
                            } },
                            enabled = shareUsername.isNotBlank() && !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Отозвать", maxLines = 1, softWrap = false) }
                    }
                    shareMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (given.isNotEmpty() || received.isNotEmpty()) {
                        Text(
                            buildString {
                                if (given.isNotEmpty()) append("Доступ дан: ${given.joinToString(", ")}")
                                if (received.isNotEmpty()) {
                                    if (isNotEmpty()) append(". ")
                                    append("Доступ от: ${received.joinToString(", ")}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Готово")
                }
            }
        }
    }

    if (showPushConfirm) {
        AlertDialog(
            onDismissRequest = { showPushConfirm = false },
            title = { Text("Отправить данные на сервер?") },
            text = { Text("Данные на сервере будут заменены данными телефона: $totalRecords записей. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showPushConfirm = false
                    vm.syncPush()
                }) { Text("Отправить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showPushConfirm = false }) { Text("Отмена") } }
        )
    }

    if (showPullConfirm) {
        AlertDialog(
            onDismissRequest = { showPullConfirm = false },
            title = { Text("Загрузить данные с сервера?") },
            text = { Text("Данные на телефоне будут заменены данными с сервера. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showPullConfirm = false
                    vm.syncPull()
                }) { Text("Загрузить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showPullConfirm = false }) { Text("Отмена") } }
        )
    }
}

/** Короткое имя хоста для статуса («kladovka.dr6ter.ru»). */
private fun syncedHost(url: String): String =
    url.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Открыть ссылку на APK в браузере (браузер сам скачает и предложит установку). */
private fun openDownloadUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}