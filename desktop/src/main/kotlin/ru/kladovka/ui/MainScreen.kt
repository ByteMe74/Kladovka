package ru.kladovka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.kladovka.data.AppData
import ru.kladovka.data.Container
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Polka
import ru.kladovka.data.Shelf
import ru.kladovka.data.SortMode
import ru.kladovka.data.SqliteDatabase
import ru.kladovka.data.sortedFor
import java.io.File

/** Вкладки — те же пять, что и на Android. */
enum class Tab(val title: String) {
    PLACES("Места"),
    SHELVES("Стеллажи"),
    POLKI("Полки"),
    CONTAINERS("Контейнеры"),
    ITEMS("Вещи");

    /** Сколько записей в разделе — для счётчика на иконке вкладки. */
    fun countIn(d: AppData): Int = when (this) {
        PLACES -> d.places.size
        SHELVES -> d.shelves.size
        POLKI -> d.polki.size
        CONTAINERS -> d.containers.size
        ITEMS -> d.items.size
    }
}

/** Что открыть в диалоге редактирования. */
private sealed interface Editor {
    data class PlaceEdit(val id: Long?) : Editor
    data class ShelfEdit(val id: Long?) : Editor
    data class PolkaEdit(val id: Long?) : Editor
    data class ContainerEdit(val id: Long?) : Editor
    data class ItemEdit(val id: Long?) : Editor
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    db: SqliteDatabase,
    data: AppData,
    photoDir: File,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Сервер ответил и обновление есть — показываем значок. */
    updateAvailable: Boolean = false,
    onOpenUpdate: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(Tab.ITEMS) }
    var query by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var sortOpen by remember { mutableStateOf(false) }
    var onlyLast by remember { mutableStateOf(false) }
    var catFilter by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Категории для чипов-фильтров берём из данных, а не из того, что уже отфильтровано,
    // иначе фильтр нельзя было бы расширить обратно.
    val allCategories = remember(data) {
        data.items.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val visibleItems = remember(data, query, sortMode, onlyLast, catFilter) {
        var list = data.items
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, true) || it.category.contains(query, true) ||
                    it.notes.contains(query, true) || data.locationOf(it).contains(query, true)
            }
        }
        if (onlyLast) list = list.filter { it.quantity <= 1 }
        if (catFilter.isNotEmpty()) list = list.filter { it.category.trim() in catFilter }
        list.sortedFor(sortMode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кладовка") },
                actions = {
                    IconButton(onClick = onOpenSync) {
                        Icon(Icons.Default.Sync, contentDescription = "Сервер и синхронизация")
                    }
                    // Значок обновления виден не всегда: показываем его, только
                    // когда сервер ответил и обновление действительно есть.
                    // Иначе кнопка была бы лишней, а её содержимое — «проверить
                    // не удалось», что человек не просил.
                    if (updateAvailable) {
                        IconButton(onClick = onOpenUpdate) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Доступно обновление"
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editor = when (tab) {
                        Tab.PLACES -> Editor.PlaceEdit(null)
                        Tab.SHELVES -> Editor.ShelfEdit(null)
                        Tab.POLKI -> Editor.PolkaEdit(null)
                        Tab.CONTAINERS -> Editor.ContainerEdit(null)
                        Tab.ITEMS -> Editor.ItemEdit(null)
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(when (tab) {
                    Tab.PLACES -> "Место"
                    Tab.SHELVES -> "Стеллаж"
                    Tab.POLKI -> "Полка"
                    Tab.CONTAINERS -> "Контейнер"
                    Tab.ITEMS -> "Вещь"
                }) }
            )
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            NavigationRail {
                Tab.entries.forEach { t ->
                    // Счётчик на иконке — как на Android: видно, сколько записей
                    // в разделе, не заходя в него.
                    val count = t.countIn(data)
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (count > 0) Badge { Text("$count") }
                                }
                            ) {
                                Icon(tabIcon(t), contentDescription = t.title)
                            }
                        },
                        label = { Text(t.title) }
                    )
                }
            }
            VerticalDivider()

            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                )

                // Фильтры и сортировка — только для вещей и только когда поиск
                // пуст: при вводе запроса место занимают результаты поиска.
                if (query.isBlank() && tab == Tab.ITEMS) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = onlyLast,
                                onClick = { onlyLast = !onlyLast },
                                label = { Text("❗ Последние") }
                            )
                        }
                        items(allCategories) { c ->
                            FilterChip(
                                selected = c in catFilter,
                                onClick = {
                                    catFilter = if (c in catFilter) catFilter - c else catFilter + c
                                },
                                label = { Text("$c (${data.items.count { it.category.trim() == c }})") }
                            )
                        }
                        item {
                            Box {
                                TextButton(onClick = { sortOpen = true }) {
                                    Text(if (sortMode == SortMode.NAME) "Сортировка" else sortMode.title)
                                }
                                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                    SortMode.entries.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(if (m == sortMode) "✓ ${m.title}" else m.title) },
                                            onClick = { sortMode = m; sortOpen = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    // Поиск перекрывает вкладки и ищет по всем сущностям сразу.
                    // Раньше поле было видно на всех вкладках, но работало только
                    // на «Вещах»: на остальных выглядело живым и молча ничего
                    // не делало. Android ищет по всем пяти разделам.
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        SearchResults(data = data, query = q, onEdit = { editor = it })
                    } else {
                        when (tab) {
                        Tab.ITEMS -> ItemsList(db, data, visibleItems, onEdit = { editor = Editor.ItemEdit(it) })
                        Tab.CONTAINERS -> ContainersList(
                            db, data,
                            onEdit = { editor = Editor.ContainerEdit(it) },
                            onOpenItem = { editor = Editor.ItemEdit(it) }
                        )
                        Tab.SHELVES -> ShelvesList(
                            db, data,
                            onEditShelf = { editor = Editor.ShelfEdit(it) },
                            onEditContainer = { editor = Editor.ContainerEdit(it) },
                            onOpenItem = { editor = Editor.ItemEdit(it) }
                        )
                        Tab.POLKI -> PolkiList(db, data, onEdit = { editor = Editor.PolkaEdit(it) })
                        Tab.PLACES -> PlacesList(
                            db, data,
                            onEditPlace = { editor = Editor.PlaceEdit(it) },
                            onEditShelf = { editor = Editor.ShelfEdit(it) },
                            onEditContainer = { editor = Editor.ContainerEdit(it) },
                            onOpenItem = { editor = Editor.ItemEdit(it) }
                        )
                        }
                    }
                }
            }
        }
    }

    when (val e = editor) {
        is Editor.PlaceEdit -> PlaceDialog(
            db = db,
            place = data.places.firstOrNull { it.id == e.id },
            onDismiss = { editor = null }
        )
        is Editor.ShelfEdit -> ShelfDialog(
            db = db, data = data,
            shelf = data.shelves.firstOrNull { it.id == e.id },
            onDismiss = { editor = null }
        )
        is Editor.PolkaEdit -> PolkaDialog(
            db = db, data = data,
            polka = data.polki.firstOrNull { it.id == e.id },
            onDismiss = { editor = null }
        )
        is Editor.ContainerEdit -> ContainerDialog(
            db = db, data = data,
            container = data.containers.firstOrNull { it.id == e.id },
            onDismiss = { editor = null }
        )
        is Editor.ItemEdit -> ItemDialog(
            db = db, data = data,
            item = data.items.firstOrNull { it.id == e.id },
            suggestions = db.categories(),
            photoDir = photoDir,
            onDismiss = { editor = null }
        )
        null -> Unit
    }
}

