package ru.kladovka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: AppViewModel,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onEditShelf: (Long) -> Unit,
    onAddShelf: () -> Unit,
    onEditPolka: (Long) -> Unit,
    onAddPolka: () -> Unit,
    onEditContainer: (Long) -> Unit,
    onAddContainer: () -> Unit,
    onEditPlace: (Long) -> Unit,
    onAddPlace: () -> Unit
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val tabIndex by vm.selectedTab.collectAsStateWithLifecycle()
    val listAnimated by vm.listAnimated.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val syncBusy = vm.syncBusy.collectAsStateWithLifecycle().value
    val synced = vm.synced.collectAsStateWithLifecycle().value
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    // Бэкап: экспорт в файл / импорт из файла (замена всех данных)
    var showBackupMenu by rememberSaveable { mutableStateOf(false) }
    var showImportConfirm by rememberSaveable { mutableStateOf(false) }
    var showSyncDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val dateStamp = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportTo(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Бэкап сохранён" else "Не удалось записать бэкап",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importFrom(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Данные восстановлены" else "Ошибка: файл не похож на бэкап Кладовки",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    // Экспорт в CSV (все вещи, для Excel/таблиц)
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            vm.exportCsvTo(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "CSV сохранён (вещи открываются в Excel)" else "Не удалось записать CSV",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Анимация появления списков — только один раз за запуск приложения,
    // чтобы возврат с экрана редактирования не проигрывал каскад заново.
    LaunchedEffect(Unit) {
        delay(800)
        vm.markListAnimated()
    }

    // Отметка «новое»: фиксируем время последнего открытия приложения
    // до первого рендера списков (см. NewSeen).
    NewSeen.init(context)

    // При сворачивании приложения мгновенно отправляем изменения на сервер
    // (если автосинхронизация включена).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.onAppBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Шапка на сплошном фоне — заголовок всегда контрастный
                CenterAlignedTopAppBar(
                    title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Inventory2,
                            null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Кладовка", fontWeight = FontWeight.Bold)
                    }
                },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // Переключатель темы: как в системе / светлая / тёмная
                        IconButton(onClick = { showThemeDialog = true }) {
                            Icon(
                                when (themeMode) {
                                    ThemeMode.SYSTEM -> Icons.Filled.Contrast
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                },
                                "Выбор темы"
                            )
                        }
                        // Статус синхронизации: облако с зелёной точкой = сервер связан,
                        // вращающийся индикатор = идёт синхронизация. Клик открывает диалог.
                        Box {
                            IconButton(onClick = { showSyncDialog = true }) {
                                Box {
                                    Icon(
                                        Icons.Filled.Cloud,
                                        "Синхронизация с сервером",
                                        tint = if (synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (syncBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (synced) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(9.dp)
                                                .background(Color(0xFF3DDC84), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                        // Ещё: бэкап (экспорт/импорт данных)
                        Box {
                            IconButton(onClick = { showBackupMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "Ещё")
                            }
                            DropdownMenu(
                                expanded = showBackupMenu,
                                onDismissRequest = { showBackupMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Экспорт (бэкап)") },
                                    leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                                    onClick = {
                                        showBackupMenu = false
                                        exportLauncher.launch("kladovka-backup-$dateStamp.json")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Импорт (восстановить)") },
                                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                                    onClick = {
                                        showBackupMenu = false
                                        showImportConfirm = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт CSV (для Excel)") },
                                    leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                                    onClick = {
                                        showBackupMenu = false
                                        csvLauncher.launch("kladovka-vechi-$dateStamp.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Синхронизация с сервером") },
                                    leadingIcon = { Icon(Icons.Filled.Sync, null) },
                                    onClick = {
                                        showBackupMenu = false
                                        showSyncDialog = true
                                    }
                                )
                            }
                        }
                    }
                )
            },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tabIndex == 0,
                    onClick = { vm.selectTab(0) },
                    icon = {
                        BadgedBox(badge = { if (data.places.isNotEmpty()) Badge { Text("${data.places.size}") } }) {
                            Icon(Icons.Filled.Place, null)
                        }
                    },
                    label = { Text("Места") }
                )
                NavigationBarItem(
                    selected = tabIndex == 1,
                    onClick = { vm.selectTab(1) },
                    icon = {
                        BadgedBox(badge = { if (data.shelves.isNotEmpty()) Badge { Text("${data.shelves.size}") } }) {
                            Icon(Icons.Filled.Layers, null)
                        }
                    },
                    label = { Text("Стеллажи") }
                )
                NavigationBarItem(
                    selected = tabIndex == 2,
                    onClick = { vm.selectTab(2) },
                    icon = {
                        BadgedBox(badge = { if (data.polki.isNotEmpty()) Badge { Text("${data.polki.size}") } }) {
                            Icon(Icons.Filled.Layers, null)
                        }
                    },
                    label = { Text("Полки") }
                )
                NavigationBarItem(
                    selected = tabIndex == 3,
                    onClick = { vm.selectTab(3) },
                    icon = {
                        BadgedBox(badge = { if (data.containers.isNotEmpty()) Badge { Text("${data.containers.size}") } }) {
                            Icon(Icons.Filled.Archive, null)
                        }
                    },
                    label = { Text("Контейнеры") }
                )
                NavigationBarItem(
                    selected = tabIndex == 4,
                    onClick = { vm.selectTab(4) },
                    icon = {
                        BadgedBox(badge = { if (data.items.isNotEmpty()) Badge { Text("${data.items.size}") } }) {
                            Icon(Icons.Filled.Inventory2, null)
                        }
                    },
                    label = { Text("Вещи") }
                )
            }
        },
        floatingActionButton = {
            when (tabIndex) {
                0 -> ExtendedFloatingActionButton(
                    onClick = onAddPlace,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Место") }
                )
                1 -> ExtendedFloatingActionButton(
                    onClick = onAddShelf,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Стеллаж") }
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = onAddPolka,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Полка") }
                )
                3 -> ExtendedFloatingActionButton(
                    onClick = onAddContainer,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Контейнер") }
                )
                else -> ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Вещь") }
                )
            }
        }
    ) { padding ->
            // На широких экранах (планшеты) контент центрируется и не растягивается на всю ширину
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxWidth().widthIn(max = 620.dp).fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        // Общий поиск по всем сущностям: вещи, места, стеллажи, полки, контейнеры
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.setQuery(it) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Поиск: вещи, места, стеллажи…") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (query.isNotBlank()) IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Filled.Clear, null)
                                }
                            },
                            singleLine = true
                        )
                        if (query.isBlank()) {
                            // Листаемые вкладки: свайп влево/вправо переключает, нижняя панель синхронизирована
                            val pagerState = rememberPagerState(pageCount = { 5 })

                            // Нажатие вкладки внизу → плавно прокручиваем пейджер (быстро, чтобы не казалось вялым)
                            LaunchedEffect(tabIndex) {
                                if (pagerState.currentPage != tabIndex) {
                                    pagerState.animateScrollToPage(tabIndex, animationSpec = tween(220))
                                }
                            }
                            // Свайп по контенту → обновляем активную вкладку
                            LaunchedEffect(pagerState.settledPage) {
                                if (pagerState.settledPage != tabIndex) {
                                    vm.selectTab(pagerState.settledPage)
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                // Соседнюю вкладку не держим в композиции — меньше нагрузка в покое
                                beyondViewportPageCount = 0,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (page) {
                                    0 -> PlacesTab(data, onEditPlace, !listAnimated)
                                    1 -> ShelvesTab(data, onOpenItem, onEditShelf, onEditContainer, !listAnimated)
                                    2 -> PolkiTab(data, onEditPolka, !listAnimated)
                                    3 -> ContainersTab(data, onOpenItem, onEditContainer, !listAnimated)
                                    else -> ItemsTab(
                                        searchResults, data,
                                        onOpenItem,
                                        { id, delta -> vm.changeItemQuantity(id, delta) },
                                        { id, pinned -> vm.setItemPinned(id, pinned) },
                                        !listAnimated
                                    )
                                }
                            }
                        } else {
                            // Глобальный поиск: результаты по всем сущностям сразу
                            GlobalSearchResults(
                                data = data,
                                query = query,
                                onOpenItem = onOpenItem,
                                onEditShelf = onEditShelf,
                                onEditPolka = onEditPolka,
                                onEditContainer = onEditContainer,
                                onEditPlace = onEditPlace
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Восстановить из бэкапа?") },
            text = { Text("Все текущие места, стеллажи, полки, контейнеры и вещи будут заменены данными из файла. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("Продолжить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("Отмена") } }
        )
    }

    if (showSyncDialog) {
        SyncDialog(vm = vm, onDismiss = { showSyncDialog = false })
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "Как в системе",
                        ThemeMode.LIGHT to "Светлая",
                        ThemeMode.DARK to "Тёмная"
                    ).forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    vm.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Готово") } }
        )
    }
}

