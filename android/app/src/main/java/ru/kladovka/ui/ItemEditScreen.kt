package ru.kladovka.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    itemId: Long,
    vm: AppViewModel,
    onDone: () -> Unit
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val existingItem = if (itemId > 0) data.items.firstOrNull { it.id == itemId } else null
    val isNew = existingItem == null

    var name by remember(existingItem) { mutableStateOf(existingItem?.name ?: "") }
    var quantity by remember(existingItem) { mutableStateOf((existingItem?.quantity ?: 1).toString()) }
    var unit by remember(existingItem) { mutableStateOf(existingItem?.unit ?: "шт.") }
    var category by remember(existingItem) { mutableStateOf(existingItem?.category ?: "") }
    var notes by remember(existingItem) { mutableStateOf(existingItem?.notes ?: "") }
    var photoPath by remember(existingItem) { mutableStateOf(existingItem?.photoPath) }
    var containerId by remember(existingItem) { mutableStateOf(existingItem?.containerId) }
    var shelfId by remember(existingItem) { mutableStateOf(existingItem?.shelfId) }
    var placeId by remember(existingItem) { mutableStateOf(existingItem?.placeId) }
    var pinned by remember(existingItem) { mutableStateOf(existingItem?.pinned ?: false) }

    // Автонаименование новой вещи: «Вещь N» без повторений, пока имя не отредактировано вручную
    val autoName = if (isNew) nextAutoName(data.items.map { it.name }, "Вещь") else null
    var lastAuto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoName) {
        val n = autoName ?: return@LaunchedEffect
        if (name.isBlank() || name == lastAuto) name = n
        lastAuto = n
    }

    var showLocationPicker by rememberSaveable { mutableStateOf(false) }
    var showPlacePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Полноэкранный просмотр фото
    var showPhotoView by rememberSaveable { mutableStateOf(false) }
    // Пользователь что-то менял → перед выходом спросим, чтобы не потерять правки
    var touched by remember { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = touched) { showExitDialog = true }
    var categories by remember { mutableStateOf(emptyList<String>()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val parsedQty = quantity.toIntOrNull()?.coerceIn(0, 999999) ?: 0

    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingUri; pendingUri = null
        if (success && uri != null) vm.persistPhoto(uri) { p -> if (p != null) { photoPath = p; touched = true } }
    }
    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.persistPhoto(uri) { p -> if (p != null) { photoPath = p; touched = true } }
    }

    fun launchCamera() {
        val uri = PhotoHelper.createCaptureUri(context)
        pendingUri = uri
        takePictureLauncher.launch(uri)
    }

    LaunchedEffect(Unit) { vm.loadCategories { categories = it } }

    val locationLabel = when {
        containerId != null -> {
            val c = data.containers.firstOrNull { it.id == containerId }
            val s = c?.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid }?.name }
            if (c != null && s != null) "$s · ${c.name}" else c?.name ?: "Без места"
        }
        shelfId != null -> data.shelves.firstOrNull { it.id == shelfId }?.name ?: "Без места"
        else -> "Без места"
    }

    fun save() {
        if (name.isNotBlank()) {
            vm.saveItem(itemId, name.trim(), parsedQty, unit, category, notes, containerId, shelfId, placeId, photoPath)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новая вещь" else "Вещь", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                        // «Создать копию»: новая вещь с теми же полями (текущими значениями формы),
                        // имя + « (копия)». Удобно, когда похожих вещей несколько (пакеты, запчасти…).
                        IconButton(onClick = {
                            vm.saveItem(
                                0L,
                                name.trim().ifBlank { "Копия" } + " (копия)",
                                parsedQty, unit, category, notes,
                                containerId, shelfId, placeId, photoPath
                            )
                            Toast.makeText(context, "Копия создана", Toast.LENGTH_SHORT).show()
                            onDone()
                        }) {
                            Icon(Icons.Filled.ContentCopy, "Создать копию вещи")
                        }
                    }
                    TextButton(onClick = { save() }, enabled = name.isNotBlank()) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        // На широких экранах (планшеты) контент центрируется и не растягивается на всю ширину
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.fillMaxWidth().widthIn(max = 600.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Фото", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if (photoPath != null) {
                            AsyncImage(
                                model = photoModel(photoPath),
                                contentDescription = "Фото вещи",
                                modifier = Modifier
                                    .fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium)
                                    .clickable { showPhotoView = true },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { launchCamera() }) { Text("Переснять") }
                                TextButton(onClick = { photoPath = null; touched = true }) { Text("Удалить") }
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { launchCamera() }) {
                                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Сфото")
                                }
                                OutlinedButton(onClick = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Filled.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Галерея")
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(name, { name = it; touched = true }, label = { Text("Название *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6); touched = true }, label = { Text("Кол-во") }, modifier = Modifier.width(120.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(unit, { unit = it; touched = true }, label = { Text("Ед. (шт, кг…)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            item {
                OutlinedTextField(category, { category = it; touched = true }, label = { Text("Категория") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.take(10).forEach { c ->
                            SuggestionChip(onClick = { category = c; touched = true }, label = { Text(c, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
            item {
                OutlinedTextField(notes, { notes = it; touched = true }, label = { Text("Заметки") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            }
            item {
                OutlinedButton(onClick = { showLocationPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Место: $locationLabel")
                }
            }
            item {
                OutlinedButton(onClick = { showPlacePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Place, null); Spacer(Modifier.width(8.dp))
                    Text("Территория: ${data.placeName(placeId) ?: "Без места"}")
                }
            }
            // ⭐ Закрепить в начале списка (только для существующих записей)
            if (!isNew) item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star, null,
                            tint = if (pinned) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Закрепить в начале", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = pinned,
                        onCheckedChange = { newPinned ->
                            pinned = newPinned
                            vm.setItemPinned(itemId, newPinned)
                        }
                    )
                }
            }
        }
        }
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            data = data,
            currentContainerId = containerId,
            currentShelfId = shelfId,
            onSelect = { cId, sId -> containerId = cId; shelfId = sId; touched = true; showLocationPicker = false },
            onDismiss = { showLocationPicker = false }
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
            title = { Text("Удалить вещь?") },
            confirmButton = { TextButton(onClick = { vm.deleteItem(itemId); onDone() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }

    // Полноэкранный просмотр фото (тап по фото в редакторе)
    val photoOk = photoPath?.let { p ->
        p.startsWith("http://") || p.startsWith("https://") || p.startsWith("//") || File(p).exists()
    } ?: false
    if (showPhotoView && photoOk) {
        Dialog(
            onDismissRequest = { showPhotoView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showPhotoView = false }
            ) {
                AsyncImage(
                    model = photoModel(photoPath),
                    contentDescription = "Фото вещи",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showPhotoView = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, "Закрыть", tint = Color.White)
                }
            }
        }
    }
}

/** Модель фото для Coil: локальный файл или URL (фото, синхронизированное с сервера). */
private fun photoModel(path: String?): Any? = when {
    path == null || path.isEmpty() -> null
    path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//") -> path
    else -> File(path)
}