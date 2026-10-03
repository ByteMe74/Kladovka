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
fun ItemEditScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Item) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("") }
    var itemUnit by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("") }
    var itemNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Вещь") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        label = { Text("Количество") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemUnit,
                        onValueChange = { itemUnit = it },
                        label = { Text("Ед. измерения") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemCategory,
                        onValueChange = { itemCategory = it },
                        label = { Text("Категория") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemNotes,
                        onValueChange = { itemNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (itemName.isNotBlank()) {
                    val item = Item(
                        id = 0L, // TODO: использовать itemId из параметров
                        name = itemName,
                        quantity = itemQuantity.toIntOrNull(),
                        unit = itemUnit,
                        category = itemCategory,
                        notes = itemNotes
                    )
                    onSave(item)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название вещи", color = MaterialTheme.colorScheme.error)
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
fun AddItemScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Item) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("") }
    var itemUnit by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("") }
    var itemNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onBack() },
        title = { Text("Новая вещь") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemQuantity,
                        onValueChange = { itemQuantity = it },
                        label = { Text("Количество") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemUnit,
                        onValueChange = { itemUnit = it },
                        label = { Text("Ед. измерения") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemCategory,
                        onValueChange = { itemCategory = it },
                        label = { Text("Категория") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = itemNotes,
                        onValueChange = { itemNotes = it },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (itemName.isNotBlank()) {
                    val item = Item(
                        id = 0L, // TODO: использовать itemId из параметров
                        name = itemName,
                        quantity = itemQuantity.toIntOrNull(),
                        unit = itemUnit,
                        category = itemCategory,
                        notes = itemNotes
                    )
                    onSave(item)
                    onBack()
                } else {
                    scaffoldPadding?.let { Scaffold(it) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Text("Введите название вещи", color = MaterialTheme.colorScheme.error)
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