/* ========================= Вещи ========================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemsTab(
    items: List<Item>,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onChangeQuantity: (Long, Int) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    animateOnEntry: Boolean
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Категории-фильтры: чипы из всех имеющихся категорий, можно выбрать несколько.
        // «❗ Последние» — показать только вещи, которых осталась одна штука.
        var selectedCategories by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var onlyLast by rememberSaveable { mutableStateOf(false) }
        // Сортировка списка: по имени / количеству / категории
        var sortMode by rememberSaveable { mutableStateOf("name") }
        var sortOpen by remember { mutableStateOf(false) }
        val categories = remember(data.items) {
            data.items.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        }
        val itemCounts = remember(data.items) {
            data.items.filter { it.category.isNotBlank() }
                .groupBy { it.category }
                .mapValues { it.value.size }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        ) {
            FilterChip(
                selected = onlyLast,
                onClick = { onlyLast = !onlyLast },
                label = { Text("❗ Последние", style = MaterialTheme.typography.labelMedium) }
            )
            categories.forEach { c ->
                FilterChip(
                    selected = c in selectedCategories,
                    onClick = {
                        selectedCategories =
                            if (c in selectedCategories) selectedCategories - c else selectedCategories + c
                    },
                    label = { Text("$c (${itemCounts[c] ?: 0})", style = MaterialTheme.typography.labelMedium) }
                )
            }
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.Filled.Sort, "Сортировка", Modifier.size(20.dp))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    listOf(
                        "name" to "По имени",
                        "qty" to "По количеству",
                        "cat" to "По категории",
                        "date" to "Сначала изменённые"
                    ).forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(if (k == sortMode) "✓ $label" else label) },
                            onClick = { sortMode = k; sortOpen = false }
                        )
                    }
                }
            }
        }
        val visibleItems = remember(items, selectedCategories, onlyLast, sortMode) {
            var list = if (selectedCategories.isEmpty()) items
            else items.filter { it.category in selectedCategories }
            if (onlyLast) list = list.filter { it.quantity <= 1 }
            when (sortMode) {
                "qty" -> list.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.quantity }.thenBy { it.name.lowercase() })
                "cat" -> list.sortedWith(compareByDescending<Item> { it.pinned }.thenBy { it.category.lowercase() }.thenBy { it.name.lowercase() })
                "date" -> list.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.updatedAt }.thenBy { it.name.lowercase() })
                else -> list.sortedWith(compareByDescending<Item> { it.pinned }.thenBy { it.name.lowercase() })
            }
        }

        // Итог по видимому списку: позиций и общее количество штук
        if (visibleItems.isNotEmpty()) {
            Text(
                "Итого: ${visibleItems.size} позиций · ${visibleItems.sumOf { it.quantity }} шт",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        if (visibleItems.isEmpty()) {
            EmptyState(
                Icons.Filled.Inventory2,
                if (items.isEmpty()) "Пока нет вещей.\nНажмите «+ Вещь», чтобы добавить."
                else "Ничего не найдено"
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
            ) {
                items(visibleItems, key = { it.id }) { item ->
                    AnimatedItem(animateOnEntry) {
                        ItemCard(item, data, { onOpenItem(item.id) }, onChangeQuantity, onTogglePin)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    data: AppData,
    onClick: () -> Unit,
    onChangeQuantity: (Long, Int) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.photoPath != null) {
                AsyncImage(
                    model = photoModel(item.photoPath),
                    contentDescription = item.name,
                    modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (NewSeen.isNew(item.createdAt)) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "новое",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (item.category.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (item.quantity <= 1) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "❗ Последний!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    Text(data.locationOf(item), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // ⭐ Закрепить / открепить вещь (в начало списка)
            IconButton(onClick = { onTogglePin(item.id, !item.pinned) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (item.pinned) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = if (item.pinned) "Открепить" else "Закрепить",
                    tint = if (item.pinned) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Быстрое изменение количества без открытия редактора
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onChangeQuantity(item.id, 1) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Filled.Add, "Увеличить количество", Modifier.size(18.dp))
                }
                Text(
                    qtyText(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                IconButton(
                    onClick = { onChangeQuantity(item.id, -1) },
                    enabled = item.quantity > 1,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Filled.Remove, "Уменьшить количество", Modifier.size(18.dp))
                }
            }
        }
    }
}

/* ========================= Контейнеры ========================= */

