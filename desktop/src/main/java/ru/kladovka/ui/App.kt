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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ru.kladovka.data.*
import kotlinx.coroutines.launch

@Composable
fun App(
    initialSettings: Settings
) {
    val scope = rememberCoroutineScope()
    val repo = remember { Repository() }
    val settings = remember { initialSettings }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Вещи", "Стеллажи", "Полки", "Контейнеры", "Места", "Сервер", "Настройки")

    // Dialog flags
    var showShelfDialog by remember { mutableStateOf(false) }
    var showShelfEditDialog by remember { mutableStateOf(false) }
    var showPolkaDialog by remember { mutableStateOf(false) }
    var showPolkaEditDialog by remember { mutableStateOf(false) }
    var showContainerDialog by remember { mutableStateOf(false) }
    var showContainerEditDialog by remember { mutableStateOf(false) }
    var showPlaceDialog by remember { mutableStateOf(false) }
    var showPlaceEditDialog by remember { mutableStateOf(false) }
    var showItemDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Selection dialogs
    var showShelfPlaceDialog by remember { mutableStateOf(false) }
    var showPolkaShelfDialog by remember { mutableStateOf(false) }
    var showContainerPlaceDialog by remember { mutableStateOf(false) }

    // Selection state
    var selectedPlaceForShelf by remember { mutableStateOf<Place?>(null) }
    var selectedShelfForPolka by remember { mutableStateOf<Shelf?>(null) }
    var selectedPlaceForContainer by remember { mutableStateOf<Place?>(null) }

    // Delete callbacks
    val onDeleteShelf = remember {
        { shelfId: Long ->
            scope.launch {
                repo.shelf(shelfId)?.let { shelf ->
                    repo.deleteShelf(shelfId)
                    println("Удален стеллаж: ${shelf.name}")
                }
            }
        }
    }

    val onDeletePolka = remember {
        { polkaId: Long ->
            scope.launch {
                repo.polka(polkaId)?.let { polka ->
                    repo.deletePolka(polkaId)
                    println("Удалена полка: ${polka.name}")
                }
            }
        }
    }

    val onDeleteContainer = remember {
        { containerId: Long ->
            scope.launch {
                repo.container(containerId)?.let { container ->
                    repo.deleteContainer(containerId)
                    println("Удален контейнер: ${container.name}")
                }
            }
        }
    }

    val onDeletePlace = remember {
        { placeId: Long ->
            scope.launch {
                repo.place(placeId)?.let { place ->
                    repo.deletePlace(placeId)
                    println("Удалено место: ${place.name}")
                }
            }
        }
    }

    // Sync state
    var syncState by remember { mutableStateOf(SyncState(id = 0, status = "Никогда", lastSyncAt = 0, lastError = null)) }

    Window(
        onCloseRequest = ::System.exit(0),
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        AppContent(
            repo = repo,
            settings = settings,
            onDeleteShelf = onDeleteShelf,
            onDeletePolka = onDeletePolka,
            onDeleteContainer = onDeleteContainer,
            onDeletePlace = onDeletePlace,
            onNavigateToServer = { showServerDialog = true },
            onNavigateToSettings = { showSettingsDialog = true },
            onOpenShelfEdit = { id -> 
                if (id == 0L) {
                    AddShelfScreen(repo, onBack = { selectedTab = 1 }, onSave = { shelf -> repo.addShelf(shelf) })
                } else {
                    EditShelfScreen(repo, onBack = { selectedTab = 1 }, onSave = { shelf -> repo.updateShelf(shelf) })
                }
            },
            onOpenPolkaEdit = { id -> 
                if (id == 0L) {
                    AddPolkaScreen(repo, onBack = { selectedTab = 2 }, onSave = { polka -> repo.addPolka(polka) })
                } else {
                    EditPolkaScreen(repo, onBack = { selectedTab = 2 }, onSave = { polka -> repo.updatePolka(polka) })
                }
            },
            onOpenContainerEdit = { id -> 
                if (id == 0L) {
                    AddContainerScreen(repo, onBack = { selectedTab = 3 }, onSave = { container -> repo.addContainer(container) })
                } else {
                    EditContainerScreen(repo, onBack = { selectedTab = 3 }, onSave = { container -> repo.updateContainer(container) })
                }
            },
            onOpenPlaceEdit = { id -> 
                if (id == 0L) {
                    AddPlaceScreen(repo, onBack = { selectedTab = 4 }, onSave = { place -> repo.addPlace(place) })
                } else {
                    EditPlaceScreen(repo, onBack = { selectedTab = 4 }, onSave = { place -> repo.updatePlace(place) })
                }
            },
            onOpenItemEdit = { id -> 
                if (id == 0L) {
                    AddItemScreen(repo, onBack = { selectedTab = 0 }, onSave = { item -> repo.addItem(item) })
                } else {
                    ItemEditScreen(repo, onBack = { selectedTab = 0 }, onSave = { item -> repo.updateItem(item) })
                }
            },
            syncState = syncState,
            onSync = {
                scope.launch {
                    syncState = repo.sync()
                }
            }
        )

        // Selection dialogs
        if (showShelfPlaceDialog) {
            SelectPlaceDialog(
                places = repo.places.value,
                onPlaceSelected = { place ->
                    selectedPlaceForShelf = place
                    showShelfPlaceDialog = false
                },
                onDismiss = { showShelfPlaceDialog = false }
            )
        }

        if (showPolkaShelfDialog) {
            SelectShelfDialog(
                shelves = repo.shelves.value,
                onShelfSelected = { shelf ->
                    selectedShelfForPolka = shelf
                    showPolkaShelfDialog = false
                },
                onDismiss = { showPolkaShelfDialog = false }
            )
        }

        if (showContainerPlaceDialog) {
            SelectPlaceDialog(
                places = repo.places.value,
                onPlaceSelected = { place ->
                    selectedPlaceForContainer = place
                    showContainerPlaceDialog = false
                },
                onDismiss = { showContainerPlaceDialog = false }
            )
        }

        // Dialog: Add Shelf
        if (showShelfDialog) {
            AlertDialog(
                onDismissRequest = { showShelfDialog = false },
                title = { Text("Новый стеллаж") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showShelfPlaceDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Место: ${repo.places.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShelfDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShelfDialog = false }) {
                        Text("Создать")
                    }
                }
            )
        }

        // Dialog: Edit Shelf
        if (showShelfEditDialog) {
            AlertDialog(
                onDismissRequest = { showShelfEditDialog = false },
                title = { Text("Редактировать стеллаж") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showShelfPlaceDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Место: ${repo.places.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShelfEditDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShelfEditDialog = false }) {
                        Text("Сохранить")
                    }
                }
            )
        }

        // Dialog: Add Polka
        if (showPolkaDialog) {
            AlertDialog(
                onDismissRequest = { showPolkaDialog = false },
                title = { Text("Новая полка") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showPolkaShelfDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Стеллаж: ${repo.shelves.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPolkaDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPolkaDialog = false }) {
                        Text("Создать")
                    }
                }
            )
        }

        // Dialog: Edit Polka
        if (showPolkaEditDialog) {
            AlertDialog(
                onDismissRequest = { showPolkaEditDialog = false },
                title = { Text("Редактировать полку") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showPolkaShelfDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Стеллаж: ${repo.shelves.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPolkaEditDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPolkaEditDialog = false }) {
                        Text("Сохранить")
                    }
                }
            )
        }

        // Dialog: Add Container
        if (showContainerDialog) {
            AlertDialog(
                onDismissRequest = { showContainerDialog = false },
                title = { Text("Новый контейнер") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Расположение") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showContainerPlaceDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Место: ${repo.places.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showContainerDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showContainerDialog = false }) {
                        Text("Создать")
                    }
                }
            )
        }

        // Dialog: Edit Container
        if (showContainerEditDialog) {
            AlertDialog(
                onDismissRequest = { showContainerEditDialog = false },
                title = { Text("Редактировать контейнер") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Расположение") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                onClick = { showContainerPlaceDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Место: ${repo.places.value.take(1).firstOrNull()?.name ?: "Не выбрано" }",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text("Выбрать", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showContainerEditDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showContainerEditDialog = false }) {
                        Text("Сохранить")
                    }
                }
            )
        }

        // Dialog: Add Place
        if (showPlaceDialog) {
            AlertDialog(
                onDismissRequest = { showPlaceDialog = false },
                title = { Text("Новое место") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Широта") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Долгота") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaceDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaceDialog = false }) {
                        Text("Создать")
                    }
                }
            )
        }

        // Dialog: Edit Place
        if (showPlaceEditDialog) {
            AlertDialog(
                onDismissRequest = { showPlaceEditDialog = false },
                title = { Text("Редактировать место") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Название") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Широта") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Долгота") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = "",
                                onValueChange = { /* TODO: capture value */ },
                                label = { Text("Заметки") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaceEditDialog = false }) {
                        Text("Отмена")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaceEditDialog = false }) {
                        Text("Сохранить")
                    }
                }
            )
        }

        // Dialog: Server sync
        if (showServerDialog) {
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Синхронизация") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Синхронизировать данные с сервером?")
                        Text(
                            "Статус: ${syncState.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (syncState.lastError != null) {
                            Text(
                                "Ошибка: ${syncState.lastError}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Red)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            syncState = repo.sync()
                            showServerDialog = false
                        }
                    }) {
                        Text("Синхронизировать")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showServerDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Dialog: Settings
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Настройки") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Темная тема: ${if (settings.theme == "DARK") "Включена" else "Отключена"}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Версия: 1.0", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("ОК")
                    }
                }
            )
        }
    }
}

