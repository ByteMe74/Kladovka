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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PlaceEditScreen(
    placeId: Long,
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Place) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var loading by remember { mutableStateOf(true) }
    var place by remember { mutableStateOf<Place?>(null) }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    LaunchedEffect(placeId) {
        val p = repo.places.value.find { it.id == placeId }
        place = p
        if (p != null) {
            name = p.name
            notes = p.notes
            latitude = p.latitude?.toString() ?: ""
            longitude = p.longitude?.toString() ?: ""
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Место") },
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

                // Широта
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Широта") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    supportingText = {
                        Text("Например: 55.7558", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Долгота
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Долгота") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    supportingText = {
                        Text("Например: 37.6173", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                            onSave(place.copy(
                                id = place?.id ?: 0,
                                name = name,
                                latitude = if (latitude.isNotBlank()) latitude.toDoubleOrNull() else null,
                                longitude = if (longitude.isNotBlank()) longitude.toDoubleOrNull() else null,
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
