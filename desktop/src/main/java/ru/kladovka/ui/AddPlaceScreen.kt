package ru.kladovka.ui

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
fun AddPlaceScreen(
    repo: Repository,
    onBack: () -> Unit,
    onSave: (Place) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое место") },
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
                    label = { Text("Широта (например: 55.7558)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Долгота
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Долгота (например: 37.6173)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
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
                            onSave(Place(
                                name = name,
                                latitude = if (latitude.isNotBlank()) latitude.toDoubleOrNull() else null,
                                longitude = if (longitude.isNotBlank()) longitude.toDoubleOrNull() else null,
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
}
