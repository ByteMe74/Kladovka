package ru.kladovka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import android.widget.Toast
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
fun ContainerEditScreen(
    containerId: Long,
    vm: AppViewModel,
    onDone: () -> Unit
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val existing = if (containerId > 0) data.containers.firstOrNull { it.id == containerId } else null
    val isNew = existing == null

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var shelfId by rememberSaveable { mutableStateOf(existing?.shelfId) }
    var placeId by rememberSaveable { mutableStateOf(existing?.placeId) }
    var showShelfPicker by rememberSaveable { mutableStateOf(false) }
    var showPlacePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Пользователь что-то менял → перед выходом спросим, чтобы не потерять правки
    var touched by remember { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = touched) { showExitDialog = true }
    val context = LocalContext.current

    // Автонаименование нового контейнера: «Контейнер N» без повторений, пока имя не отредактировано
    val autoName = if (isNew) nextAutoName(data.containers.map { it.name }, "Контейнер") else null
    var lastAuto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoName) {
        val n = autoName ?: return@LaunchedEffect
        if (name.isBlank() || name == lastAuto) name = n
        lastAuto = n
    }

    val shelfLabel = shelfId?.let { id -> data.shelves.firstOrNull { it.id == id }?.name } ?: "Без стеллажа"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новый контейнер" else "Контейнер", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (!isNew) IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                    if (!isNew) IconButton(onClick = {
                        vm.saveContainer(0L, name.trim().ifBlank { "Копия" } + " (копия)", shelfId, placeId)
                        Toast.makeText(context, "Копия создана", Toast.LENGTH_SHORT).show()
                        onDone()
                    }) { Icon(Icons.Filled.ContentCopy, "Создать копию контейнера") }
                    TextButton(onClick = {
                        if (name.isNotBlank()) { vm.saveContainer(containerId, name.trim(), shelfId, placeId); onDone() }
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
                OutlinedButton(onClick = { showShelfPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Стеллаж: $shelfLabel")
                }
                OutlinedButton(onClick = { showPlacePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Place, null); Spacer(Modifier.width(8.dp))
                    Text("Место: ${data.placeName(placeId) ?: "Без места"}")
                }
            }
        }
    }

    if (showShelfPicker) {
        AlertDialog(
            onDismissRequest = { showShelfPicker = false },
            title = { Text("На каком стеллаже?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    item {
                        PickerRow("Без стеллажа", shelfId == null) { shelfId = null; touched = true; showShelfPicker = false }
                    }
                    data.shelves.forEach { shelf ->
                        item(key = "shelf-${shelf.id}") {
                            val placeName = data.placeName(shelf.placeId)
                            val label = if (placeName != null) "${shelf.name} · $placeName" else shelf.name
                            PickerRow(label, shelfId == shelf.id) { shelfId = shelf.id; touched = true; showShelfPicker = false }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShelfPicker = false }) { Text("Готово") } }
        )
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
            title = { Text("Удалить контейнер?") },
            text = { Text("Вещи из контейнера останутся, но будут отмечены как «без места».") },
            confirmButton = { TextButton(onClick = { vm.deleteContainer(containerId); onDone() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }
}