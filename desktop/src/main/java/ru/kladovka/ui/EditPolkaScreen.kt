package ru.kladovka.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kladovka.data.*
import kotlinx.coroutines.launch

@Composable
fun PolkaEditScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Polka) -> Unit
) {
    val scope = rememberCoroutineScope()
    var polkaName by remember { mutableStateOf("") }
    var polkaNotes by remember { mutableStateOf("") }
    var selectedShelfId by remember { mutableStateOf<Long?>(null) }
    
    val shelves by repo.shelves.collectAsState()

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Полка") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = polkaName,
                        onValueChange = { polkaName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = polkaNotes,
                        onValueChange = { polkaNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        onClick = { /* Открывается через Dialog */ }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Стеллаж: ${if (selectedShelfId != null) shelves.find { it.id == selectedShelfId }?.name ?: "Не выбрано" }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { /* Открыть диалог выбора стеллажа */ }) {
                                Text("Выбрать")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (polkaName.isNotBlank()) {
                    val polka = Polka(
                        id = 0L, // TODO: использовать polkaId из параметров
                        name = polkaName,
                        shelfId = selectedShelfId,
                        notes = polkaNotes
                    )
                    onSave(polka)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название полки", color = MaterialTheme.colorScheme.error)
                        }
                    } }
                }
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AddPolkaScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Polka) -> Unit
) {
    var polkaName by remember { mutableStateOf("") }
    var polkaNotes by remember { mutableStateOf("") }
    var selectedShelfId by remember { mutableStateOf<Long?>(null) }
    
    val shelves by repo.shelves.collectAsState()

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Новая полка") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = polkaName,
                        onValueChange = { polkaName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = polkaNotes,
                        onValueChange = { polkaNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        onClick = { /* Открывается через Dialog */ }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Стеллаж: ${if (selectedShelfId != null) shelves.find { it.id == selectedShelfId }?.name ?: "Не выбрано" }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { /* Открыть диалог выбора стеллажа */ }) {
                                Text("Выбрать")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (polkaName.isNotBlank()) {
                    val polka = Polka(
                        id = 0L, // TODO: использовать polkaId из параметров
                        name = polkaName,
                        shelfId = selectedShelfId,
                        notes = polkaNotes
                    )
                    onSave(polka)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название полки", color = MaterialTheme.colorScheme.error)
                        }
                    } }
                }
            }) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Отмена")
            }
        }
    )
}
