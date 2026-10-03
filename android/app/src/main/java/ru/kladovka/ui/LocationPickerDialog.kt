package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocationPickerDialog(
    data: AppData,
    currentContainerId: Long?,
    currentShelfId: Long?,
    onSelect: (containerId: Long?, shelfId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Где лежит?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    PickerRow("Без места", currentContainerId == null && currentShelfId == null) {
                        onSelect(null, null)
                    }
                }
                data.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.id}") {
                        PickerRow(
                            if (shelf.location.isNotBlank()) "Стеллаж: ${shelf.name} · ${shelf.location}" else "Стеллаж: ${shelf.name}",
                            currentShelfId == shelf.id && currentContainerId == null
                        ) {
                            onSelect(null, shelf.id)
                        }
                    }
                }
                data.containers.forEach { container ->
                    item(key = "container-${container.id}") {
                        val shelfName = container.shelfId?.let { sid ->
                            data.shelves.firstOrNull { it.id == sid }?.name
                        }
                        val label = if (shelfName != null) {
                            if (container.location.isNotBlank()) "${container.name} ($shelfName, ${container.location})"
                            else "${container.name} ($shelfName)"
                        } else {
                            if (container.location.isNotBlank()) "${container.name} (без стеллажа, ${container.location})"
                            else "${container.name} (без стеллажа)"
                        }
                        PickerRow(label, currentContainerId == container.id) {
                            onSelect(container.id, null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

@Composable
fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}