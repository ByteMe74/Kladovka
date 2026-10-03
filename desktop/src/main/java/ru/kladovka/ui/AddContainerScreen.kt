package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddContainerScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Container) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var shelfId by remember { mutableStateOf<Long?>(null) }
    var placeId by remember { mutableStateOf<Long?>(null) }
    var selectedShelf by remember { mutableStateOf<Shelf?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var showShelfDialog by remember { mutableStateOf(false) }
    var showPlaceDialog by remember { mutableStateOf(false) }

    val shelves by repo.shelves.collectAsState()
    val places by repo.places.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый контейнер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Назад")
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Название
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Стеллаж
                OutlinedTextField(
                    value = selectedShelf?.name ?: "Выберите стеллаж...",
                    onValueChange = { /* TODO */ },
                    label = { Text("Стеллаж") },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showShelfDialog = true },
                    enabled = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Место
                OutlinedTextField(
                    value = selectedPlace?.name ?: "Выберите место...",
                    onValueChange = { /* TODO */ },
                    label = { Text("Место") },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPlaceDialog = true },
                    enabled = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Расположение
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Расположение") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Заметки
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onBack) {
                        Text("Отмена")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        if (name.isNotBlank()) {
                            onSave(Container(
                                name = name,
                                shelfId = shelfId,
                                placeId = placeId,
                                location = location,
                                notes = notes
                            ))
                            onBack()
                        }
                    }, enabled = name.isNotBlank()) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }

    // Диалог выбора стеллажа
    if (showShelfDialog) {
        AlertDialog(
            onDismissRequest = { showShelfDialog = false },
            title = { Text("Выберите стеллаж") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(shelves) { shelf ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedShelf = shelf
                                    shelfId = shelf.id
                                    showShelfDialog = false
                                }
                        ) {
                            Text(
                                "${shelf.name}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShelfDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог выбора места
    if (showPlaceDialog) {
        AlertDialog(
            onDismissRequest = { showPlaceDialog = false },
            title = { Text("Выберите место") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(places) { place ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlace = place
                                    placeId = place.id
                                    showPlaceDialog = false
                                }
                        ) {
                            Text(
                                "${place.name}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaceDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
