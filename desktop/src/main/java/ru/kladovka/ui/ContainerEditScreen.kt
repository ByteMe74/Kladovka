package ru.kladovka.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ContainerEditScreen(
    containerId: Long,
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Container) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var loading by remember { mutableStateOf(true) }
    var container by remember { mutableStateOf<Container?>(null) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var shelfId by remember { mutableStateOf<ContainerId?>(null) }
    var placeId by remember { mutableStateOf<ContainerId?>(null) }
    var location by remember { mutableStateOf("") }

    LaunchedEffect(containerId) {
        val c = repo.containers.value.find { it.id == containerId }
        container = c
        if (c != null) {
            name = c.name
            notes = c.notes
            shelfId = c.shelfId
            placeId = c.placeId
            location = c.location
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Контейнер") },
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
                    value = shelfId?.toString() ?: "",
                    onValueChange = { /* TODO: select shelf */ },
                    label = { Text("Стеллаж") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Место
                OutlinedTextField(
                    value = placeId?.toString() ?: "",
                    onValueChange = { /* TODO: select place */ },
                    label = { Text("Место") },
                    modifier = Modifier.fillMaxWidth(),
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
                    OutlinedButton(onClick = onCancel) {
                        Text("Отмена")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        if (name.isNotBlank()) {
                            onSave(container.copy(
                                id = container?.id ?: 0,
                                name = name,
                                shelfId = shelfId,
                                placeId = placeId,
                                location = location,
                                notes = notes
                            ))
                        }
                    }, enabled = name.isNotBlank()) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
