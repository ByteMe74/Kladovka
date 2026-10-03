package ru.kladovka.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Репозиторий для работы с данными через Database
 */
class Repository(private val db: Database) {
    
    private val _items = MutableStateFlow(emptyList<Item>())
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    
    private val _containers = MutableStateFlow(emptyList<Container>())
    val containers: StateFlow<List<Container>> = _containers.asStateFlow()
    
    private val _shelves = MutableStateFlow(emptyList<Shelf>())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()
    
    private val _polki = MutableStateFlow(emptyList<Polka>())
    val polki: StateFlow<List<Polka>> = _polki.asStateFlow()
    
    private val _places = MutableStateFlow(emptyList<Place>())
    val places: StateFlow<List<Place>> = _places.asStateFlow()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        refresh()
    }
    
    fun refresh() {
        _items.value = db.getAllItems()
        _containers.value = db.getAllContainers()
        _shelves.value = db.getAllShelves()
        _polki.value = db.getAllPolki()
        _places.value = db.getAllPlaces()
    }
    
    suspend fun upsertPlace(place: Place) {
        if (place.id == 0L) {
            db.insertPlace(place)
            _places.value = _places.value + place
        } else {
            val idx = _places.value.indexOfFirst { it.id == place.id }
            if (idx >= 0) {
                val updated = _places.value.toMutableList()
                updated[idx] = place
                _places.value = updated
            }
        }
    }
    
    suspend fun deletePlace(id: Long) {
        db.deletePlace(id)
        _places.value = _places.value.filter { it.id != id }
    }
    
    suspend fun upsertShelf(shelf: Shelf) {
        if (shelf.id == 0L) {
            db.insertShelf(shelf)
            _shelves.value = _shelves.value + shelf
        } else {
            val idx = _shelves.value.indexOfFirst { it.id == shelf.id }
            if (idx >= 0) {
                val updated = _shelves.value.toMutableList()
                updated[idx] = shelf
                _shelves.value = updated
            }
        }
    }
    
    suspend fun deleteShelf(id: Long) {
        db.deleteShelf(id)
        _shelves.value = _shelves.value.filter { it.id != id }
    }
    
    suspend fun upsertPolka(polka: Polka) {
        if (polka.id == 0L) {
            db.insertPolka(polka)
            _polki.value = _polki.value + polka
        } else {
            val idx = _polki.value.indexOfFirst { it.id == polka.id }
            if (idx >= 0) {
                val updated = _polki.value.toMutableList()
                updated[idx] = polka
                _polki.value = updated
            }
        }
    }
    
    suspend fun deletePolka(id: Long) {
        db.deletePolka(id)
        _polki.value = _polki.value.filter { it.id != id }
    }
    
    suspend fun upsertContainer(container: Container) {
        if (container.id == 0L) {
            db.insertContainer(container)
            _containers.value = _containers.value + container
        } else {
            val idx = _containers.value.indexOfFirst { it.id == container.id }
            if (idx >= 0) {
                val updated = _containers.value.toMutableList()
                updated[idx] = container
                _containers.value = updated
            }
        }
    }
    
    suspend fun deleteContainer(id: Long) {
        db.deleteContainer(id)
        _containers.value = _containers.value.filter { it.id != id }
    }
    
    suspend fun upsertItem(item: Item) {
        if (item.id == 0L) {
            db.insertItem(item)
            _items.value = _items.value + item
        } else {
            val idx = _items.value.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                val updated = _items.value.toMutableList()
                updated[idx] = item
                _items.value = updated
            }
        }
    }
    
    suspend fun updateItemQuantity(id: Long, quantity: Int) {
        db.updateItemQuantity(id, quantity)
        val idx = _items.value.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val updated = _items.value.toMutableList()
            updated[idx] = updated[idx].copy(quantity = quantity)
            _items.value = updated
        }
    }
    
    suspend fun setItemPinned(id: Long, pinned: Boolean) {
        db.setItemPinned(id, pinned)
        val idx = _items.value.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val updated = _items.value.toMutableList()
            updated[idx] = updated[idx].copy(pinned = pinned)
            _items.value = updated
        }
    }
    
    suspend fun deleteItem(id: Long) {
        db.deleteItem(id)
        _items.value = _items.value.filter { it.id != id }
    }
    
    suspend fun searchItems(query: String): List<Item> = db.searchItems(query)
    
    suspend fun clearAll() {
        db.clearAll()
        _items.value = emptyList()
        _containers.value = emptyList()
        _shelves.value = emptyList()
        _polki.value = emptyList()
        _places.value = emptyList()
    }
}
