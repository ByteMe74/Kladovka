package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ItemDetailsScreen(
    itemId: Long,
    repo: Repository,
    onBack: () -> Unit,
    onUpdate: (Item) -> Unit,
    onDelete: (Long) -> Unit,
    onAddItem: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var loading by remember { mutableStateOf(true) }
    var item by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(itemId) {
        item = repo.items.value.find { it.id == itemId }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вещь") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("Настройки")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        } else if (item != null) {
            val currentItem = item!!
            Box(modifier = Modifier.padding(paddingValues)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Название
                    OutlinedTextField(
                        value = currentItem.name,
                        onValueChange = { /* TODO: update and save */ },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Количество
                    OutlinedTextField(
                        value = currentItem.quantity.toString(),
                        onValueChange = { /* TODO: update and save */ },
                        label = { Text("Количество") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ед. измерения
                    OutlinedTextField(
                        value = currentItem.unit,
                        onValueChange = { /* TODO: update and save */ },
                        label = { Text("Ед. измерения") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Категория
                    OutlinedTextField(
                        value = currentItem.category,
                        onValueChange = { /* TODO: update and save */ },
                        label = { Text("Категория") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Заметки
                    OutlinedTextField(
                        value = currentItem.notes,
                        onValueChange = { /* TODO: update and save */ },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Действия
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { /* TODO: update item */ }) {
                            Text("Обновить")
                        }

                        TextButton(onClick = { onDelete(currentItem.id) }) {
                            Text("Удалить")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Закрепить/открепить
                    OutlinedButton(
                        onClick = { /* TODO: toggle pinned */ },
                        enabled = false
                    ) {
                        Text("Закреплена: ${if (currentItem.pinned) "Да" else "Нет"}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Кнопка "Добавить вещь"
                    OutlinedButton(onClick = onAddItem) {
                        Text("+ Добавить вещь")
                    }
                }
            }
        }
    }
}
