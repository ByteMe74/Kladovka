package ru.kladovka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import ru.kladovka.data.*

@Composable
fun KladovkaDesktopApp() {
    var serverUrl by remember { mutableStateOf("https://kladovka.dr6ter.ru") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var isLoginDialogOpen by remember { mutableStateOf(false) }
    var isRegisterDialogOpen by remember { mutableStateOf(false) }
    var isProfileDialogOpen by remember { mutableStateOf(false) }
    var isSyncDialogOpen by remember { mutableStateOf(false) }
    var exportDialogOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    
    val database = remember { Database }
    val repository = remember { Repository(database }
    
    val items by repository.items.collectAsState()
    val shelves by repository.shelves.collectAsState()
    val containers by repository.containers.collectAsState()
    val places by repository.places.collectAsState()
    
    // Фильтрация по поиску
    val filteredItems = if (searchQuery.isEmpty()) {
        items
    } else {
        items.filter { it.name.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }
    }
    
    // Главная панель
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кладовка", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Синхронизация и профиль
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { isSyncDialogOpen = true }) {
                        Text("Синхронизация")
                    }
                    
                    OutlinedButton(onClick = { isProfileDialogOpen = true }) {
                        Text("Профиль")
                    }
                }
                
                Divider()
                
                // Навигация
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TabItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = "📦", label = "Вещи")
                    TabItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = "📚", label = "Контейнеры")
                    TabItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = "🗄️", label = "Стеллажи")
                    TabItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = "📍", label = "Места")
                }
                
                Divider()
                
                // Поиск
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Контент по вкладкам
                when (selectedTab) {
                    0 -> ItemsScreen(filteredItems, database, repository)
                    1 -> ContainersScreen(containers, database, repository)
                    2 -> ShelvesScreen(shelves, database, repository)
                    3 -> PlacesScreen(places, database, repository)
                }
            }
        }
    }
}

@Composable
fun TabItem(selected: Boolean, onClick: () -> Unit, icon: String, label: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White)
            .clip(RoundedCornerShape(8.dp))
            .onClick { onClick() }
    ) {
        Text(
            text = "$icon $label",
            modifier = Modifier.padding(8.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ItemsScreen(items: List<Item>, database: Database, repository: Repository) {
    LazyColumn {
        items(items) { item ->
            ItemCard(item, database, repository)
        }
    }
}

@Composable
fun ItemCard(item: Item, database: Database, repository: Repository) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "${item.quantity} ${item.unit}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = item.category.ifEmpty { "Без категории" },
                fontSize = 12.sp,
                color = Color.Gray
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder()
                        .name("star")
                        .build(),
                    contentDescription = null,
                    tint = if (item.pinned) Color.Yellow else Color.Gray
                )
                Text(if (item.pinned) "Закреплён" else "Не закреплен")
            }
        }
    }
}

@Composable
fun ContainersScreen(containers: List<Container>, database: Database, repository: Repository) {
    LazyColumn {
        items(containers) { container ->
            ContainerCard(container, database, repository)
        }
    }
}

@Composable
fun ContainerCard(container: Container, database: Database, repository: Repository) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = container.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShelvesScreen(shelves: List<Shelf>, database: Database, repository: Repository) {
    LazyColumn {
        items(shelves) { shelf ->
            ShelfCard(shelf, database, repository)
        }
    }
}

@Composable
fun ShelfCard(shelf: Shelf, database: Database, repository: Repository) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = shelf.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlacesScreen(places: List<Place>, database: Database, repository: Repository) {
    LazyColumn {
        items(places) { place ->
            PlaceCard(place, database, repository)
        }
    }
}

@Composable
fun PlaceCard(place: Place, database: Database, repository: Repository) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = place.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Диалог синхронизации
@Composable
fun SyncDialog(token: String?, onDismiss: () -> Unit) {
    if (!isSyncDialogOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Синхронизация") },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("URL сервера") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    passwordVisuals = true
                )
                Button(onClick = { /* login logic */ }) {
                    Text("Войти")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// Диалог профиля
@Composable
fun ProfileDialog(token: String?, onDismiss: () -> Unit) {
    if (!isProfileDialogOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        text = {
            Column {
                Text("Username: ${username ?: "Не введён"}")
                Text("Email: Пример@example.com")
                Text("Статус: Подтверждён")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// Диалог экспорта/импорта
@Composable
fun ExportImportDialog(token: String?, onDismiss: () -> Unit) {
    if (!exportDialogOpen && !importDialogOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бэкап/Восстановление") },
        text = {
            Column {
                Button(onClick = { /* export logic */ }) {
                    Text("Экспорт в JSON")
                }
                Button(onClick = { /* import logic */ }) {
                    Text("Импорт из JSON")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@OptIn(ExperimentalWindowApi::class)
fun main() {
    applicationWindow(
        onClose = ::exitApplication,
        title = "Кладовка Desktop",
        resizable = true
    ) {
        KladovkaDesktopApp()
    }
}
