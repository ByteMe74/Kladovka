package ru.kladovka.data

import org.sqlite.SQLiteConnection
import org.sqlite.JDBC
import java.io.File
import java.nio.file.Path
import java.sql.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AppDatabaseImpl(private val db: AppDatabase, private val path: String) : AppDatabase by db {
    
    companion object {
        const val DB_VERSION = 6
    }
    
    fun createDatabase() {
        // Создаем таблицу с версией для миграций
        val versionTable = """
            CREATE TABLE IF NOT EXISTS _kladovka_version (
                version INTEGER NOT NULL
            );
        """.trimIndent()
        
        val conn = DriverManager.getConnection("jdbc:sqlite:$path")
        try {
            conn.createStatement().execute(versionTable)
            
            // Применяем миграции если версия меньше
            applyMigrations(conn)
            
            // Записываем текущую версию
            conn.prepareStatement("INSERT OR REPLACE INTO _kladovka_version (version) VALUES (?)")
                .setInt(1, DB_VERSION)
                .executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    private fun applyMigrations(conn: Connection) {
        val currentVersion = conn.prepareStatement("SELECT version FROM _kladovka_version").use {
            it.executeQuery().let { rs ->
                if (rs.next()) rs.getInt("version") else 0
            }
        }
        
        if (currentVersion < DB_VERSION) {
            when (currentVersion) {
                5 -> {
                    // MIGRATION_5_6: Добавление location в shelves и containers
                    migrate5To6(conn)
                }
            }
        }
    }
    
    private fun migrate5To6(conn: Connection) {
        // Добавляем column location в shelves
        conn.prepareStatement("""
            ALTER TABLE shelves ADD COLUMN location TEXT
        """.trimIndent()).execute()
        
        // Добавляем column location в containers
        conn.prepareStatement("""
            ALTER TABLE containers ADD COLUMN location TEXT
        """.trimIndent()).execute()
        
        println("Migration 5->6 completed: Added location to shelves and containers")
    }

    fun createDatabase() {
        val sql = """
            CREATE TABLE IF NOT EXISTS places (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                notes TEXT,
                latitude REAL,
                longitude REAL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE TABLE IF NOT EXISTS shelves (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                notes TEXT,
                place_id INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
            );
            
            CREATE TABLE IF NOT EXISTS polki (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                notes TEXT,
                shelf_id INTEGER,
                place_id INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE CASCADE,
                FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
            );
            
            CREATE TABLE IF NOT EXISTS containers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                notes TEXT,
                shelf_id INTEGER,
                place_id INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE CASCADE,
                FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
            );
            
            CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 0,
                unit TEXT DEFAULT 'шт.',
                category TEXT DEFAULT 'Общее',
                notes TEXT,
                container_id INTEGER,
                shelf_id INTEGER,
                place_id INTEGER,
                photo_path TEXT,
                pinned INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (container_id) REFERENCES containers(id) ON DELETE CASCADE,
                FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE CASCADE,
                FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
            );
            
            CREATE INDEX IF NOT EXISTS idx_items_name ON items(name);
            CREATE INDEX IF NOT EXISTS idx_shelves_name ON shelves(name);
            CREATE INDEX IF NOT EXISTS idx_polki_name ON polki(name);
            CREATE INDEX IF NOT EXISTS idx_containers_name ON containers(name);
            CREATE INDEX IF NOT EXISTS idx_places_name ON places(name);
        """.trimIndent()
        
        try {
            val conn = DriverManager.getConnection("jdbc:sqlite:$path")
            conn.createStatement().execute(sql)
            conn.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun placeDao(): PlaceDao = PlaceDaoImpl(this)
    override fun shelfDao(): ShelfDao = ShelfDaoImpl(this)
    override fun polkaDao(): PolkaDao = PolkaDaoImpl(this)
    override fun containerDao(): ContainerDao = ContainerDaoImpl(this)
    override fun itemDao(): ItemDao = ItemDaoImpl(this)
}

open class PlaceDaoImpl(private val db: AppDatabaseImpl) : PlaceDao() {
    
    override suspend fun insert(place: Place): Long = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val stmt = conn.prepareStatement(
                """INSERT INTO places (name, notes, latitude, longitude) VALUES (?, ?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS
            )
            stmt.setString(1, place.name)
            stmt.setString(2, place.notes)
            stmt.setDouble(3, place.latitude ?: 0.0)
            stmt.setDouble(4, place.longitude ?: 0.0)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) rs.getLong(1) else 0L
        } finally {
            conn.close()
        }
    }
    
    override suspend fun update(place: Place): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement(
                """UPDATE places SET name = ?, notes = ?, latitude = ?, longitude = ? WHERE id = ?"""
            ).apply {
                setString(1, place.name)
                setString(2, place.notes)
                setDouble(3, place.latitude ?: 0.0)
                setDouble(4, place.longitude ?: 0.0)
                setLong(5, place.id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun getById(id: Long): Place? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM places WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()
            if (rs.next()) toPlace(rs)
        } finally {
            conn.close()
        }
    }
    
    override suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM places WHERE id = ?").apply {
                setLong(1, id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun observeAll(): StateFlow<List<Place>> = MutableStateFlow(emptyList())
    
    override suspend fun detachShelves(placeId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM shelves WHERE place_id = ?").apply {
                setLong(1, placeId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun detachPolki(placeId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM polki WHERE place_id = ?").apply {
                setLong(1, placeId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun detachContainers(placeId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM containers WHERE place_id = ?").apply {
                setLong(1, placeId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun detachItems(placeId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM items WHERE place_id = ?").apply {
                setLong(1, placeId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun findByName(name: String): Place? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM places WHERE name LIKE ?").apply {
                setString(1, "%$name%")
            }.executeQuery()
            if (rs.next()) toPlace(rs)
        } finally {
            conn.close()
        }
    }
}

private fun toPlace(rs: ResultSet): Place {
    val id = rs.getLong("id")
    val name = rs.getString("name")
    val notes = rs.getString("notes")
    val latitude = rs.getDouble("latitude")
    val longitude = rs.getDouble("longitude")
    return Place(id = id, name = name, notes = notes, latitude = latitude, longitude = longitude)
}

open class ShelfDaoImpl(private val db: AppDatabaseImpl) : ShelfDao() {
    
    override suspend fun insert(shelf: Shelf): Long = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val stmt = conn.prepareStatement(
                """INSERT INTO shelves (name, notes, place_id) VALUES (?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS
            )
            stmt.setString(1, shelf.name)
            stmt.setString(2, shelf.notes)
            stmt.setLong(3, shelf.placeId ?: 0)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) rs.getLong(1) else 0L
        } finally {
            conn.close()
        }
    }
    
    override suspend fun update(shelf: Shelf): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement(
                """UPDATE shelves SET name = ?, notes = ?, place_id = ? WHERE id = ?"""
            ).apply {
                setString(1, shelf.name)
                setString(2, shelf.notes)
                setLong(3, shelf.placeId ?: 0)
                setLong(4, shelf.id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun getById(id: Long): Shelf? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM shelves WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()
            if (rs.next()) toShelf(rs)
        } finally {
            conn.close()
        }
    }
    
    override suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM shelves WHERE id = ?").apply {
                setLong(1, id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun observeAll(): StateFlow<List<Shelf>> = MutableStateFlow(emptyList())
    
    override suspend fun detachPolki(shelfId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM polki WHERE shelf_id = ?").apply {
                setLong(1, shelfId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun detachContainers(shelfId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM containers WHERE shelf_id = ?").apply {
                setLong(1, shelfId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun detachItems(shelfId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM items WHERE shelf_id = ?").apply {
                setLong(1, shelfId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun findByName(name: String): Shelf? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM shelves WHERE name LIKE ?").apply {
                setString(1, "%$name%")
            }.executeQuery()
            if (rs.next()) toShelf(rs)
        } finally {
            conn.close()
        }
    }
}

private fun toShelf(rs: ResultSet): Shelf {
    val id = rs.getLong("id")
    val name = rs.getString("name")
    val notes = rs.getString("notes")
    val placeId = rs.getLong("place_id")
    return Shelf(id = id, name = name, notes = notes, placeId = if (placeId != 0L) placeId else null)
}

open class PolkaDaoImpl(private val db: AppDatabaseImpl) : PolkaDao() {
    
    override suspend fun insert(polka: Polka): Long = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val stmt = conn.prepareStatement(
                """INSERT INTO polki (name, notes, shelf_id, place_id) VALUES (?, ?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS
            )
            stmt.setString(1, polka.name)
            stmt.setString(2, polka.notes)
            stmt.setLong(3, polka.shelfId ?: 0)
            stmt.setLong(4, polka.placeId ?: 0)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) rs.getLong(1) else 0L
        } finally {
            conn.close()
        }
    }
    
    override suspend fun update(polka: Polka): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement(
                """UPDATE polki SET name = ?, notes = ?, shelf_id = ?, place_id = ? WHERE id = ?"""
            ).apply {
                setString(1, polka.name)
                setString(2, polka.notes)
                setLong(3, polka.shelfId ?: 0)
                setLong(4, polka.placeId ?: 0)
                setLong(5, polka.id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun getById(id: Long): Polka? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM polki WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()
            if (rs.next()) toPolka(rs)
        } finally {
            conn.close()
        }
    }
    
    override suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM polki WHERE id = ?").apply {
                setLong(1, id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun observeAll(): StateFlow<List<Polka>> = MutableStateFlow(emptyList())
    
    override suspend fun findByName(name: String): Polka? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM polki WHERE name LIKE ?").apply {
                setString(1, "%$name%")
            }.executeQuery()
            if (rs.next()) toPolka(rs)
        } finally {
            conn.close()
        }
    }
}

private fun toPolka(rs: ResultSet): Polka {
    val id = rs.getLong("id")
    val name = rs.getString("name")
    val notes = rs.getString("notes")
    val shelfId = rs.getLong("shelf_id")
    val placeId = rs.getLong("place_id")
    return Polka(
        id = id,
        name = name,
        notes = notes,
        shelfId = if (shelfId != 0L) shelfId else null,
        placeId = if (placeId != 0L) placeId else null
    )
}

open class ContainerDaoImpl(private val db: AppDatabaseImpl) : ContainerDao() {
    
    override suspend fun insert(container: Container): Long = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val stmt = conn.prepareStatement(
                """INSERT INTO containers (name, notes, shelf_id, place_id) VALUES (?, ?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS
            )
            stmt.setString(1, container.name)
            stmt.setString(2, container.notes)
            stmt.setLong(3, container.shelfId ?: 0)
            stmt.setLong(4, container.placeId ?: 0)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) rs.getLong(1) else 0L
        } finally {
            conn.close()
        }
    }
    
    override suspend fun update(container: Container): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement(
                """UPDATE containers SET name = ?, notes = ?, shelf_id = ?, place_id = ? WHERE id = ?"""
            ).apply {
                setString(1, container.name)
                setString(2, container.notes)
                setLong(3, container.shelfId ?: 0)
                setLong(4, container.placeId ?: 0)
                setLong(5, container.id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun getById(id: Long): Container? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM containers WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()
            if (rs.next()) toContainer(rs)
        } finally {
            conn.close()
        }
    }
    
    override suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM containers WHERE id = ?").apply {
                setLong(1, id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun observeAll(): StateFlow<List<Container>> = MutableStateFlow(emptyList())
    
    override suspend fun detachItems(containerId: Long) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM items WHERE container_id = ?").apply {
                setLong(1, containerId)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun findByName(name: String): Container? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM containers WHERE name LIKE ?").apply {
                setString(1, "%$name%")
            }.executeQuery()
            if (rs.next()) toContainer(rs)
        } finally {
            conn.close()
        }
    }
}

private fun toContainer(rs: ResultSet): Container {
    val id = rs.getLong("id")
    val name = rs.getString("name")
    val notes = rs.getString("notes")
    val shelfId = rs.getLong("shelf_id")
    val placeId = rs.getLong("place_id")
    return Container(
        id = id,
        name = name,
        notes = notes,
        shelfId = if (shelfId != 0L) shelfId else null,
        placeId = if (placeId != 0L) placeId else null
    )
}

open class ItemDaoImpl(private val db: AppDatabaseImpl) : ItemDao() {
    
    override suspend fun insert(item: Item): Long = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val stmt = conn.prepareStatement(
                """INSERT INTO items (name, quantity, unit, category, notes, container_id, shelf_id, place_id, photo_path, pinned) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS
            )
            stmt.setString(1, item.name)
            stmt.setInt(2, item.quantity)
            stmt.setString(3, item.unit)
            stmt.setString(4, item.category)
            stmt.setString(5, item.notes)
            stmt.setLong(6, item.containerId ?: 0)
            stmt.setLong(7, item.shelfId ?: 0)
            stmt.setLong(8, item.placeId ?: 0)
            stmt.setString(9, item.photoPath)
            stmt.setInt(10, if (item.pinned) 1 else 0)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) rs.getLong(1) else 0L
        } finally {
            conn.close()
        }
    }
    
    override suspend fun update(item: Item): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement(
                """UPDATE items SET name = ?, quantity = ?, unit = ?, category = ?, notes = ?, container_id = ?, shelf_id = ?, place_id = ?, photo_path = ?, pinned = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"""
            ).apply {
                setString(1, item.name)
                setInt(2, item.quantity)
                setString(3, item.unit)
                setString(4, item.category)
                setString(5, item.notes)
                setLong(6, item.containerId ?: 0)
                setLong(7, item.shelfId ?: 0)
                setLong(8, item.placeId ?: 0)
                setString(9, item.photoPath)
                setInt(10, if (item.pinned) 1 else 0)
                setLong(11, item.id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun getById(id: Long): Item? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM items WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()
            if (rs.next()) toItem(rs)
        } finally {
            conn.close()
        }
    }
    
    override suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.prepareStatement("DELETE FROM items WHERE id = ?").apply {
                setLong(1, id)
            }.executeUpdate()
        } finally {
            conn.close()
        }
    }
    
    override suspend fun search(query: String): StateFlow<List<Item>> = MutableStateFlow(emptyList())
    
    override suspend fun observeAll(): StateFlow<List<Item>> = MutableStateFlow(emptyList())
    
    override suspend fun categories(): List<String> = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT DISTINCT category FROM items").executeQuery()
            val categories = mutableListOf<String>()
            while (rs.next()) categories.add(rs.getString("category"))
            categories
        } finally {
            conn.close()
        }
    }
    
    override suspend fun atomicallyAdjustQuantity(id: Long, delta: Int, updatedAt: Long): Int = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            conn.setAutoCommit(false)
            try {
                val result = conn.prepareStatement(
                    """UPDATE items SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"""
                ).apply {
                    setInt(1, delta)
                    setLong(2, id)
                }.executeUpdate()
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.setAutoCommit(true)
            }
        } finally {
            conn.close()
        }
    }
    
    override suspend fun findByName(name: String): Item? = withContext(Dispatchers.IO) {
        val conn = db.getConn()
        try {
            val rs = conn.prepareStatement("SELECT * FROM items WHERE name LIKE ?").apply {
                setString(1, "%$name%")
            }.executeQuery()
            if (rs.next()) toItem(rs)
        } finally {
            conn.close()
        }
    }
}

private fun toItem(rs: ResultSet): Item {
    val id = rs.getLong("id")
    val name = rs.getString("name")
    val quantity = rs.getInt("quantity")
    val unit = rs.getString("unit")
    val category = rs.getString("category")
    val notes = rs.getString("notes")
    val containerId = rs.getLong("container_id")
    val shelfId = rs.getLong("shelf_id")
    val placeId = rs.getLong("place_id")
    val photoPath = rs.getString("photo_path")
    val pinned = rs.getInt("pinned")
    return Item(
        id = id,
        name = name,
        quantity = quantity,
        unit = unit,
        category = category,
        notes = notes,
        containerId = if (containerId != 0L) containerId else null,
        shelfId = if (shelfId != 0L) shelfId else null,
        placeId = if (placeId != 0L) placeId else null,
        photoPath = photoPath,
        pinned = pinned == 1
    )
}

private fun AppDatabaseImpl.getConn() = DriverManager.getConnection("jdbc:sqlite:$path")