@Composable
private fun ContainersTab(
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditContainer: (Long) -> Unit,
    animateOnEntry: Boolean
) {
    if (data.containers.isEmpty()) {
        EmptyState(
            Icons.Filled.Archive,
            "Контейнеров пока нет.\nНажмите «+ Контейнер», чтобы добавить."
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(data.containers, key = { it.id }) { container ->
            AnimatedItem(animateOnEntry) {
                ContainerCard(
                    cu = ContainerUi(container, data.items.filter { it.containerId == container.id }),
                    data = data,
                    onOpenItem = onOpenItem,
                    onEditContainer = onEditContainer
                )
            }
        }
    }
}

@Composable
private fun ContainerCard(
    cu: ContainerUi,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditContainer: (Long) -> Unit
) {
    var expanded by rememberSaveable(cu.container.id) { mutableStateOf(true) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(cu.container.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    val shelf = cu.container.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid } }
                    val shelfPlace = data.placeName(shelf?.placeId)
                    val sub = when {
                        shelf != null && shelfPlace != null -> "Стеллаж ${shelf.name} · $shelfPlace"
                        shelf != null -> "Стеллаж ${shelf.name}"
                        else -> data.placeName(cu.container.placeId)?.let { "Место: $it" } ?: "Без стеллажа"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(3.dp))
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("${cu.items.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onEditContainer(cu.container.id) }) { Icon(Icons.Filled.Edit, "Изменить") }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AnimatedVisibility(visible = expanded) {
                if (cu.items.isEmpty()) {
                    Text("Пусто", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    Column { cu.items.forEach { ItemRow(it, data, onOpenItem, indent = true) } }
                }
            }
        }
    }
}

