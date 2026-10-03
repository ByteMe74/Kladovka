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
fun ShelfEditScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Shelf) -> Unit
) {
    val scope = rememberCoroutineScope()
    var shelfName by remember { mutableStateOf("") }
    var shelfNotes by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf<Long?>(null) }
    
    val places by repo.places.collectAsState()

    LaunchedEffect(0) {
        // Загрузка данных для редактирования (если id != 0)
        // TODO: добавить shelfId параметр
    }

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Стеллаж") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = shelfName,
                        onValueChange = { shelfName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = shelfNotes,
                        onValueChange = { shelfNotes = it },
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
                if (shelfName.isNotBlank()) {
                    val shelf = Shelf(
                        id = 0L, // TODO: использовать shelfId из параметров
                        name = shelfName,
                        placeId = selectedPlaceId,
                        notes = shelfNotes
                    )
                    onSave(shelf)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название стеллажа", color = MaterialTheme.colorScheme.error)
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
fun AddShelfScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Shelf) -> Unit
) {
    var shelfName by remember { mutableStateOf("") }
    var shelfNotes by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf<Long?>(null) }
    
    val places by repo.places.collectAsState()

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Новый стеллаж") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = shelfName,
                        onValueChange = { shelfName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = shelfNotes,
                        onValueChange = { shelfNotes = it },
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
                if (shelfName.isNotBlank()) {
                    val shelf = Shelf(
                        id = 0L, // TODO: использовать shelfId из параметров
                        name = shelfName,
                        placeId = selectedPlaceId,
                        notes = shelfNotes
                    )
                    onSave(shelf)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название стеллажа", color = MaterialTheme.colorScheme.error)
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