@Composable
private fun AppContent(
    repo: Repository,
    settings: Settings,
    onDeleteShelf: (Long) -> Unit,
    onDeletePolka: (Long) -> Unit,
    onDeleteContainer: (Long) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onNavigateToServer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenShelfEdit: (Long) -> Unit,
    onOpenPolkaEdit: (Long) -> Unit,
    onOpenContainerEdit: (Long) -> Unit,
    onOpenPlaceEdit: (Long) -> Unit,
    onOpenItemEdit: (Long) -> Unit,
    syncState: SyncState,
    onSync: () -> Unit
) {
    val places by repo.places.collectAsState()
    val shelves by repo.shelves.collectAsState()
    val containers by repo.containers.collectAsState()
    val items by repo.items.collectAsState()
    val polki by repo.polki.collectAsState()

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
                0 -> MainScreen(
                    repo = repo,
                    settings = settings,
                    onOpenItem = { onOpenItemEdit(it) },
                    onAddItem = { showItemDialog = true },
                    onEditShelf = { /* placeholder */ },
                    onDeleteShelf = { /* placeholder */ },
                    onAddShelf = { showShelfDialog = true },
                    onEditPolka = { /* placeholder */ },
                    onDeletePolka = { /* placeholder */ },
                    onAddPolka = { showPolkaDialog = true },
                    onEditContainer = { /* placeholder */ },
                    onDeleteContainer = { /* placeholder */ },
                    onAddContainer = { showContainerDialog = true },
                    onEditPlace = { /* placeholder */ },
                    onDeletePlace = { /* placeholder */ },
                    onAddPlace = { showPlaceDialog = true },
                    onNavigateToSettings = { onNavigateToSettings },
                    onNavigateToServer = { onNavigateToServer }
                )
                1 -> MainScreen(
                    repo = repo,
                    settings = settings,
                    onOpenItem = { /* placeholder */ },
                    onAddItem = { /* placeholder */ },
                    onEditShelf = { onOpenShelfEdit(it) },
                    onDeleteShelf = { onDeleteShelf(it) },
                    onAddShelf = { showShelfDialog = true },
                    onEditPolka = { /* placeholder */ },
                    onDeletePolka = { /* placeholder */ },
                    onAddPolka = { showPolkaDialog = true },
                    onEditContainer = { /* placeholder */ },
                    onDeleteContainer = { /* placeholder */ },
                    onAddContainer = { /* placeholder */ },
                    onEditPlace = { /* placeholder */ },
                    onDeletePlace = { /* placeholder */ },
                    onAddPlace = { /* placeholder */ },
                    onNavigateToSettings = { onNavigateToSettings },
                    onNavigateToServer = { onNavigateToServer }
                )
                2 -> MainScreen(
                    repo = repo,
                    settings = settings,
                    onOpenItem = { /* placeholder */ },
                    onAddItem = { /* placeholder */ },
                    onEditShelf = { /* placeholder */ },
                    onDeleteShelf = { /* placeholder */ },
                    onAddShelf = { /* placeholder */ },
                    onEditPolka = { onOpenPolkaEdit(it) },
                    onDeletePolka = { onDeletePolka(it) },
                    onAddPolka = { showPolkaDialog = true },
                    onEditContainer = { /* placeholder */ },
                    onDeleteContainer = { /* placeholder */ },
                    onAddContainer = { /* placeholder */ },
                    onEditPlace = { /* placeholder */ },
                    onDeletePlace = { /* placeholder */ },
                    onAddPlace = { /* placeholder */ },
                    onNavigateToSettings = { onNavigateToSettings },
                    onNavigateToServer = { onNavigateToServer }
                )
                3 -> MainScreen(
                    repo = repo,
                    settings = settings,
                    onOpenItem = { /* placeholder */ },
                    onAddItem = { /* placeholder */ },
                    onEditShelf = { /* placeholder */ },
                    onDeleteShelf = { /* placeholder */ },
                    onAddShelf = { /* placeholder */ },
                    onEditPolka = { /* placeholder */ },
                    onDeletePolka = { /* placeholder */ },
                    onAddPolka = { /* placeholder */ },
                    onEditContainer = { onOpenContainerEdit(it) },
                    onDeleteContainer = { onDeleteContainer(it) },
                    onAddContainer = { showContainerDialog = true },
                    onEditPlace = { /* placeholder */ },
                    onDeletePlace = { /* placeholder */ },
                    onAddPlace = { /* placeholder */ },
                    onNavigateToSettings = { onNavigateToSettings },
                    onNavigateToServer = { onNavigateToServer }
                )
                4 -> MainScreen(
                    repo = repo,
                    settings = settings,
                    onOpenItem = { /* placeholder */ },
                    onAddItem = { /* placeholder */ },
                    onEditShelf = { /* placeholder */ },
                    onDeleteShelf = { /* placeholder */ },
                    onAddShelf = { /* placeholder */ },
                    onEditPolka = { /* placeholder */ },
                    onDeletePolka = { /* placeholder */ },
                    onAddPolka = { /* placeholder */ },
                    onEditContainer = { /* placeholder */ },
                    onDeleteContainer = { /* placeholder */ },
                    onAddContainer = { /* placeholder */ },
                    onEditPlace = { onOpenPlaceEdit(it) },
                    onDeletePlace = { onDeletePlace(it) },
                    onAddPlace = { showPlaceDialog = true },
                    onNavigateToSettings = { onNavigateToSettings },
                    onNavigateToServer = { onNavigateToServer }
                )
                5 -> {
                    SyncScreen(
                        syncState = syncState,
                        onSync = onSync
                    )
                }
                6 -> {
                    SettingsScreen(
                        settings = settings,
                        onBack = { selectedTab = 6 }
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalRow(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab,
                    style = if (index == selectedTab) {
                        MaterialTheme.typography.titleMedium.copy(color = Color.Black)
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == selectedTab) Color.White else Color.Transparent,
                            shape = RectangleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun SyncScreen(
    syncState: SyncState,
    onSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Синхронизация",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Статус: ${syncState.status}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (syncState.lastSyncAt > 0) {
                        Text(
                            "Последняя синхронизация: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(java.util.Date(syncState.lastSyncAt))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (syncState.lastError != null) {
                        Text(
                            "Ошибка: ${syncState.lastError}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Red)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onSync) {
                        Text("Синхронизировать")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: Settings,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Настройки",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Темная тема: ${if (settings.theme == "DARK") "Включена" else "Отключена"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Версия: 1.0",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack) {
                        Text("Назад")
                    }
                }
            }
        }
    }
}
