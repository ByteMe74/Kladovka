package ru.kladovka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.kladovka.data.*

@Composable
fun MainScreen(
    repo: Repository,
    settings: Settings,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onEditShelf: (Long) -> Unit,
    onDeleteShelf: (Long) -> Unit,
    onAddShelf: () -> Unit,
    onEditPolka: (Long) -> Unit,
    onDeletePolka: (Long) -> Unit,
    onAddPolka: () -> Unit,
    onEditContainer: (Long) -> Unit,
    onDeleteContainer: (Long) -> Unit,
    onAddContainer: () -> Unit,
    onEditPlace: (Long) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToServer: () -> Unit
) {
    val places by repo.places.collectAsState()
    val shelves by repo.shelves.collectAsState()
    val containers by repo.containers.collectAsState()
    val items by repo.items.collectAsState()
    val polki by repo.polki.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Вещи", "Стеллажи", "Полки", "Контейнеры", "Места")

    // Закреплённые вещи + остальные
    val pinnedItems = items.filter { it.pinned }
    val otherItems = items.filterNot { it.pinned }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kladovka Desktop") },
                actions = {
                    IconButton(onClick = { onNavigateToServer() }) {
                        Text("Сервер", modifier = Modifier.padding(end = 8.dp))
                    }
                    IconButton(onClick = { onNavigateToSettings() }) {
                        Text("Настройки")
                    }
                }
            )
        },
        bottomBar = {
            HorizontalRow(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        if (pinnedItems.isNotEmpty()) {
                            stickyHeader {
                                Row(
                                    modifier = Modifier
                                        .background(Color.LightGray)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Закреплённые", modifier = Modifier.padding(horizontal = 16.dp))
                                    TextButton(onClick = onAddItem) {
                                        Text("+ Добавить")
                                    }
                                }
                            }
                            items(pinnedItems) { item ->
                                ItemRow(item, onOpenItem)
                            }
                        }

                        if (otherItems.isNotEmpty()) {
                            stickyHeader {
                                Row(
                                    modifier = Modifier
                                        .background(Color.LightGray)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Другие", modifier = Modifier.padding(horizontal = 16.dp))
                                    TextButton(onClick = onAddItem) {
                                        Text("+ Добавить")
                                    }
                                }
                            }
                            items(otherItems) { item ->
                                ItemRow(item, onOpenItem)
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(shelves) { shelf ->
                            ShelfCard(shelf, onEditShelf, onDeleteShelf)
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = onAddShelf) {
                                    Text("+ Добавить стеллаж")
                                }
                            }
                        }
                    }
                }
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(polki) { polka ->
                            PolkaCard(polka, onEditPolka, onDeletePolka)
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = onAddPolka) {
                                    Text("+ Добавить полку")
                                }
                            }
                        }
                    }
                }
                3 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(containers) { container ->
                            ContainerCard(container, onEditContainer, onDeleteContainer)
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = onAddContainer) {
                                    Text("+ Добавить контейнер")
                                }
                            }
                        }
                    }
                }
                4 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(places) { place ->
                            PlaceCard(place, onEditPlace, onDeletePlace)
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = onAddPlace) {
                                    Text("+ Добавить место")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.category.isNotBlank()) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            TextButton(onClick = { onOpen(item.id) }) {
                Text("✎")
            }
        }
    }
}

@Composable
private fun ShelfCard(shelf: Shelf, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(shelf.name, style = MaterialTheme.typography.titleMedium)
                if (shelf.placeId != null) {
                    val place = repo.place(shelf.placeId)
                    if (place != null) {
                        Text(
                            "Место: ${place.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onEdit(shelf.id) }) {
                    Text("✎")
                }
                OutlinedButton(onClick = { onDelete(shelf.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Text("🗑")
                }
            }
        }
    }
}

@Composable
private fun PolkaCard(polka: Polka, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(polka.name, style = MaterialTheme.typography.titleMedium)
                if (polka.shelfId != null) {
                    val shelf = repo.shelf(polka.shelfId)
                    if (shelf != null) {
                        Text(
                            "Стеллаж: ${shelf.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onEdit(polka.id) }) {
                    Text("✎")
                }
                OutlinedButton(onClick = { onDelete(polka.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Text("🗑")
                }
            }
        }
    }
}

@Composable
private fun ContainerCard(container: Container, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(container.name, style = MaterialTheme.typography.titleMedium)
                if (container.placeId != null) {
                    val place = repo.place(container.placeId)
                    if (place != null) {
                        Text(
                            "М место: ${place.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onEdit(container.id) }) {
                    Text("✎")
                }
                OutlinedButton(onClick = { onDelete(container.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Text("🗑")
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                if (place.latitude != null && place.longitude != null) {
                    Text(
                        "${place.latitude}°, ${place.longitude}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onEdit(place.id) }) {
                    Text("✎")
                }
                OutlinedButton(onClick = { onDelete(place.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Text("🗑")
                }
            }
        }
    }
}
