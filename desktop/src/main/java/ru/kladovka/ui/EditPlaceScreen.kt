package ru.kladovka.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kladovka.data.*
import kotlinx.coroutines.launch

@Composable
fun PlaceEditScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Place) -> Unit
) {
    var placeName by remember { mutableStateOf("") }
    var placeLatitude by remember { mutableStateOf("") }
    var placeLongitude by remember { mutableStateOf("") }
    var placeNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Место") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeLatitude,
                        onValueChange = { placeLatitude = it },
                        label = { Text("Широта") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeLongitude,
                        onValueChange = { placeLongitude = it },
                        label = { Text("Долгота") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeNotes,
                        onValueChange = { placeNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (placeName.isNotBlank()) {
                    val place = Place(
                        id = 0L, // TODO: использовать placeId из параметров
                        name = placeName,
                        latitude = placeLatitude.toDoubleOrNull(),
                        longitude = placeLongitude.toDoubleOrNull(),
                        notes = placeNotes
                    )
                    onSave(place)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название места", color = MaterialTheme.colorScheme.error)
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
fun AddPlaceScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Place) -> Unit
) {
    var placeName by remember { mutableStateOf("") }
    var placeLatitude by remember { mutableStateOf("") }
    var placeLongitude by remember { mutableStateOf("") }
    var placeNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Новое место") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeLatitude,
                        onValueChange = { placeLatitude = it },
                        label = { Text("Широта") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeLongitude,
                        onValueChange = { placeLongitude = it },
                        label = { Text("Долгота") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = placeNotes,
                        onValueChange = { placeNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (placeName.isNotBlank()) {
                    val place = Place(
                        id = 0L, // TODO: использовать placeId из параметров
                        name = placeName,
                        latitude = placeLatitude.toDoubleOrNull(),
                        longitude = placeLongitude.toDoubleOrNull(),
                        notes = placeNotes
                    )
                    onSave(place)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название места", color = MaterialTheme.colorScheme.error)
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
