package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import ru.kladovka.data.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ItemEditScreen(
    itemId: Long,
    repo: Repository,
    onDone: () -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf(1) }
    var itemUnit by remember { mutableStateOf("шт.") }
    var itemCategory by remember { mutableStateOf("Общее") }
    var itemNotes by remember { mutableStateOf("") }
    var itemContainerId by remember { mutableStateOf<Long?>(null) }
    var itemShelfId by remember { mutableStateOf<Long?>(null) }
    var itemPlaceId by remember { mutableStateOf<Long?>(null) }
    var itemPhotoPath by remember { mutableStateOf<String?>(null) }
    var itemPinned by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf(emptyList<String>()) }

    val existingItem = remember(itemId) {
        runCatching {
            kotlinx.coroutines.runBlocking { repo.itemById(itemId) }
        }.getOrNull()
    }

    LaunchedEffect(itemId) {
        kotlinx.coroutines.runBlocking {
            itemQuantity = if (existingItem != null) existingItem.quantity else 1
            itemUnit = if (existingItem != null) existingItem.unit else "шт."
            itemCategory = if (existingItem != null) existingItem.category else "Общее"
            itemNotes = if (existingItem != null) existingItem.notes else ""
            itemContainerId = if (existingItem != null) existingItem.containerId else null
            itemShelfId = if (existingItem != null) existingItem.shelfId else null
            itemPlaceId = if (existingItem != null) existingItem.placeId else null
            itemPhotoPath = if (existingItem != null) existingItem.photoPath else null
            itemPinned = if (existingItem != null) existingItem.pinned else false
            categories = kotlinx.coroutines.runBlocking { repo.categories() }
        }
    }

    var showCategories by remember { mutableStateOf(false) }

    fun save() {
        isDirty = false
        kotlinx.coroutines.runBlocking {
            repo.upsertItem(
                id = itemId,
                name = itemName,
                quantity = itemQuantity,
                unit = itemUnit,
                category = itemCategory,
                notes = itemNotes,
                containerId = itemContainerId,
                shelfId = itemShelfId,
                placeId = itemPlaceId,
                photoPath = itemPhotoPath
            )
            repo.setItemPinned(itemId, itemPinned)
        }
        onDone()
    }

    fun cancel() {
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать предмет") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Text("Отмена")
                    }
                },
                actions = {
                    IconButton(onClick = { save() }) {
                        Text("Сохранить")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Название
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = itemId == 0L,
                    readOnly = itemId != 0L
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Количество
                Row {
                    OutlinedTextField(
                        value = itemQuantity.toString(),
                        onValueChange = { itemQuantity = it.toIntOrNull() ?: 1 },
                        label = { Text("Количество") },
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.Alignment.CenterHorizontally
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = itemUnit,
                        onValueChange = { itemUnit = it },
                        label = { Text("Ед.") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Категория
                Row {
                    TextButton(
                        onClick = { showCategories = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Категория: ${itemCategory}")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showCategories = true }) {
                        Text("Выбрать")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Место хранения (упрощённо)
                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(listOf("Стеллаж", "Полка", "Контейнер")) { label ->
                        Card(
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable { /* TODO */ }
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Заметки
                OutlinedTextField(
                    value = itemNotes,
                    onValueChange = { itemNotes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Закрепить
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { itemPinned = !itemPinned }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Закрепить предмет", style = MaterialTheme.typography.bodyLarge)
                        Checkbox(
                            checked = itemPinned,
                            onCheckedChange = { itemPinned = it },
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCategories) {
        AlertDialog(
            onDismissRequest = { showCategories = false },
            title = { Text("Выберите категорию") },
            text = {
                LazyColumn {
                    items(categories) { cat ->
                        TextButton(
                            onClick = { itemCategory = cat; showCategories = false }
                        ) {
                            Text(cat)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategories = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
