package ru.kladovka.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.*

class Repository(
    private val db: AppDatabase,
    private val localStorage: LocalStorageService? = null,
    private val storagePath: Path? = null
) {
    val places: StateFlow<List<Place>> = MutableStateFlow(emptyList())
    val shelves: StateFlow<List<Shelf>> = MutableStateFlow(emptyList())
    val polki: StateFlow<List<Polka>> = MutableStateFlow(emptyList())
    val containers: StateFlow<List<Container>> = MutableStateFlow(emptyList())
    val items: StateFlow<List<Item>> = MutableStateFlow(emptyList())

    // Простое кэширование для StateFlow
    private var _places = emptyList<Place>()
    private var _shelves = emptyList<Shelf>()
    private var _polki = emptyList<Polka>()
    private var _containers = emptyList<Container>()
    private var _items = emptyList<Item>()

    init {
        // Загружаем из localStorage если есть
        loadFromLocalStorage()
        updateCaches()
    }

    private fun updateCaches() {
        _places = db.placeDao().observeAll().value
        _shelves = db.shelfDao().observeAll().value
        _polki = db.polkaDao().observeAll().value
        _containers = db.containerDao().observeAll().value
        _items = db.itemDao().observeAll().value
    }

    /**
     * Загружает данные из localStorage при инициализации
     */
    private suspend fun loadFromLocalStorage() {
        storagePath?.let { path ->
            localStorage?.let { ls ->
                // Загружаем все типы данных
                val placesJson = ls.load(StorageKeys.PLACES, object : TypeToken<List<Place>>() {}.type)
                val shelvesJson = ls.load(StorageKeys.SHELVES, object : TypeToken<List<Shelf>>() {}.type)
                val polkiJson = ls.load(StorageKeys.POLKAS, object : TypeToken<List<Polka>>() {}.type)
                val containersJson = ls.load(StorageKeys.CONTAINERS, object : TypeToken<List<Container>>() {}.type)
                val itemsJson = ls.load(StorageKeys.ITEMS, object : TypeToken<List<Item>>() {}.type)

                // Если данные есть — применяем к БД
                if (placesJson != null) {
                    val places = gson.fromJson<List<PlaceForStorage>>(placesJson, object : TypeToken<List<PlaceForStorage>>() {}.type)
                    places?.forEach { p ->
                        db.placeDao().insert(
                            Place(
                                id = p.id,
                                name = p.name,
                                notes = p.notes,
                                latitude = p.latitude,
                                longitude = p.longitude
                            )
                        )
                    }
                }

                if (shelvesJson != null) {
                    val shelves = gson.fromJson<List<ShelfForStorage>>(shelvesJson, object : TypeToken<List<ShelfForStorage>>() {}.type)
                    shelves?.forEach { s ->
                        db.shelfDao().insert(
                            Shelf(
                                id = s.id,
                                name = s.name,
                                notes = s.notes,
                                placeId = s.placeId
                            )
                        )
                    }
                }

                if (polkiJson != null) {
                    val polki = gson.fromJson<List<PolkaForStorage>>(polkiJson, object : TypeToken<List<PolkaForStorage>>() {}.type)
                    polki?.forEach { p ->
                        db.polkaDao().insert(
                            Polka(
                                id = p.id,
                                name = p.name,
                                notes = p.notes,
                                shelfId = p.shelfId,
                                placeId = p.placeId
                            )
                        )
                    }
                }

                if (containersJson != null) {
                    val containers = gson.fromJson<List<ContainerForStorage>>(containersJson, object : TypeToken<List<ContainerForStorage>>() {}.type)
                    containers?.forEach { c ->
                        db.containerDao().insert(
                            Container(
                                id = c.id,
                                name = c.name,
                                notes = c.notes,
                                shelfId = c.shelfId,
                                placeId = c.placeId
                            )
                        )
                    }
                }

                if (itemsJson != null) {
                    val items = gson.fromJson<List<ItemForStorage>>(itemsJson, object : TypeToken<List<ItemForStorage>>() {}.type)
                    items?.forEach { i ->
                        db.itemDao().insert(
                            Item(
                                id = i.id,
                                name = i.name,
                                quantity = i.quantity,
                                unit = i.unit,
                                category = i.category,
                                notes = i.notes,
                                containerId = i.containerId,
                                shelfId = i.shelfId,
                                placeId = i.placeId,
                                photoPath = i.photoPath,
                                pinned = i.pinned == 1,
                                createdAt = i.createdAt,
                                updatedAt = i.updatedAt
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Сохраняет данные в localStorage после каждой операции
     */
    private suspend fun saveToLocalStorage() {
        storagePath?.let { path ->
            localStorage?.let { ls ->
                val places = db.placeDao().observeAll().value
                val shelves = db.shelfDao().observeAll().value
                val polki = db.polkaDao().observeAll().value
                val containers = db.containerDao().observeAll().value
                val items = db.itemDao().observeAll().value

                ls.save(StorageKeys.PLACES, places)
                ls.save(StorageKeys.SHELVES, shelves)
                ls.save(StorageKeys.POLKAS, polki)
                ls.save(StorageKeys.CONTAINERS, containers)
                ls.save(StorageKeys.ITEMS, items)
            }
        }
    }

    companion object {
        private val gson = com.google.gson.Gson()
    }

    suspend fun itemById(id: Long): Item? = withContext(Dispatchers.Default) {
        db.itemDao().getById(id)
    }

    suspend fun categories(): List<String> = withContext(Dispatchers.Default) {
        db.itemDao().categories()
    }

    // Методы для получения ID по типам (используются в UI)
    suspend fun shelfId(shelfId: Long): Shelf? = withContext(Dispatchers.Default) {
        db.shelfDao().getById(shelfId)
    }

    suspend fun polkaId(polkaId: Long): Polka? = withContext(Dispatchers.Default) {
        db.polkaDao().getById(polkaId)
    }

    suspend fun containerId(containerId: Long): Container? = withContext(Dispatchers.Default) {
        db.containerDao().getById(containerId)
    }

    suspend fun placeId(placeId: Long): Place? = withContext(Dispatchers.Default) {
        db.placeDao().getById(placeId)
    }

    // Поиск по названию (для UI)
    suspend fun findShelfByName(name: String): Shelf? = withContext(Dispatchers.Default) {
        db.shelfDao().findByName(name)
    }

    suspend fun findPolkaByName(name: String): Polka? = withContext(Dispatchers.Default) {
        db.polkaDao().findByName(name)
    }

    suspend fun findContainerByName(name: String): Container? = withContext(Dispatchers.Default) {
        db.containerDao().findByName(name)
    }

    suspend fun findPlaceByName(name: String): Place? = withContext(Dispatchers.Default) {
        db.placeDao().findByName(name)
    }

    /* ---------- Места ---------- */

    suspend fun upsertPlace(id: Long, name: String, notes: String, latitude: Double?, longitude: Double?) {
        val n = name.trim()
        if (n.isEmpty()) return
        val lat = latitude?.takeIf { it in -90.0..90.0 }
        val lon = longitude?.takeIf { it in -180.0..180.0 }
        val dao = db.placeDao()
        if (id == 0L) {
            dao.insert(Place(name = n, notes = notes.trim(), latitude = lat, longitude = lon))
        } else {
            val old = dao.getById(id) ?: return
            dao.update(old.copy(name = n, notes = notes.trim(), latitude = lat, longitude = lon))
        }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun deletePlace(id: Long) {
        val dao = db.placeDao()
        dao.detachShelves(id)
        dao.detachPolki(id)
        dao.detachContainers(id)
        dao.detachItems(id)
        dao.deleteById(id)
        updateCaches()
        saveToLocalStorage()
    }

    /* ---------- Полки (polki) ---------- */

    suspend fun upsertPolka(id: Long, name: String, notes: String, shelfId: Long?, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        val dao = db.polkaDao()
        if (id == 0L) {
            dao.insert(Polka(name = n, notes = notes.trim(), shelfId = shelfId, placeId = placeId))
        } else {
            val old = dao.getById(id) ?: return
            dao.update(old.copy(name = n, notes = notes.trim(), shelfId = shelfId, placeId = placeId))
        }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun deletePolka(id: Long) {
        db.polkaDao().deleteById(id)
        updateCaches()
        saveToLocalStorage()
    }

    /* ---------- Стеллажи ---------- */

    suspend fun upsertShelf(id: Long, name: String, notes: String, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        val dao = db.shelfDao()
        if (id == 0L) {
            dao.insert(Shelf(name = n, notes = notes.trim(), placeId = placeId))
        } else {
            val old = dao.getById(id) ?: return
            dao.update(old.copy(name = n, notes = notes.trim(), placeId = placeId))
        }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun deleteShelf(id: Long) {
        val dao = db.shelfDao()
        dao.detachPolki(id)
        dao.detachContainers(id)
        dao.detachItems(id)
        dao.deleteById(id)
        updateCaches()
        saveToLocalStorage()
    }

    /* ---------- Контейнеры ---------- */

    suspend fun upsertContainer(id: Long, name: String, shelfId: Long?, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        val dao = db.containerDao()
        if (id == 0L) {
            dao.insert(Container(name = n, shelfId = shelfId, placeId = placeId))
        } else {
            val old = dao.getById(id) ?: return
            dao.update(old.copy(name = n, shelfId = shelfId, placeId = placeId))
        }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun deleteContainer(id: Long) {
        db.containerDao().detachItems(id)
        db.containerDao().deleteById(id)
        updateCaches()
        saveToLocalStorage()
    }

    /* ---------- Вещи ---------- */

    suspend fun upsertItem(
        id: Long,
        name: String,
        quantity: Int,
        unit: String,
        category: String,
        notes: String,
        containerId: Long?,
        shelfId: Long?,
        placeId: Long?,
        photoPath: String?
    ) {
        val n = name.trim()
        if (n.isEmpty()) return
        val now = System.currentTimeMillis()
        val dao = db.itemDao()
        if (id == 0L) {
            dao.insert(Item(
                name = n,
                quantity = quantity,
                unit = unit.trim(),
                category = category.trim(),
                notes = notes.trim(),
                containerId = containerId,
                shelfId = shelfId,
                placeId = placeId,
                photoPath = photoPath,
                createdAt = now,
                updatedAt = now
            ))
        } else {
            val old = dao.getById(id) ?: return
            dao.update(old.copy(
                name = n,
                quantity = quantity,
                unit = unit.trim(),
                category = category.trim(),
                notes = notes.trim(),
                containerId = containerId,
                shelfId = shelfId,
                placeId = placeId,
                photoPath = photoPath,
                updatedAt = now
            ))
        }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun deleteItem(id: Long) {
        val old = db.itemDao().getById(id)
        db.itemDao().deleteById(id)
        old?.photoPath?.let { deletePhotoFile(it) }
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun setItemPinned(id: Long, pinned: Boolean) {
        val old = db.itemDao().getById(id) ?: return
        db.itemDao().update(old.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
        updateCaches()
        saveToLocalStorage()
    }

    suspend fun changeItemQuantity(id: Long, delta: Int) {
        db.itemDao().atomicallyAdjustQuantity(id, delta, System.currentTimeMillis())
        updateCaches()
        saveToLocalStorage()
    }

    /* ---------- Фото ---------- */

    suspend fun savePhoto(uri: String): String? = withContext(Dispatchers.Default) {
        runCatching {
            val dir = File("kladovka-desktop/data/photos").apply { mkdirs() }
            val out = File(dir, UUID.randomUUID().toString() + ".jpg")

            // Упрощенная обработка: считываем из URI
            val input = File(uri)
            if (!input.exists()) return@runCatching null

            val bitmap = java.awt.ImageIO.read(input)
            if (bitmap == null) return@runCatching null

            val width = bitmap.width
            val height = bitmap.height
            var sample = 1
            while ((width / sample) > 3200 || (height / sample) > 3200) sample *= 2

            val scaled = java.awt.ImageIO.createThumbnail(bitmap, width / sample, height / sample)
            java.awt.ImageIO.write(scaled, "jpg", out)
            out.absolutePath
        }.getOrNull()
    }

    fun deletePhotoFile(path: String) {
        runCatching { File(path).delete() }
    }

    /* ---------- Бэкап (JSON) ---------- */

    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        val root = JSONObject().apply {
            put("app", "kladovka")
            put("exportedAt", System.currentTimeMillis())
            put("places", JSONArray().apply {
                places.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("notes", p.notes)
                    })
                }
            })
            put("shelves", JSONArray().apply {
                shelves.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("notes", s.notes)
                        put("placeId", s.placeId)
                        put("location", s.location)
                    })
                }
            })
            put("polki", JSONArray().apply {
                polki.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("notes", p.notes)
                        put("shelfId", p.shelfId)
                        put("placeId", p.placeId)
                    })
                }
            })
            put("containers", JSONArray().apply {
                containers.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("shelfId", c.shelfId)
                        put("placeId", c.placeId)
                        put("location", c.location)
                    })
                }
            })
            put("items", JSONArray().apply {
                items.forEach { i ->
                    put(JSONObject().apply {
                        put("id", i.id)
                        put("name", i.name)
                        put("quantity", i.quantity)
                        put("unit", i.unit)
                        put("category", i.category)
                        put("notes", i.notes)
                        put("containerId", i.containerId)
                        put("shelfId", i.shelfId)
                        put("placeId", i.placeId)
                        put("photoPath", i.photoPath)
                        put("pinned", if (i.pinned) 1 else 0)
                        put("createdAt", i.createdAt)
                        put("updatedAt", i.updatedAt)
                    })
                }
            })
        }
        root.toString(2)
    }

    suspend fun importJson(json: String): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val root = JSONObject(json)
            if (root.optString("app") != "kladovka") return@runCatching false

            // Очищаем и вставляем
            with(db) {
                placeDao().clearAllTables()
                shelfDao().clearAllTables()
                polkaDao().clearAllTables()
                containerDao().clearAllTables()
                itemDao().clearAllTables()

                val places = root.optJSONArray("places") ?: JSONArray()
                for (i in 0 until places.length()) {
                    val o = places.getJSONObject(i)
                    placeDao().insert(
                        Place(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                            longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                            notes = o.optString("notes")
                        )
                    )
                }

                val shelves = root.optJSONArray("shelves") ?: JSONArray()
                for (i in 0 until shelves.length()) {
                    val o = shelves.getJSONObject(i)
                    shelfDao().insert(
                        Shelf(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            notes = o.optString("notes"),
                            placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                            location = o.optString("location")
                        )
                    )
                }

                val polki = root.optJSONArray("polki") ?: JSONArray()
                for (i in 0 until polki.length()) {
                    val o = polki.getJSONObject(i)
                    polkaDao().insert(
                        Polka(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            notes = o.optString("notes"),
                            shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                            placeId = if (o.isNull("placeId")) null else o.optLong("placeId")
                        )
                    )
                }

                val containers = root.optJSONArray("containers") ?: JSONArray()
                for (i in 0 until containers.length()) {
                    val o = containers.getJSONObject(i)
                    containerDao().insert(
                        Container(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                            placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                            location = o.optString("location")
                        )
                    )
                }

                val items = root.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    itemDao().insert(
                        Item(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            quantity = o.optInt("quantity", 1),
                            unit = o.optString("unit"),
                            category = o.optString("category"),
                            notes = o.optString("notes"),
                            containerId = if (o.isNull("containerId")) null else o.optLong("containerId"),
                            shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                            placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                            photoPath = if (o.isNull("photoPath")) null else o.optString("photoPath"),
                            pinned = o.optInt("pinned", 0) != 0,
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.Default) {
        val places = places.associateBy { it.id }
        val shelves = shelves.associateBy { it.id }
        val containers = containers.associateBy { it.id }

        fun esc(s: String): String =
            "\"" + s.replace("\"", "\"\"") + "\""

        fun itemLocation(i: Item): String {
            val c = i.containerId?.let(containers::get)
            val s = c?.shelfId?.let(shelves::get) ?: i.shelfId?.let(shelves::get)
            val p = i.placeId?.let(places::get) ?: c?.placeId?.let(places::get) ?: s?.placeId?.let(places::get)
            return listOfNotNull(
                p?.name,
                c?.name,
                s?.name
            ).joinToString(" · ").ifEmpty { "Без места" }
        }

        val items = items.sortedBy { it.name.lowercase() }
        buildString {
            append('\uFEFF') // BOM
            append("Название;Кол-во;Ед.;Категория;Место;Заметки\r\n")
            items.forEach { i ->
                append(esc(i.name)).append(';')
                append(i.quantity).append(';')
                append(esc(i.unit)).append(';')
                append(esc(i.category)).append(';')
                append(esc(itemLocation(i))).append(';')
                append(esc(i.notes)).append("\r\n")
            }
        }
    }
}
