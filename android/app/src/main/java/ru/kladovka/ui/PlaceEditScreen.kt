package ru.kladovka.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(
    placeId: Long,
    vm: AppViewModel,
    onDone: () -> Unit,
    pendingLat: Double? = null,
    pendingLon: Double? = null,
    onPendingConsumed: () -> Unit = {}
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val existing = if (placeId > 0) data.places.firstOrNull { it.id == placeId } else null
    val isNew = existing == null

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var latText by rememberSaveable { mutableStateOf(existing?.latitude?.toString() ?: "") }
    var lonText by rememberSaveable { mutableStateOf(existing?.longitude?.toString() ?: "") }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Пользователь что-то менял → перед выходом спросим, чтобы не потерять правки
    var touched by remember { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = touched) { showExitDialog = true }
    val context = LocalContext.current

    val lat = latText.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon = lonText.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

    // Автонаименование нового места: сначала «Место N», а при появлении координат — адрес точки
    val autoBase = if (isNew) nextAutoName(data.places.map { it.name }, "Место") else null
    var lastAuto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoBase) {
        lastAuto = autoBase
        if (name.isBlank()) name = autoBase ?: ""
    }
    LaunchedEffect(lat, lon) {
        val a = lat
        val b = lon
        if (isNew && a != null && b != null) {
            AddressCache.address(context, a, b)?.let { addr ->
                val short = addr.take(60)
                // Подставляем адрес, только пока имя не отредактировано вручную
                if (name.isBlank() || name == lastAuto) {
                    name = short
                    lastAuto = short
                }
            }
        }
    }

    // Координаты, присланные из внешних карт через «Поделиться» (Google/Яндекс Карты)
    LaunchedEffect(pendingLat, pendingLon) {
        if (pendingLat != null && pendingLon != null) {
            latText = String.format(Locale.US, "%.6f", pendingLat)
            lonText = String.format(Locale.US, "%.6f", pendingLon)
            Toast.makeText(context, "Координаты получены из карт", Toast.LENGTH_SHORT).show()
            onPendingConsumed()
        }
    }

    // Разрешение на геолокацию: карта по умолчанию открывается на текущем месте
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openMapForPick(context)
        } else {
            Toast.makeText(context, "Нет доступа к геолокации: карта откроется без вашей позиции", Toast.LENGTH_LONG).show()
            openMapForPick(context)
        }
    }

    fun pickOnMap() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            openMapForPick(context)
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isNew) "Новое место" else "Место", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                    actions = {
                        if (!isNew) IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                        if (!isNew) IconButton(onClick = {
                            vm.savePlace(0L, name.trim().ifBlank { "Копия" } + " (копия)", notes.trim(), lat, lon)
                            Toast.makeText(context, "Копия создана", Toast.LENGTH_SHORT).show()
                            onDone()
                        }) { Icon(Icons.Filled.ContentCopy, "Создать копию места") }
                        TextButton(onClick = {
                            if (name.isNotBlank()) {
                                vm.savePlace(placeId, name.trim(), notes.trim(), lat, lon)
                                onDone()
                            }
                        }, enabled = name.isNotBlank()) { Text("Сохранить") }
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
                    OutlinedTextField(
                        name, { name = it; touched = true },
                        label = { Text("Название *") },
                        placeholder = { Text("например: Кладовая, Балкон, Гараж, Подвал") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickOnMap() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Place, null, Modifier.width(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Выбрать в картах…")
                        }
                        // На узких экранах поля координат ставятся друг под другом
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth < 420.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CoordField(latText, { latText = it; touched = true }, "Широта")
                                    CoordField(lonText, { lonText = it; touched = true }, "Долгота")
                                    if (latText.isNotBlank() || lonText.isNotBlank()) {
                                        IconButton(onClick = { latText = ""; lonText = ""; touched = true }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Очистить координаты")
                                        }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CoordField(latText, { latText = it; touched = true }, "Широта", Modifier.weight(1f))
                                    CoordField(lonText, { lonText = it; touched = true }, "Долгота", Modifier.weight(1f))
                                    if (latText.isNotBlank() || lonText.isNotBlank()) {
                                        IconButton(onClick = { latText = ""; lonText = ""; touched = true }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Очистить координаты")
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "«Выбрать в картах…» откроет Google, Яндекс или 2ГИС на вашем текущем месте. " +
                                "Поставьте точку (в Яндексе — долгим касанием карты) и нажмите «Поделиться», " +
                                "выбрав Кладовку: координаты подставятся автоматически. " +
                                "Можно также вписать их вручную.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        notes, { notes = it; touched = true },
                        label = { Text("Заметки") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.width(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Место можно назначать стеллажам, полкам, контейнерам и вещам (опционально).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
        }
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
            title = { Text("Удалить место?") },
            text = { Text("Стеллажи, полки, контейнеры и вещи этого места останутся, но будут без места.") },
            confirmButton = { TextButton(onClick = { vm.deletePlace(placeId); onDone() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }
}

/** Поле координат (широта/долгота) с фильтром ввода. */
@Composable
private fun CoordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(16)) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}