package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.kladovka.data.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    updateSettings: (Settings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    onSyncNow: () -> Unit,
    onNavigateToServer: () -> Unit
) {
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var autoSync by remember { mutableStateOf(settings.autoSync) }
    var syncInterval by remember { mutableStateOf(settings.syncInterval) }
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var showServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        themeMode = settings.themeMode
        autoSync = settings.autoSync
        syncInterval = settings.syncInterval
        serverUrl = settings.serverUrl
    }

    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Тема
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: show theme picker */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Тема",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Сейчас: ${when(themeMode) {
                                    ThemeMode.LIGHT -> "Светлая"
                                    ThemeMode.DARK -> "Тёмная"
                                    ThemeMode.SYSTEM -> "Системная"
                                }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { /* TODO: show theme picker */ }) {
                            Text("Выбрать")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Автосинхронизация
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoSync = !autoSync }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Автосинхронизация",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Интервал: ${syncInterval} мин",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Checkbox(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Синхронизировать сейчас
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSyncNow() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Синхронизировать сейчас",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Сервер
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showServerDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Сервер",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "URL: ${serverUrl.ifEmpty { "Не указан" }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { showServerDialog = true }) {
                            Text("Настроить")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Экспорт
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExport() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Экспорт данных",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Сохранить в JSON/CSV",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Импорт
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImport() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Импорт данных",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Загрузить из JSON/CSV",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Очистка данных
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: confirm and clear */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.padding(end = 16.dp)) {
                            Text(
                                "Очистить все данные",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Это необратимо!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог сервера
    if (showServerDialog) {
        ServerConfigDialog(
            serverUrl = serverUrl,
            onSave = { url ->
                updateSettings(settings.copy(serverUrl = url))
                showServerDialog = false
            },
            onDismiss = { showServerDialog = false }
        )
    }

    // Диалог экспорта
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт данных") },
            text = {
                Text("Выберите формат для экспорта:")
            },
            confirmButton = {
                TextButton(onClick = {
                    onExport()
                    showExportDialog = false
                }) {
                    Text("JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun ServerConfigDialog(
    serverUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputUrl by remember { mutableStateOf(serverUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки сервера") },
        text = {
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                label = { Text("URL сервера") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Введите полный URL, например: https://kladovka.example.com")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (inputUrl.isNotBlank()) {
                    onSave(inputUrl.trim())
                }
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
