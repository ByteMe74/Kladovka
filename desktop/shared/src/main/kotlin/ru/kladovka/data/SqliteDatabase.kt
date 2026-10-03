package ru.kladovka.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Хранилище на SQLite через JDBC.
 *
 * Схема таблиц побайтово повторяет Room-схему Android-приложения (version = 5),
 * поэтому база, выгруженная с телефона, открывается здесь без конвертации.
 *
 * Все изменения проходят через [mutate], который держит одну транзакцию на операцию
 * и после успеха перечитывает данные в [data] — на этом построен весь UI.
 */
class SqliteDatabase(private val file: Path) {

    private val connection: Connection
    private val _data = MutableStateFlow(AppData())
    private val _error = MutableStateFlow<String?>(null)

    /** Текущее сводное состояние; UI подписывается на него через [data]. */
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** Последняя ошибка БД (null — всё хорошо). */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        Files.createDirectories(file.toAbsolutePath().parent)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")
        connection.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = OFF")
            st.execute("PRAGMA journal_mode = WAL")
        }
        createSchema()
        ensureColumns()
        reload()
    }

    // ------------------------------------------------------------------ schema

    private fun createSchema() = connection.createStatement().use { st ->
        st.executeUpdate(
            """CREATE TABLE IF NOT EXISTS places (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                notes TEXT NOT NULL DEFAULT '')"""
        )
        st.executeUpdate(
            """CREATE TABLE IF NOT EXISTS shelves (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                placeId INTEGER,
                location TEXT NOT NULL DEFAULT '')"""
        )
        st.executeUpdate(
            """CREATE TABLE IF NOT EXISTS polki (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                shelfId INTEGER,
                placeId INTEGER)"""
        )
        st.executeUpdate(
            """CREATE TABLE IF NOT EXISTS containers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                shelfId INTEGER,
                placeId INTEGER,
                location TEXT NOT NULL DEFAULT '')"""
        )
        st.executeUpdate(
            """CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                unit TEXT NOT NULL,
                category TEXT NOT NULL,
                notes TEXT NOT NULL,
                containerId INTEGER,
                shelfId INTEGER,
                placeId INTEGER,
                photoPath TEXT,
                pinned INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL)"""
        )
    }

    /**
     * Догоняет базу, созданную прошлыми версиями приложения: Room при обновлении
     * выполнял те же ALTER TABLE, что и мы здесь. Нужно, чтобы открыть не только
     * свежую базу, но и выгруженную со старой версии Android.
     */
    private fun ensureColumns() {
        fun ensure(table: String, column: String, decl: String) {
            val present = mutableSetOf<String>()
            connection.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info($table)").use { rs ->
                    while (rs.next()) present += rs.getString("name")
                }
            }
            if (column !in present) {
                connection.createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $column $decl") }
            }
        }
        ensure("shelves", "notes", "TEXT NOT NULL DEFAULT ''")
        ensure("shelves", "placeId", "INTEGER")
        ensure("shelves", "location", "TEXT NOT NULL DEFAULT ''")
        ensure("polki", "notes", "TEXT NOT NULL DEFAULT ''")
        ensure("polki", "shelfId", "INTEGER")
        ensure("polki", "placeId", "INTEGER")
        ensure("containers", "placeId", "INTEGER")
        ensure("containers", "location", "TEXT NOT NULL DEFAULT ''")
        ensure("items", "placeId", "INTEGER")
        ensure("items", "photoPath", "TEXT")
        ensure("items", "pinned", "INTEGER NOT NULL DEFAULT 0")
        ensure("items", "createdAt", "INTEGER NOT NULL DEFAULT 0")
        ensure("items", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
    }

    // ------------------------------------------------------------------ helpers

    private inline fun <T> tx(block: () -> T): T {
        val auto = connection.autoCommit
        connection.autoCommit = false
        return try {
            val r = block()
            connection.commit()
            r
        } catch (e: Throwable) {
            runCatching { connection.rollback() }
            throw e
        } finally {
            connection.autoCommit = auto
        }
    }

    /** Выполняет операцию с перечитыванием состояния; ошибка попадает в [error]. */
    private inline fun mutate(block: () -> Unit) {
        try {
            tx(block)
            reload()
        } catch (e: SQLException) {
            _error.value = e.message ?: "Ошибка базы данных"
        }
    }

    /**
     * Выполняет запрос и сразу материализует строки.
     *
     * Возвращать сами ResultSet нельзя: statement и result set закрываются вместе
     * с блоком use, и читать из них после этого нельзя. Поэтому строки забираются
     * через [map] до закрытия.
     */
    private fun <T> Connection.query(sql: String, map: (ResultSet) -> T, vararg args: Any?): List<T> =
        prepareStatement(sql).use { st ->
            st.setParams(args)
            st.executeQuery().use { rs ->
                val out = ArrayList<T>()
                while (rs.next()) out.add(map(rs))
                out
            }
        }

    private fun Connection.ex(sql: String, vararg args: Any?): Int {
        prepareStatement(sql).use { st ->
            st.setParams(args)
            return st.executeUpdate()
        }
    }

    private fun java.sql.PreparedStatement.setParams(args: Array<out Any?>) {
        args.forEachIndexed { i, v ->
            val idx = i + 1
            when (v) {
                null -> setObject(idx, null)
                is String -> setString(idx, v)
                is Int -> setInt(idx, v)
                is Long -> setLong(idx, v)
                is Double -> setDouble(idx, v)
                is Boolean -> setInt(idx, if (v) 1 else 0)
                else -> setObject(idx, v)
            }
        }
    }

    /** Числовые/текстовые колонки, допускающие NULL: SQLite отдаёт 0, а не null. */
    private fun ResultSet.longOrNull(label: String): Long? =
        getLong(label).let { if (wasNull()) null else it }

    private fun ResultSet.dblOrNull(label: String): Double? =
        getDouble(label).let { if (wasNull()) null else it }

    private fun ResultSet.strOrNull(label: String): String? = getString(label)

    // ------------------------------------------------------------------ loading

    private fun reload() {
        _data.value = AppData(
            places = connection.query("SELECT * FROM places ORDER BY name COLLATE NOCASE", { r ->
                Place(r.getLong("id"), r.getString("name"),
                    r.dblOrNull("latitude"), r.dblOrNull("longitude"),
                    r.getString("notes") ?: "")
            }),
            shelves = connection.query("SELECT * FROM shelves ORDER BY name COLLATE NOCASE", { r ->
                Shelf(r.getLong("id"), r.getString("name"), r.getString("notes") ?: "",
                    r.longOrNull("placeId"), r.getString("location") ?: "")
            }),
            polki = connection.query("SELECT * FROM polki ORDER BY name COLLATE NOCASE", { r ->
                Polka(r.getLong("id"), r.getString("name"), r.getString("notes") ?: "",
                    r.longOrNull("shelfId"), r.longOrNull("placeId"))
            }),
            containers = connection.query("SELECT * FROM containers ORDER BY name COLLATE NOCASE", { r ->
                Container(r.getLong("id"), r.getString("name"),
                    r.longOrNull("shelfId"), r.longOrNull("placeId"),
                    r.getString("location") ?: "")
            }),
            items = connection.query(
                "SELECT * FROM items ORDER BY name COLLATE NOCASE, updatedAt DESC",
                { r -> r.toItem() }
            )
        )
    }

    // ------------------------------------------------------------------ places

    fun savePlace(p: Place): Long = mutateAndReturn(p.id) {
        if (p.id == 0L) {
            connection.ex(
                "INSERT INTO places (name,latitude,longitude,notes) VALUES (?,?,?,?)",
                p.name, p.latitude, p.longitude, p.notes
            )
            lastInsert()
        } else {
            connection.ex(
                "UPDATE places SET name=?,latitude=?,longitude=?,notes=? WHERE id=?",
                p.name, p.latitude, p.longitude, p.notes, p.id
            ); p.id
        }
    }

    fun deletePlace(id: Long) = mutate {
        // Как и на Android: удаление места не удаляет содержимое — связи обнуляются.
        connection.ex("UPDATE shelves SET placeId=NULL WHERE placeId=?", id)
        connection.ex("UPDATE polki SET placeId=NULL WHERE placeId=?", id)
        connection.ex("UPDATE containers SET placeId=NULL WHERE placeId=?", id)
        connection.ex("UPDATE items SET placeId=NULL WHERE placeId=?", id)
        connection.ex("DELETE FROM places WHERE id=?", id)
    }

    // ------------------------------------------------------------------ shelves

    fun saveShelf(s: Shelf): Long = mutateAndReturn(s.id) {
        if (s.id == 0L) {
            connection.ex(
                "INSERT INTO shelves (name,notes,placeId,location) VALUES (?,?,?,?)",
                s.name, s.notes, s.placeId, s.location
            )
            lastInsert()
        } else {
            connection.ex(
                "UPDATE shelves SET name=?,notes=?,placeId=?,location=? WHERE id=?",
                s.name, s.notes, s.placeId, s.location, s.id
            ); s.id
        }
    }

    fun deleteShelf(id: Long) = mutate {
        connection.ex("UPDATE polki SET shelfId=NULL WHERE shelfId=?", id)
        connection.ex("UPDATE containers SET shelfId=NULL WHERE shelfId=?", id)
        connection.ex("UPDATE items SET shelfId=NULL WHERE shelfId=?", id)
        connection.ex("DELETE FROM shelves WHERE id=?", id)
    }

    // ------------------------------------------------------------------ polki

    fun savePolka(p: Polka): Long = mutateAndReturn(p.id) {
        if (p.id == 0L) {
            connection.ex("INSERT INTO polki (name,notes,shelfId,placeId) VALUES (?,?,?,?)",
                p.name, p.notes, p.shelfId, p.placeId)
            lastInsert()
        } else {
            connection.ex("UPDATE polki SET name=?,notes=?,shelfId=?,placeId=? WHERE id=?",
                p.name, p.notes, p.shelfId, p.placeId, p.id)
            p.id
        }
    }

    fun deletePolka(id: Long) = mutate { connection.ex("DELETE FROM polki WHERE id=?", id) }

    // ------------------------------------------------------------------ containers

    fun saveContainer(c: Container): Long = mutateAndReturn(c.id) {
        if (c.id == 0L) {
            connection.ex("INSERT INTO containers (name,shelfId,placeId,location) VALUES (?,?,?,?)",
                c.name, c.shelfId, c.placeId, c.location)
            lastInsert()
        } else {
            connection.ex("UPDATE containers SET name=?,shelfId=?,placeId=?,location=? WHERE id=?",
                c.name, c.shelfId, c.placeId, c.location, c.id)
            c.id
        }
    }

    fun deleteContainer(id: Long) = mutate {
        connection.ex("UPDATE items SET containerId=NULL WHERE containerId=?", id)
        connection.ex("DELETE FROM containers WHERE id=?", id)
    }

    // ------------------------------------------------------------------ items

    fun saveItem(i: Item): Long = mutateAndReturn(i.id) {
        val now = System.currentTimeMillis()
        if (i.id == 0L) {
            // Как на Android: createdAt/updatedAt проставляются при первом сохранении.
            val created = if (i.createdAt == 0L) now else i.createdAt
            connection.ex(
                """INSERT INTO items
                   (name,quantity,unit,category,notes,containerId,shelfId,placeId,photoPath,pinned,createdAt,updatedAt)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                i.name, i.quantity, i.unit, i.category, i.notes,
                i.containerId, i.shelfId, i.placeId, i.photoPath,
                if (i.pinned) 1 else 0, created, now
            )
            lastInsert()
        } else {
            val prev = connection.query("SELECT createdAt FROM items WHERE id=?", { it.getLong("createdAt") }, i.id)
                .firstOrNull() ?: i.createdAt
            connection.ex(
                """UPDATE items SET name=?,quantity=?,unit=?,category=?,notes=?,containerId=?,shelfId=?,
                   placeId=?,photoPath=?,pinned=?,createdAt=?,updatedAt=? WHERE id=?""",
                i.name, i.quantity, i.unit, i.category, i.notes,
                i.containerId, i.shelfId, i.placeId, i.photoPath,
                if (i.pinned) 1 else 0, prev, now, i.id
            )
            i.id
        }
    }

    fun deleteItem(id: Long) = mutate { connection.ex("DELETE FROM items WHERE id=?", id) }

    fun setItemPinned(id: Long, pinned: Boolean) = mutate {
        connection.ex("UPDATE items SET pinned=?, updatedAt=? WHERE id=?",
            if (pinned) 1 else 0, System.currentTimeMillis(), id)
    }

    /** Количество не падает ниже единицы — повторяет запрос Room `MAX(1, quantity + :delta)`. */
    fun adjustQuantity(id: Long, delta: Int) = mutate {
        connection.ex("UPDATE items SET quantity=MAX(1, quantity + ?), updatedAt=? WHERE id=?",
            delta, System.currentTimeMillis(), id)
    }

    /**
     * Поиск по названию, категории и заметкам.
     *
     * Фильтрация намеренно выполняется в Kotlin, а не через SQL `LIKE`: SQLite
     * регистронезависим только для ASCII, поэтому запрос вида `LIKE '%м6%'` не находит
     * «М6». На телефоне используется именно такой LIKE — здесь поведение исправлено,
     * при выборе номера из базы всё так же отдаётся всё (быстрый путь без затрат).
     */
    fun searchItems(q: String): List<Item> {
        val all = connection.query(
            "SELECT * FROM items ORDER BY name COLLATE NOCASE, updatedAt DESC",
            { it.toItem() }
        )
        if (q.isBlank()) return all
        return all.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.notes.contains(q, ignoreCase = true)
        }
    }

    /** Различные непустые категории — для подсказок в поле ввода. */
    fun categories(): List<String> =
        connection.query(
            "SELECT DISTINCT category FROM items WHERE category <> '' ORDER BY category COLLATE NOCASE",
            { it.getString("category") }
        )

    // ------------------------------------------------------------------ bulk

    /**
     * Полная замена содержимого базы — используется импортом бэкапа и загрузкой с сервера.
     * Вся операция в одной транзакции: при ошибке откат оставит прежние данные целыми.
     */
    fun replaceAll(d: AppData) = mutate {
        tx {
            for (t in listOf("items", "containers", "polki", "shelves", "places")) {
                connection.ex("DELETE FROM $t")
            }
            d.places.forEach { p ->
                connection.ex("INSERT INTO places (id,name,latitude,longitude,notes) VALUES (?,?,?,?,?)",
                    p.id, p.name, p.latitude, p.longitude, p.notes)
            }
            d.shelves.forEach { s ->
                connection.ex("INSERT INTO shelves (id,name,notes,placeId,location) VALUES (?,?,?,?,?)",
                    s.id, s.name, s.notes, s.placeId, s.location)
            }
            d.polki.forEach { p ->
                connection.ex("INSERT INTO polki (id,name,notes,shelfId,placeId) VALUES (?,?,?,?,?)",
                    p.id, p.name, p.notes, p.shelfId, p.placeId)
            }
            d.containers.forEach { c ->
                connection.ex("INSERT INTO containers (id,name,shelfId,placeId,location) VALUES (?,?,?,?,?)",
                    c.id, c.name, c.shelfId, c.placeId, c.location)
            }
            d.items.forEach { i ->
                connection.ex(
                    """INSERT INTO items (id,name,quantity,unit,category,notes,containerId,shelfId,
                       placeId,photoPath,pinned,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    i.id, i.name, i.quantity, i.unit, i.category, i.notes,
                    i.containerId, i.shelfId, i.placeId, i.photoPath,
                    if (i.pinned) 1 else 0, i.createdAt, i.updatedAt
                )
            }
            // Сдвигаем счётчики AUTOINCREMENT за пределы вставленных id, иначе следующая
            // вставка без явного id столкнётся с уже занятыми значениями.
            for (t in listOf("places", "shelves", "polki", "containers", "items")) {
                val max = connection.query("SELECT COALESCE(MAX(id),0) AS m FROM $t", { it.getLong("m") })
                    .firstOrNull() ?: 0
                connection.ex("UPDATE sqlite_sequence SET seq=? WHERE name=?", max, t)
            }
        }
    }

    fun clearAll() = replaceAll(AppData())

    fun close() = runCatching { connection.close() }

    // ------------------------------------------------------------------ private

    private fun ResultSet.toItem() = Item(
        getLong("id"), getString("name"), getInt("quantity"), getString("unit") ?: "",
        getString("category") ?: "", getString("notes") ?: "",
        longOrNull("containerId"), longOrNull("shelfId"), longOrNull("placeId"),
        strOrNull("photoPath"), getInt("pinned") == 1,
        getLong("createdAt"), getLong("updatedAt")
    )

    private fun lastInsert(): Long = connection.createStatement().use {
        it.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getLong(1) }
    }

    /** Как [mutate], но возвращает id изменённой записи (нужен для выбора строки в UI). */
    private inline fun mutateAndReturn(currentId: Long, block: () -> Long): Long {
        var result = currentId
        try {
            tx {
                result = block()
            }
            reload()
        } catch (e: SQLException) {
            _error.value = e.message ?: "Ошибка базы данных"
        }
        return result
    }
}