@Composable
private fun tabIcon(t: Tab) = when (t) {
    Tab.PLACES -> Icons.Filled.Place
    Tab.SHELVES -> Icons.Filled.Layers
    Tab.POLKI -> Icons.Filled.Layers
    Tab.CONTAINERS -> Icons.Filled.Archive
    Tab.ITEMS -> Icons.Filled.Inventory2
}

// ------------------------------------------------------------------ поиск

private class SearchRow(
    val key: String,
    val editor: Editor,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private class SearchGroup(val title: String, val rows: List<SearchRow>)

/**
 * Группировка находок по разделам.
 *
 * Ищем по всем сущностям сразу, как в Android: раньше десктоп фильтровал только
 * вещи, хотя поле поиска висело на каждой вкладке. Регистр игнорируем в Kotlin,
 * а не через SQL LIKE — SQLite сравнивает без учёта регистра только ASCII, и
 * «м6» не находил «М6».
 */
private fun searchGroups(data: AppData, query: String): List<SearchGroup> {
    val q = query.trim()
    fun hit(text: String) = text.contains(q, ignoreCase = true)

    val places = data.places.filter { hit(it.name) || hit(it.notes) }.map {
        SearchRow("place-${it.id}", Editor.PlaceEdit(it.id), Icons.Filled.Place, it.name,
            coordsText(it) ?: it.notes.ifBlank { "Место" })
    }
    val shelves = data.shelves.filter { hit(it.name) || hit(it.notes) }.map {
        SearchRow("shelf-${it.id}", Editor.ShelfEdit(it.id), Icons.Filled.Layers, it.name,
            data.placeName(it.placeId) ?: "Без места")
    }
    val polki = data.polki.filter { hit(it.name) || hit(it.notes) }.map {
        val onShelf = it.shelfId?.let { sid -> data.shelves.firstOrNull { s -> s.id == sid }?.name }
        SearchRow("polka-${it.id}", Editor.PolkaEdit(it.id), Icons.Filled.Layers, it.name,
            listOfNotNull(onShelf, data.placeName(it.placeId)).joinToString(" · ").ifEmpty { "Без привязки" })
    }
    val containers = data.containers.filter { hit(it.name) }.map {
        SearchRow("container-${it.id}", Editor.ContainerEdit(it.id), Icons.Filled.Archive, it.name,
            listOfNotNull(data.placeName(it.placeId)).joinToString(" · ").ifEmpty { "Без стеллажа" })
    }
    val items = data.items.filter {
        hit(it.name) || hit(it.category) || hit(it.notes) || hit(data.locationOf(it))
    }.map {
        SearchRow("item-${it.id}", Editor.ItemEdit(it.id), Icons.Filled.Inventory2, it.name,
            "${qtyText(it)} · ${data.locationOf(it)}")
    }

    return listOf(
        SearchGroup("Места", places),
        SearchGroup("Стеллажи", shelves),
        SearchGroup("Полки", polki),
        SearchGroup("Контейнеры", containers),
        SearchGroup("Вещи", items)
    ).filter { it.rows.isNotEmpty() }
}

@Composable
private fun SearchResults(
    data: AppData,
    query: String,
    onEdit: (Editor) -> Unit
) {
    val groups = remember(data, query) { searchGroups(data, query) }
    if (groups.isEmpty()) {
        EmptyState("Ничего не найдено по запросу «$query»")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.forEach { group ->
            item(key = "h-${group.title}") {
                Text(
                    "${group.title.uppercase()} — ${group.rows.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                )
            }
            items(group.rows, key = { it.key }) { row ->
                Card(onClick = { onEdit(row.editor) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            row.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(
                                row.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ списки

@Composable
private fun ItemsList(
    db: SqliteDatabase,
    data: AppData,
    items: List<Item>,
    onEdit: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState("Вещей пока нет. Нажмите «Вещь», чтобы добавить первую.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Итог по видимому списку — на Android он над списком, и при фильтрах
        // считает именно то, что видно, а не всю базу.
        item(key = "totals") {
            val pieces = items.size
            val units = items.sumOf { it.quantity }
            Text(
                "Итого: $pieces позиций · $units шт",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(items, key = { it.id }) { item ->
            ItemCard(db, data, item, onEdit)
        }
    }
}

@Composable
private fun ItemCard(db: SqliteDatabase, data: AppData, item: Item, onEdit: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${qtyText(item)} · ${data.locationOf(item)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.category.isNotBlank()) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            IconButton(onClick = { db.adjustQuantity(item.id, -1) }) {
                Icon(Icons.Default.Remove, contentDescription = "Убавить")
            }
            Text(qtyText(item), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { db.adjustQuantity(item.id, +1) }) {
                Icon(Icons.Default.Add, contentDescription = "Прибавить")
            }
            IconButton(onClick = { db.setItemPinned(item.id, !item.pinned) }) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = if (item.pinned) "Открепить" else "Закрепить",
                    tint = if (item.pinned) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { onEdit(item.id) }) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить")
            }
        }
    }
}

@Composable
private fun ContainersList(
    db: SqliteDatabase,
    data: AppData,
    onEdit: (Long) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    if (data.containers.isEmpty()) {
        EmptyState("Контейнеров пока нет.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.containers, key = { it.id }) { c ->
            val inside = data.items.filter { it.containerId == c.id }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            val where = listOfNotNull(
                                data.placeName(c.placeId),
                                c.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid }?.name }
                            ).joinToString(" · ")
                            Text(
                                where.ifBlank { "Без стеллажа" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${inside.size} шт", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { onEdit(c.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить")
                        }
                    }
                    inside.forEach { i ->
                        MiniItemRow(db, data, i, onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelvesList(
    db: SqliteDatabase,
    data: AppData,
    onEditShelf: (Long) -> Unit,
    onEditContainer: (Long) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    if (data.shelves.isEmpty()) {
        EmptyState("Стеллажей пока нет.")
        return
    }
    val view = buildPlaces(data)
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(view.shelves, key = { it.shelf.id }) { su ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(su.shelf.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                data.placeName(su.shelf.placeId) ?: "Без места",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditShelf(su.shelf.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить")
                        }
                    }
                    su.containers.forEach { cu ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📦 ${cu.container.name} (${cu.items.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(top = 4.dp)
                            )
                            IconButton(onClick = { onEditContainer(cu.container.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить контейнер")
                            }
                        }
                        cu.items.forEach { MiniItemRow(db, data, it, onOpenItem) }
                    }
                    su.direct.forEach { MiniItemRow(db, data, it, onOpenItem) }
                }
            }
        }
    }
}

@Composable
private fun PolkiList(db: SqliteDatabase, data: AppData, onEdit: (Long) -> Unit) {
    if (data.polki.isEmpty()) {
        EmptyState("Полок пока нет.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.polki, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                p.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid }?.name },
                                data.placeName(p.placeId)
                            ).joinToString(" · ").ifBlank { "Без привязки" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onEdit(p.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacesList(
    db: SqliteDatabase,
    data: AppData,
    onEditPlace: (Long) -> Unit,
    onEditShelf: (Long) -> Unit,
    onEditContainer: (Long) -> Unit,
    onOpenItem: (Long) -> Unit
) {
    if (data.places.isEmpty()) {
        EmptyState("Мест пока нет. Например: «Кладовая», «Балкон», «Гараж».")
        return
    }
    val view = buildPlaces(data)
    LazyColumn(
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.places, key = { it.id }) { place ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            coordsText(place)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onEditPlace(place.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить")
                        }
                    }
                    // Сводка по месту — как на Android: сколько внутри стеллажей,
                    // полок, контейнеров и вещей. Считаем от того, что реально
                    // показываем в дереве ниже, чтобы цифры не расходились с ним.
                    val shelvesHere = view.shelves.filter { it.shelf.placeId == place.id }
                    val shelfIds = shelvesHere.map { it.shelf.id }.toSet()
                    val containersHere = data.containers.filter {
                        it.placeId == place.id || it.shelfId in shelfIds
                    }
                    val containerIds = containersHere.map { it.id }.toSet()
                    val itemsHere = data.items.filter {
                        it.placeId == place.id ||
                            it.shelfId in shelfIds ||
                            it.containerId in containerIds
                    }
                    Text(
                        "Стеллажей: ${shelvesHere.size} · Полок: ${data.polki.count { it.placeId == place.id }} · " +
                            "Контейнеров: ${containersHere.size} · Вещей: ${itemsHere.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    shelvesHere.forEach { su ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🗄 ${su.shelf.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(top = 4.dp)
                            )
                            IconButton(onClick = { onEditShelf(su.shelf.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить")
                            }
                        }
                        su.containers.forEach { cu ->
                            Text(
                                "   📦 ${cu.container.name} (${cu.items.size})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniItemRow(db: SqliteDatabase, data: AppData, item: Item, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${if (item.pinned) "⭐ " else ""}${item.name} — ${qtyText(item)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { db.adjustQuantity(item.id, -1) }) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.width(16.dp))
        }
        IconButton(onClick = { db.adjustQuantity(item.id, +1) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(16.dp))
        }
        IconButton(onClick = { onOpen(item.id) }) {
            Icon(Icons.Default.Edit, contentDescription = "Изменить")
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}
