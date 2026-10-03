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
fun ContainerEditScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Container) -> Unit
) {
    val scope = rememberCoroutineScope()
    var containerName by remember { mutableStateOf("") }
    var containerLocation by remember { mutableStateOf("") }
    var containerNotes by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf<Long?>(null) }
    
    val places by repo.places.collectAsState()

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Контейнер") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = containerName,
                        onValueChange = { containerName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = containerLocation,
                        onValueChange = { containerLocation = it },
                        label = { Text("Расположение") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = containerNotes,
                        onValueChange = { containerNotes = it },
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
                                "Место: ${if (selectedPlaceId != null) places.find { it.id == selectedPlaceId }?.name ?: "Не выбрано" }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { /* Открыть диалог выбора места */ }) {
                                Text("Выбрать")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (containerName.isNotBlank()) {
                    val container = Container(
                        id = 0L, // TODO: использовать containerId из параметров
                        name = containerName,
                        placeId = selectedPlaceId,
                        location = containerLocation,
                        notes = containerNotes
                    )
                    onSave(container)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название контейнера", color = MaterialTheme.colorScheme.error)
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
fun AddContainerScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Container) -> Unit
) {
    var containerName by remember { mutableStateOf("") }
    var containerLocation by remember { mutableStateOf("") }
    var containerNotes by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf<Long?>(null) }
    
    val places by repo.places.collectAsState()

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Новый контейнер") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = containerName,
                        onValueChange = { containerName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = containerLocation,
                        onValueChange = { containerLocation = it },
                        label = { Text("Расположение") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = containerNotes,
                        onValueChange = { containerNotes = it },
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
                                "Место: ${if (selectedPlaceId != null) places.find { it.id == selectedPlaceId }?.name ?: "Не выбрано" }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { /* Открыть диалог выбора места */ }) {
                                Text("Выбрать")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (containerName.isNotBlank()) {
                    val container = Container(
                        id = 0L, // TODO: использовать containerId из параметров
                        name = containerName,
                        placeId = selectedPlaceId,
                        location = containerLocation,
                        notes = containerNotes
                    )
                    onSave(container)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название контейнера", color = MaterialTheme.colorScheme.error)
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
