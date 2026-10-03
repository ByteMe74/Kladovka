package ru.kladovka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import ru.kladovka.data.AppData
import ru.kladovka.data.Container
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Polka
import ru.kladovka.data.Shelf
import ru.kladovka.data.SqliteDatabase
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser

/*
 * Диалоги редактирования. В отличие от прежней версии порта поля здесь связаны
 * с состоянием по-настоящему: TextField стартует из существующей сущности,
 * а сохранение вызывает реальный метод БД, а не заглушку.
 *
 * В каждом редакторе есть две вещи, которых раньше не было, а на Android они
 * были: подтверждение выхода с несохранёнными правками и «Создать копию».
 */

/**
 * Обработчик выхода из редактора: спрашивает подтверждение, если [dirty].
 *
 * Раньше закрытие окна с несохранёнными правками молча их теряло — на Android
 * для этого стоит `BackHandler` с предупреждением во всех пяти редакторах.
 * Возвращаемое значение передаётся в `onDismissRequest`.
 */
@Composable
private fun unsavedGuard(dirty: Boolean, onDiscard: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Несохранённые изменения") },
            text = { Text("Изменения не сохранены. Выйти без сохранения?") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Остаться") } }
        )
    }
    return if (dirty) ({ ask = true }) else onDiscard
}

/** Выбор одной сущности из списка («Место: Кладова»). */
@Composable
private fun <T> PickerField(
    label: String,
    options: List<T>,
    selectedId: Long?,
    idOf: (T) -> Long,
    nameOf: (T) -> String,
    onPick: (Long?) -> Unit,
    allowNone: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { idOf(it) == selectedId }?.let(nameOf) ?: "Не выбрано"

    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedId == null && allowNone) "Не выбрано" else current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text("Не выбрано") },
                    onClick = { onPick(null); open = false }
                )
            }
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(nameOf(opt)) },
                    onClick = { onPick(idOf(opt)); open = false }
                )
            }
        }
    }
}

