package ru.kladovka.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Диалог выбора места (из списка мест). Можно выбрать «Без места».
 *
 * @param currentPlaceId текущая привязка (null = без места)
 * @param onSelect       вызывается при выборе: (placeId или null)
 * @param onDismiss      закрыть диалог без выбора
 */
@Composable
fun PlacePickerDialog(
    data: AppData,
    currentPlaceId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Место (необязательно)") },
        text = {
            LazyColumn(Modifier.heightIn(max = 350.dp)) {
                item {
                    PickerRow("Без места", currentPlaceId == null) { onSelect(null) }
                }
                data.places.forEach { place ->
                    item(key = "place-${place.id}") {
                        val label = coordsText(place)?.let { "${place.name} ($it)" } ?: place.name
                        PickerRow(label, currentPlaceId == place.id) { onSelect(place.id) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}