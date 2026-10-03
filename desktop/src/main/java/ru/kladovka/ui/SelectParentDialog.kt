package ru.kladovka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kladovka.data.*

// Диалог выбора места для стеллажа
@Composable
fun SelectPlaceDialog(
    places: List<Place>,
    onPlaceSelected: (Place) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Выберите место") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(places) { place ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { selectedPlace = place },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPlace == place) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            place.name,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedPlace?.let { onPlaceSelected(it) }
                onDismiss()
            }) {
                Text("Выбрано")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Диалог выбора стеллажа для полки
@Composable
fun SelectShelfDialog(
    shelves: List<Shelf>,
    onShelfSelected: (Shelf) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedShelf by remember { mutableStateOf<Shelf?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Выберите стеллаж") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(shelves) { shelf ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { selectedShelf = shelf },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedShelf == shelf) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            shelf.name,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedShelf?.let { onShelfSelected(it) }
                onDismiss()
            }) {
                Text("Выбрано")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Диалог выбора полки для контейнера
@Composable
fun SelectPolkaDialog(
    polkas: List<Polka>,
    onPolkaSelected: (Polka) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPolka by remember { mutableStateOf<Polka?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Выберите полку") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(polkas) { polka ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { selectedPolka = polka },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPolka == polka) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            polka.name,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedPolka?.let { onPolkaSelected(it) }
                onDismiss()
            }) {
                Text("Выбрано")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// Диалог выбора места для контейнера
@Composable
fun SelectContainerDialog(
    places: List<Place>,
    onPlaceSelected: (Place) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Выберите место для контейнера") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(places) { place ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { selectedPlace = place },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPlace == place) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                place.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (place.latitude != null && place.longitude != null) {
                                Text(
                                    "${place.latitude}, ${place.longitude}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedPlace?.let { onPlaceSelected(it) }
                onDismiss()
            }) {
                Text("Выбрано")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
