package ru.kladovka.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: Long): Place?

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE shelves SET placeId = NULL WHERE placeId = :id")
    suspend fun detachShelves(id: Long)

    @Query("UPDATE polki SET placeId = NULL WHERE placeId = :id")
    suspend fun detachPolki(id: Long)

    @Query("UPDATE containers SET placeId = NULL WHERE placeId = :id")
    suspend fun detachContainers(id: Long)

    @Query("UPDATE items SET placeId = NULL WHERE placeId = :id")
    suspend fun detachItems(id: Long)
}

@Dao
interface ShelfDao {

    @Query("SELECT * FROM shelves ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Shelf>>

    @Query("SELECT * FROM shelves WHERE id = :id")
    suspend fun getById(id: Long): Shelf?

    @Insert
    suspend fun insert(shelf: Shelf): Long

    @Update
    suspend fun update(shelf: Shelf)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE polki SET shelfId = NULL WHERE shelfId = :id")
    suspend fun detachPolki(id: Long)

    @Query("UPDATE containers SET shelfId = NULL WHERE shelfId = :id")
    suspend fun detachContainers(id: Long)

    @Query("UPDATE items SET shelfId = NULL WHERE shelfId = :id")
    suspend fun detachItems(id: Long)
}

@Dao
interface PolkaDao {

    @Query("SELECT * FROM polki ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Polka>>

    @Query("SELECT * FROM polki WHERE id = :id")
    suspend fun getById(id: Long): Polka?

    @Insert
    suspend fun insert(polka: Polka): Long

    @Update
    suspend fun update(polka: Polka)

    @Query("DELETE FROM polki WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ContainerDao {

    @Query("SELECT * FROM containers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Container>>

    @Query("SELECT * FROM containers WHERE id = :id")
    suspend fun getById(id: Long): Container?

    @Insert
    suspend fun insert(container: Container): Long

    @Update
    suspend fun update(container: Container)

    @Query("DELETE FROM containers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE items SET containerId = NULL WHERE containerId = :id")
    suspend fun detachItems(id: Long)
}

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE, updatedAt DESC")
    fun observeAll(): Flow<List<Item>>

    @Query(
        "SELECT * FROM items WHERE name LIKE '%' || :q || '%' " +
            "OR category LIKE '%' || :q || '%' " +
            "OR notes LIKE '%' || :q || '%' " +
            "ORDER BY name COLLATE NOCASE, updatedAt DESC"
    )
    fun search(q: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query(
        "UPDATE items SET " +
            "quantity = MAX(1, quantity + :delta), " +
            "updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun atomicallyAdjustQuantity(id: Long, delta: Int, now: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT category FROM items WHERE category <> '' ORDER BY category COLLATE NOCASE")
    suspend fun categories(): List<String>
}