/* ========================= Стеллажи ========================= */

@Composable
private fun ShelvesTab(
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditShelf: (Long) -> Unit,
    onEditContainer: (Long) -> Unit,
    animateOnEntry: Boolean
) {
    val places = remember(data) { buildPlaces(data) }

    if (data.items.isEmpty() && data.shelves.isEmpty() && data.containers.isEmpty()) {
        EmptyState(
            Icons.Filled.Layers,
            "Здесь будут стеллажи и контейнеры.\nНажмите «+», чтобы добавить."
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (places.unplaced.isNotEmpty()) {
            item(key = "unplaced") { SectionLabel("Без места", places.unplaced.size) }
            items(places.unplaced, key = { "u-${it.id}" }) { item ->
                AnimatedItem(animateOnEntry) { ItemRow(item, data, onOpenItem) }
            }
        }
        places.shelves.forEach { shelfUi ->
            item(key = "s-${shelfUi.shelf.id}") {
                AnimatedItem(animateOnEntry) {
                    ShelfCard(shelfUi, data, onOpenItem, onEditShelf, onEditContainer)
                }
            }
        }
        if (places.looseContainers.isNotEmpty()) {
            item(key = "loose") { SectionLabel("Контейнеры без стеллажа", places.looseContainers.size) }
            places.looseContainers.forEach { cu ->
                item(key = "lc-${cu.container.id}") {
                    AnimatedItem(animateOnEntry) {
                        StandaloneContainerCard(cu, data, onOpenItem, onEditContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Text(
        "$text — $count".uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

/** Пустое состояние вкладки: крупная иконка + подсказка по центру. */
@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Плавное появление карточки в списке. Прогоняется один раз за запуск приложения. */
@Composable
private fun AnimatedItem(animate: Boolean, content: @Composable () -> Unit) {
    if (!animate) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280, delayMillis = 40)) +
            slideInVertically(animationSpec = tween(340), initialOffsetY = { it / 4 })
    ) {
        content()
    }
}

@Composable
private fun ShelfCard(
    shelfUi: ShelfUi,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditShelf: (Long) -> Unit,
    onEditContainer: (Long) -> Unit
) {
    val totalItems = shelfUi.direct.size + shelfUi.containers.sumOf { it.items.size }
    val placeName = data.placeName(shelfUi.shelf.placeId)
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), tonalElevation = 1.dp, shadowElevation = 0.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shelfUi.shelf.name, style = MaterialTheme.typography.titleMedium)
                    Text("Вещей: $totalItems", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onEditShelf(shelfUi.shelf.id) }) { Icon(Icons.Filled.Edit, "Изменить") }
            }
            if (placeName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Место: $placeName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shelfUi.shelf.notes.isNotBlank()) {
                Text(shelfUi.shelf.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            HorizontalDivider()
            shelfUi.direct.forEach { item -> ItemRow(item, data, onOpenItem, indent = true) }
            shelfUi.containers.forEach { cu -> ContainerSection(cu, data, onOpenItem, onEditContainer) }
            if (shelfUi.direct.isEmpty() && shelfUi.containers.isEmpty()) {
                Text("Пустой стеллаж", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ContainerSection(
    cu: ContainerUi,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditContainer: (Long) -> Unit
) {
    var expanded by rememberSaveable(cu.container.id) { mutableStateOf(true) }
    val placeName = data.placeName(cu.container.placeId)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                if (placeName != null) "${cu.container.name} · $placeName" else cu.container.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${cu.items.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { onEditContainer(cu.container.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Edit, "Изменить", Modifier.size(16.dp)) }
        }
        AnimatedVisibility(visible = expanded && cu.items.isNotEmpty()) {
            Column { cu.items.forEach { ItemRow(it, data, onOpenItem, indent = true) } }
        }
    }
}

@Composable
private fun StandaloneContainerCard(
    cu: ContainerUi,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    onEditContainer: (Long) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), tonalElevation = 1.dp, shadowElevation = 0.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cu.container.name, style = MaterialTheme.typography.titleSmall)
                    val placeName = data.placeName(cu.container.placeId)
                    Text(
                        if (placeName != null) "Место: $placeName" else "Без стеллажа",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${cu.items.size} вещей", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onEditContainer(cu.container.id) }) { Icon(Icons.Filled.Edit, "Изменить") }
            }
            cu.items.forEach { ItemRow(it, data, onOpenItem, indent = true) }
        }
    }
}

/* ========================= Полки (polki) ========================= */

@Composable
private fun PolkiTab(
    data: AppData,
    onEditPolka: (Long) -> Unit,
    animateOnEntry: Boolean
) {
    if (data.polki.isEmpty()) {
        EmptyState(
            Icons.Filled.Class,
            "Полок пока нет.\nНажмите «+ Полка», чтобы добавить.\nПолку можно привязать к стеллажу."
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(data.polki, key = { it.id }) { polka ->
            AnimatedItem(animateOnEntry) {
                PolkaCard(
                    PolkaUi(polka, polka.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid } }),
                    data,
                    onEditPolka
                )
            }
        }
    }
}

@Composable
private fun PolkaCard(
    pu: PolkaUi,
    data: AppData,
    onEditPolka: (Long) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pu.polka.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val shelfName = pu.onShelf?.name
                val placeName = pu.polka.placeId?.let { pid -> data.placeName(pid) }
                val sub = when {
                    shelfName != null && placeName != null -> "Стеллаж ${shelfName} · Место: $placeName"
                    shelfName != null -> "Стеллаж: $shelfName"
                    placeName != null -> "Место: $placeName"
                    else -> "Без стеллажа"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pu.polka.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(pu.polka.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { onEditPolka(pu.polka.id) }) { Icon(Icons.Filled.Edit, "Изменить") }
        }
    }
}

