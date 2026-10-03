package ru.kladovka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfEditScreen(
    shelfId: Long,
    vm: AppViewModel,
    onDone: () -> Unit
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val existing = if (shelfId > 0) data.shelves.firstOrNull { it.id == shelfId } else null
    val isNew = existing == null

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var placeId by rememberSaveable { mutableStateOf(existing?.placeId) }
    var showPlacePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Пользователь что-то менял → перед выходом спросим, чтобы не потерять правки
    var touched by remember { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = touched) { showExitDialog = true }
    val context = LocalContext.current

    // Автонаименование нового стеллажа: «Стеллаж N» без повторений, пока имя не отредактировано
    val autoName = if (isNew) nextAutoName(data.shelves.map { it.name }, "Стеллаж") else null
    var lastAuto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoName) {
        val n = autoName ?: return@LaunchedEffect
        if (name.isBlank() || name == lastAuto) name = n
        lastAuto = n
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новый стеллаж" else "Стеллаж", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (!isNew) IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                    if (!isNew) IconButton(onClick = {
                        vm.saveShelf(0L, name.trim().ifBlank { "Копия" } + " (копия)", notes.trim(), placeId)
                        Toast.makeText(context, "Копия создана", Toast.LENGTH_SHORT).show()
                        onDone()
                    }) { Icon(Icons.Filled.ContentCopy, "Создать копию стеллажа") }
                    TextButton(onClick = {
                        if (name.isNotBlank()) { vm.saveShelf(shelfId, name.trim(), notes.trim(), placeId); onDone() }
                    }, enabled = name.isNotBlank()) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        // На широких экранах (планшеты) контент центрируется и не растягивается на всю ширину;
        // прокрутка + imePadding — поля не прячутся под клавиатуру на маленьких экранах.
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 600.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            OutlinedTextField(name, { name = it; touched = true }, label = { Text("Название *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { showPlacePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Place, null); Spacer(Modifier.width(8.dp))
                Text("Место: ${data.placeName(placeId) ?: "Без места"}")
            }
            OutlinedTextField(notes, { notes = it; touched = true }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (data.places.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сначала добавьте места на вкладке «Места», чтобы назначить стеллажу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }
    }

    if (showPlacePicker) {
        PlacePickerDialog(
            data = data,
            currentPlaceId = placeId,
            onSelect = { placeId = it; touched = true; showPlacePicker = false },
            onDismiss = { showPlacePicker = false }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Несохранённые изменения") },
            text = { Text("Выйти без сохранения? Правки будут потеряны.") },
            confirmButton = { TextButton(onClick = { onDone() }) { Text("Выйти", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Остаться") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить стеллаж?") },
            text = { Text("Контейнеры и вещи с этого стеллажа останутся, но будут отмечены как «без места».") },
            confirmButton = { TextButton(onClick = { vm.deleteShelf(shelfId); onDone() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }
}