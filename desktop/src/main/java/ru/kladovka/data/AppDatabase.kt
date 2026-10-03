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

abstract class AppDatabase {
    abstract fun placeDao(): PlaceDao
    abstract fun shelfDao(): ShelfDao
    abstract fun polkaDao(): PolkaDao
    abstract fun containerDao(): ContainerDao
    abstract fun itemDao(): ItemDao

    companion object {
        private var instance: AppDatabase? = null
        private var dbPath: String = ""

        fun setDatabasePath(path: Path) {
            dbPath = path.toString()
        }

        fun get(): AppDatabase = instance ?: synchronized(Companion::class.java) {
            instance ?: throw IllegalStateException("Database not initialized. Call setDatabasePath() first.")
        }
    }
}

class AppDatabaseImpl(private val db: AppDatabase) : AppDatabase by db

open class PlaceDao {
    abstract suspend fun insert(place: Place): Long
    abstract suspend fun update(place: Place): Int
    abstract suspend fun getById(id: Long): Place?
    abstract suspend fun deleteById(id: Long): Int
    abstract suspend fun observeAll(): StateFlow<List<Place>>
    abstract suspend fun detachShelves(placeId: Long)
    abstract suspend fun detachPolki(placeId: Long)
    abstract suspend fun detachContainers(placeId: Long)
    abstract suspend fun detachItems(placeId: Long)
    abstract suspend fun findByName(name: String): Place?
}

open class ShelfDao {
    abstract suspend fun insert(shelf: Shelf): Long
    abstract suspend fun update(shelf: Shelf): Int
    abstract suspend fun getById(id: Long): Shelf?
    abstract suspend fun deleteById(id: Long): Int
    abstract suspend fun observeAll(): StateFlow<List<Shelf>>
    abstract suspend fun detachPolki(shelfId: Long)
    abstract suspend fun detachContainers(shelfId: Long)
    abstract suspend fun detachItems(shelfId: Long)
    abstract suspend fun findByName(name: String): Shelf?
}

open class PolkaDao {
    abstract suspend fun insert(polka: Polka): Long
    abstract suspend fun update(polka: Polka): Int
    abstract suspend fun getById(id: Long): Polka?
    abstract suspend fun deleteById(id: Long): Int
    abstract suspend fun observeAll(): StateFlow<List<Polka>>
    abstract suspend fun findByName(name: String): Polka?
}

open class ContainerDao {
    abstract suspend fun insert(container: Container): Long
    abstract suspend fun update(container: Container): Int
    abstract suspend fun getById(id: Long): Container?
    abstract suspend fun deleteById(id: Long): Int
    abstract suspend fun observeAll(): StateFlow<List<Container>>
    abstract suspend fun detachItems(containerId: Long)
    abstract suspend fun findByName(name: String): Container?
}

open class ItemDao {
    abstract suspend fun insert(item: Item): Long
    abstract suspend fun update(item: Item): Int
    abstract suspend fun getById(id: Long): Item?
    abstract suspend fun deleteById(id: Long): Int
    abstract suspend fun search(query: String): StateFlow<List<Item>>
    abstract suspend fun observeAll(): StateFlow<List<Item>>
    abstract suspend fun categories(): List<String>
    abstract suspend fun atomicallyAdjustQuantity(id: Long, delta: Int, updatedAt: Long): Int
    abstract suspend fun findByName(name: String): Item?
}