/* ========================= Места ========================= */

@Composable
private fun PlacesTab(
    data: AppData,
    onEditPlace: (Long) -> Unit,
    animateOnEntry: Boolean
) {
    if (data.places.isEmpty()) {
        EmptyState(
            Icons.Filled.Place,
            "Мест пока нет.\nНажмите «+ Место», чтобы добавить (Кладовая, Балкон, Гараж…)."
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(data.places, key = { _, p -> p.id }) { index, place ->
            AnimatedItem(animateOnEntry) {
                PlaceCard(place, data, onEditPlace, index)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceCard(place: Place, data: AppData, onEditPlace: (Long) -> Unit, index: Int = 0) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shelvesCount = data.shelves.count { it.placeId == place.id }
    val polkiCount = data.polki.count { it.placeId == place.id }
    val containersCount = data.containers.count { it.placeId == place.id }
    val itemsCount = data.items.count { it.placeId == place.id }

    val hasCoords = place.latitude != null && place.longitude != null
    // Обратное геокодирование: координаты → адрес (с кэшем, чтобы не дёргать сеть при каждой перерисовке)
    var address by remember(place.id, place.latitude, place.longitude) { mutableStateOf<String?>(null) }
    var addressLoaded by remember(place.id, place.latitude, place.longitude) { mutableStateOf(false) }
    LaunchedEffect(place.id, place.latitude, place.longitude) {
        if (hasCoords) {
            // Небольшая задержка по позиции в списке: геокодер не нагружается пачкой сразу
            delay(index * 120L)
            address = AddressCache.address(context, place.latitude!!, place.longitude!!)
            addressLoaded = true
        }
    }

    val locationText = when {
        address != null -> address
        !addressLoaded && hasCoords -> "Определение адреса…"
        else -> coordsText(place)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    // Короткое нажатие — открыть координаты в картах
                    onClick = { if (hasCoords) openInMapApp(context, place.latitude!!, place.longitude!!) },
                    // Долгое нажатие — скопировать координаты в буфер обмена
                    onLongClick = {
                        if (hasCoords) {
                            clipboard.setText(
                                AnnotatedString(
                                    "${place.latitude}, ${place.longitude}"
                                )
                            )
                            Toast.makeText(context, "Координаты скопированы", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    if (locationText != null) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(locationText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        "Стеллажей: $shelvesCount · Полок: $polkiCount · Контейнеров: $containersCount · Вещей: $itemsCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (place.notes.isNotBlank()) {
                        Text(place.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        sharePlace(context, place.name, locationText ?: place.notes, place.latitude, place.longitude)
                    }) { Icon(Icons.Filled.Share, "Поделиться") }
                    IconButton(onClick = { onEditPlace(place.id) }) { Icon(Icons.Filled.Edit, "Изменить") }
                }
            }
        }
    }
}

/* ========================= Общая строка вещи ========================= */

@Composable
private fun ItemRow(
    item: Item,
    data: AppData,
    onOpenItem: (Long) -> Unit,
    indent: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 16.dp) else Modifier)
            .clickable { onOpenItem(item.id) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.photoPath != null) {
            AsyncImage(
                model = photoModel(item.photoPath),
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
        }
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (NewSeen.isNew(item.createdAt)) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "новое",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(qtyText(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ========================= Глобальный поиск ========================= */

/**
 * Результаты общего поиска: все сущности, в имени которых встречается запрос.
 * Клик по строке открывает соответствующую запись в редакторе.
 */
@Composable
private fun GlobalSearchResults(
    data: AppData,
    query: String,
    onOpenItem: (Long) -> Unit,
    onEditShelf: (Long) -> Unit,
    onEditPolka: (Long) -> Unit,
    onEditContainer: (Long) -> Unit,
    onEditPlace: (Long) -> Unit
) {
    val q = query.trim().lowercase()
    val placeMatches = data.places.filter { it.name.lowercase().contains(q) }
    val shelfMatches = data.shelves.filter { it.name.lowercase().contains(q) }
    val polkaMatches = data.polki.filter { it.name.lowercase().contains(q) }
    val containerMatches = data.containers.filter { it.name.lowercase().contains(q) }
    val itemMatches = data.items.filter {
        it.name.lowercase().contains(q) ||
            it.notes.lowercase().contains(q) ||
            it.category.lowercase().contains(q)
    }

    if (placeMatches.isEmpty() && shelfMatches.isEmpty() && polkaMatches.isEmpty() &&
        containerMatches.isEmpty() && itemMatches.isEmpty()
    ) {
        EmptyState(Icons.Filled.Search, "Ничего не найдено по запросу «${query.trim()}»")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (placeMatches.isNotEmpty()) {
            item(key = "h-places") { SectionLabel("Места", placeMatches.size) }
            items(placeMatches, key = { "p-${it.id}" }) { p ->
                SearchResultRow(
                    icon = Icons.Filled.Place,
                    title = p.name,
                    subtitle = p.notes.ifBlank { coordsText(p) ?: "" },
                    onClick = { onEditPlace(p.id) }
                )
            }
        }
        if (shelfMatches.isNotEmpty()) {
            item(key = "h-shelves") { SectionLabel("Стеллажи", shelfMatches.size) }
            items(shelfMatches, key = { "s-${it.id}" }) { s ->
                SearchResultRow(
                    icon = Icons.Filled.Layers,
                    title = s.name,
                    subtitle = data.placeName(s.placeId) ?: s.notes,
                    onClick = { onEditShelf(s.id) }
                )
            }
        }
        if (polkaMatches.isNotEmpty()) {
            item(key = "h-polki") { SectionLabel("Полки", polkaMatches.size) }
            items(polkaMatches, key = { "k-${it.id}" }) { k ->
                SearchResultRow(
                    icon = Icons.Filled.Class,
                    title = k.name,
                    subtitle = k.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid }?.name }
                        ?: data.placeName(k.placeId) ?: "",
                    onClick = { onEditPolka(k.id) }
                )
            }
        }
        if (containerMatches.isNotEmpty()) {
            item(key = "h-containers") { SectionLabel("Контейнеры", containerMatches.size) }
            items(containerMatches, key = { "c-${it.id}" }) { c ->
                SearchResultRow(
                    icon = Icons.Filled.Archive,
                    title = c.name,
                    subtitle = buildString {
                        c.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid }?.name }?.let(::append)
                        data.placeName(c.placeId)?.let { if (isNotEmpty()) append(" · "); append(it) }
                    },
                    onClick = { onEditContainer(c.id) }
                )
            }
        }
        if (itemMatches.isNotEmpty()) {
            item(key = "h-items") { SectionLabel("Вещи", itemMatches.size) }
            items(itemMatches, key = { "i-${it.id}" }) { i ->
                SearchResultRow(
                    icon = Icons.Filled.Inventory2,
                    title = i.name,
                    subtitle = if (i.notes.lowercase().contains(q) && !i.name.lowercase().contains(q)) i.notes
                    else "${qtyText(i)} · ${data.locationOf(i)}",
                    onClick = { onOpenItem(i.id) }
                )
            }
        }
    }
}

/** Строка результата глобального поиска: иконка, название, подпись. */
@Composable
private fun SearchResultRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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