/** Подсказки из уже встречавшихся категорий — чтобы не вводить одни и те же строки. */
@Composable
private fun CategoryField(
    value: String,
    suggestions: List<String>,
    onChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("Категория") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (suggestions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.take(4).forEach { s ->
                    AssistChip(onClick = { onChange(s) }, label = { Text(s) })
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ------------------------------------------------------------------ Место

@Composable
fun PlaceDialog(
    db: SqliteDatabase,
    place: Place?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(place?.name.orEmpty()) }
    var lat by remember { mutableStateOf(place?.latitude?.toString().orEmpty()) }
    var lon by remember { mutableStateOf(place?.longitude?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(place?.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dirty = name != place?.name.orEmpty() ||
        lat != place?.latitude?.toString().orEmpty() ||
        lon != place?.longitude?.toString().orEmpty() ||
        notes != place?.notes.orEmpty()
    val dismiss = unsavedGuard(dirty, onDismiss)

    if (confirmDelete && place != null) {
        ConfirmDialog(
            "Удалить место?",
            "Стеллажи, контейнеры и вещи останутся на своих местах, но потеряют привязку к «${place.name}».",
            "Удалить",
            onConfirm = {
                db.deletePlace(place.id)
                confirmDelete = false
                onDismiss()
            },
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (place == null) "Новое место" else "Место") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it },
                    label = { Text("Широта") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lon, onValueChange = { lon = it },
                    label = { Text("Долгота") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    db.savePlace(
                        Place(
                            id = place?.id ?: 0,
                            name = name.trim(),
                            latitude = lat.trim().replace(',', '.').toDoubleOrNull(),
                            longitude = lon.trim().replace(',', '.').toDoubleOrNull(),
                            notes = notes.trim()
                        )
                    )
                    onDismiss()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (place != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            db.savePlace(
                                Place(
                                    id = 0,
                                    name = name.trim() + " (копия)",
                                    latitude = lat.trim().replace(',', '.').toDoubleOrNull(),
                                    longitude = lon.trim().replace(',', '.').toDoubleOrNull(),
                                    notes = notes.trim()
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Создать копию") }
                }
                TextButton(onClick = dismiss) { Text("Отмена") }
            }
        }
    )
}

// ------------------------------------------------------------------ Стеллаж

@Composable
fun ShelfDialog(
    db: SqliteDatabase,
    data: AppData,
    shelf: Shelf?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(shelf?.name.orEmpty()) }
    var notes by remember { mutableStateOf(shelf?.notes.orEmpty()) }
    var placeId by remember { mutableStateOf(shelf?.placeId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dirty = name != shelf?.name.orEmpty() ||
        notes != shelf?.notes.orEmpty() ||
        placeId != shelf?.placeId
    val dismiss = unsavedGuard(dirty, onDismiss)

    if (confirmDelete && shelf != null) {
        ConfirmDialog(
            "Удалить стеллаж?",
            "Полки, контейнеры и вещи с него сохранятся, но останутся без привязки.",
            "Удалить",
            onConfirm = { db.deleteShelf(shelf.id); confirmDelete = false; onDismiss() },
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (shelf == null) "Новый стеллаж" else "Стеллаж") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = "Место",
                    options = data.places,
                    idOf = { it.id },
                    selectedId = placeId,
                    nameOf = { it.name },
                    onPick = { placeId = it }
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    db.saveShelf(
                        Shelf(
                            id = shelf?.id ?: 0,
                            name = name.trim(),
                            notes = notes.trim(),
                            placeId = placeId,
                            location = shelf?.location.orEmpty()
                        )
                    )
                    onDismiss()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (shelf != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            db.saveShelf(
                                Shelf(
                                    id = 0,
                                    name = name.trim() + " (копия)",
                                    notes = notes.trim(),
                                    placeId = placeId,
                                    location = shelf.location
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Создать копию") }
                }
                TextButton(onClick = dismiss) { Text("Отмена") }
            }
        }
    )
}

// ------------------------------------------------------------------ Полка

@Composable
fun PolkaDialog(
    db: SqliteDatabase,
    data: AppData,
    polka: Polka?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(polka?.name.orEmpty()) }
    var notes by remember { mutableStateOf(polka?.notes.orEmpty()) }
    var shelfId by remember { mutableStateOf(polka?.shelfId) }
    var placeId by remember { mutableStateOf(polka?.placeId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dirty = name != polka?.name.orEmpty() ||
        notes != polka?.notes.orEmpty() ||
        shelfId != polka?.shelfId ||
        placeId != polka?.placeId
    val dismiss = unsavedGuard(dirty, onDismiss)

    if (confirmDelete && polka != null) {
        ConfirmDialog(
            "Удалить полку?",
            "Полка будет удалена. Вещи, привязанные к ней напрямую, сохранятся.",
            "Удалить",
            onConfirm = { db.deletePolka(polka.id); confirmDelete = false; onDismiss() },
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (polka == null) "Новая полка" else "Полка") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = "Стеллаж",
                    options = data.shelves,
                    idOf = { it.id },
                    selectedId = shelfId,
                    nameOf = { it.name },
                    onPick = { shelfId = it }
                )
                PickerField(
                    label = "Место",
                    options = data.places,
                    idOf = { it.id },
                    selectedId = placeId,
                    nameOf = { it.name },
                    onPick = { placeId = it }
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    db.savePolka(
                        Polka(
                            id = polka?.id ?: 0,
                            name = name.trim(),
                            notes = notes.trim(),
                            shelfId = shelfId,
                            placeId = placeId
                        )
                    )
                    onDismiss()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (polka != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            db.savePolka(
                                Polka(
                                    id = 0,
                                    name = name.trim() + " (копия)",
                                    notes = notes.trim(),
                                    shelfId = shelfId,
                                    placeId = placeId
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Создать копию") }
                }
                TextButton(onClick = dismiss) { Text("Отмена") }
            }
        }
    )
}

// ------------------------------------------------------------------ Контейнер

@Composable
fun ContainerDialog(
    db: SqliteDatabase,
    data: AppData,
    container: Container?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(container?.name.orEmpty()) }
    var shelfId by remember { mutableStateOf(container?.shelfId) }
    var placeId by remember { mutableStateOf(container?.placeId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dirty = name != container?.name.orEmpty() ||
        shelfId != container?.shelfId ||
        placeId != container?.placeId
    val dismiss = unsavedGuard(dirty, onDismiss)

    if (confirmDelete && container != null) {
        ConfirmDialog(
            "Удалить контейнер?",
            "Вещи из контейнера сохранятся, но останутся без привязки.",
            "Удалить",
            onConfirm = { db.deleteContainer(container.id); confirmDelete = false; onDismiss() },
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (container == null) "Новый контейнер" else "Контейнер") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    label = "Стеллаж",
                    options = data.shelves,
                    idOf = { it.id },
                    selectedId = shelfId,
                    nameOf = { it.name },
                    onPick = { shelfId = it }
                )
                PickerField(
                    label = "Место",
                    options = data.places,
                    idOf = { it.id },
                    selectedId = placeId,
                    nameOf = { it.name },
                    onPick = { placeId = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    db.saveContainer(
                        Container(
                            id = container?.id ?: 0,
                            name = name.trim(),
                            shelfId = shelfId,
                            placeId = placeId,
                            location = container?.location.orEmpty()
                        )
                    )
                    onDismiss()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (container != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            db.saveContainer(
                                Container(
                                    id = 0,
                                    name = name.trim() + " (копия)",
                                    shelfId = shelfId,
                                    placeId = placeId,
                                    location = container.location
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Создать копию") }
                }
                TextButton(onClick = dismiss) { Text("Отмена") }
            }
        }
    )
}

// ------------------------------------------------------------------ Вещь

@Composable
fun ItemDialog(
    db: SqliteDatabase,
    data: AppData,
    item: Item?,
    suggestions: List<String>,
    /** Куда складывать выбранные файлы фото. Android хранит их в своём внутреннем
     *  хранилище, здесь — подкаталог photos рядом с базой. */
    photoDir: File,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var qty by remember { mutableStateOf((item?.quantity ?: 1).toString()) }
    var unit by remember { mutableStateOf(item?.unit.orEmpty()) }
    var category by remember { mutableStateOf(item?.category.orEmpty()) }
    var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
    var containerId by remember { mutableStateOf(item?.containerId) }
    var shelfId by remember { mutableStateOf(item?.shelfId) }
    var placeId by remember { mutableStateOf(item?.placeId) }
    var pinned by remember { mutableStateOf(item?.pinned ?: false) }
    var photoPath by remember { mutableStateOf(item?.photoPath) }
    var confirmDelete by remember { mutableStateOf(false) }

    val dirty = name != item?.name.orEmpty() ||
        qty != (item?.quantity ?: 1).toString() ||
        unit != item?.unit.orEmpty() ||
        category != item?.category.orEmpty() ||
        notes != item?.notes.orEmpty() ||
        containerId != item?.containerId ||
        shelfId != item?.shelfId ||
        placeId != item?.placeId ||
        photoPath != item?.photoPath ||
        pinned != (item?.pinned ?: false)
    val dismiss = unsavedGuard(dirty, onDismiss)

    if (confirmDelete && item != null) {
        ConfirmDialog(
            "Удалить вещь?",
            "«${item.name}» будет удалена из базы.",
            "Удалить",
            onConfirm = { db.deleteItem(item.id); confirmDelete = false; onDismiss() },
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (item == null) "Новая вещь" else "Вещь") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoBlock(
                    photoPath = photoPath,
                    photoDir = photoDir,
                    onPick = { picked ->
                        // Копируем файл внутрь каталога приложения: путь из
                        // проводника уедет вместе с файлом пользователя.
                        try {
                            photoDir.mkdirs()
                            val target = File(photoDir, UUID.randomUUID().toString() + ".jpg")
                            picked.copyTo(target, overwrite = true)
                            photoPath = target.absolutePath
                        } catch (_: Exception) {
                            // Не смогли скопировать — оставляем путь как есть,
                            // товар не должен потеряться из-за фотографии.
                        }
                    },
                    onClear = { photoPath = null }
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty, onValueChange = { v -> qty = v.filter { it.isDigit() } },
                        label = { Text("Кол-во") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text("Ед.") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                CategoryField(category, suggestions) { category = it }
                PickerField(
                    label = "Контейнер",
                    options = data.containers,
                    idOf = { it.id },
                    selectedId = containerId,
                    nameOf = { it.name },
                    onPick = { containerId = it }
                )
                PickerField(
                    label = "Стеллаж",
                    options = data.shelves,
                    idOf = { it.id },
                    selectedId = shelfId,
                    nameOf = { it.name },
                    onPick = { shelfId = it }
                )
                PickerField(
                    label = "Место",
                    options = data.places,
                    idOf = { it.id },
                    selectedId = placeId,
                    nameOf = { it.name },
                    onPick = { placeId = it }
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { pinned = !pinned }) {
                    Text(if (pinned) "⭐ Закреплено" else "☆ Закрепить")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    db.saveItem(
                        Item(
                            id = item?.id ?: 0,
                            name = name.trim(),
                            quantity = qty.toIntOrNull() ?: 1,
                            unit = unit.trim(),
                            category = category.trim(),
                            notes = notes.trim(),
                            containerId = containerId,
                            shelfId = shelfId,
                            placeId = placeId,
                            photoPath = photoPath,
                            pinned = pinned,
                            createdAt = item?.createdAt ?: 0,
                            updatedAt = item?.updatedAt ?: 0
                        )
                    )
                    onDismiss()
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (item != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            db.saveItem(
                                Item(
                                    id = 0,
                                    name = name.trim() + " (копия)",
                                    quantity = qty.toIntOrNull() ?: 1,
                                    unit = unit.trim(),
                                    category = category.trim(),
                                    notes = notes.trim(),
                                    containerId = containerId,
                                    shelfId = shelfId,
                                    placeId = placeId,
                                    photoPath = photoPath,
                                    pinned = pinned
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Создать копию") }
                }
                TextButton(onClick = dismiss) { Text("Отмена") }
            }
        }
    )
}

/**
 * Фотоблок вещи: превью, выбор файла и удаление.
 *
 * На Android фото снимается камерой или берётся из галереи, здесь — выбирается
 * файл с диска. Само поле `photoPath` переносится при обмене, но управлять им
 * было нечем: путь менялся только тем, что база его аккуратно сохраняла.
 */
@Composable
private fun PhotoBlock(
    photoPath: String?,
    photoDir: File,
    onPick: (File) -> Unit,
    onClear: () -> Unit
) {
    val file = photoPath?.let { p -> if (File(p).isAbsolute) File(p) else File(photoDir, p) }
    // Читаем файл один раз на путь: иначе на каждый кадр диск открывался заново.
    val bitmap = remember(photoPath) {
        file?.takeIf { it.isFile && it.length() > 0L }
            ?.let { runCatching { loadImageBitmap(it.inputStream()) }.getOrNull() }
    }

    Column(Modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Фото вещи",
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val chooser = JFileChooser().apply { dialogTitle = "Выберите фото вещи" }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    onPick(chooser.selectedFile)
                }
            }) { Text("Прикрепить фото") }
            if (photoPath != null) {
                OutlinedButton(onClick = onClear) { Text("Убрать фото") }
            }
        }
    }
}