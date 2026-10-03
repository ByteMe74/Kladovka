package ru.kladovka.data

/**
 * Интерфейс для работы с базой данных
 */
interface Database {
    // Получение всех объектов
    fun getAllItems(): List<Item>
    fun getAllContainers(): List<Container>
    fun getAllShelves(): List<Shelf>
    fun getAllPolki(): List<Polka>
    fun getAllPlaces(): List<Place>
    
    // Операции с Place
    suspend fun insertPlace(place: Place)
    suspend fun updatePlace(place: Place)
    suspend fun deletePlace(id: Long)
    
    // Операции с Shelf
    suspend fun insertShelf(shelf: Shelf)
    suspend fun updateShelf(shelf: Shelf)
    suspend fun deleteShelf(id: Long)
    
    // Операции с Polka
    suspend fun insertPolka(polka: Polka)
    suspend fun updatePolka(polka: Polka)
    suspend fun deletePolka(id: Long)
    
    // Операции с Container
    suspend fun insertContainer(container: Container)
    suspend fun updateContainer(container: Container)
    suspend fun deleteContainer(id: Long)
    
    // Операции с Item
    suspend fun insertItem(item: Item)
    suspend fun updateItem(item: Item)
    suspend fun deleteItem(id: Long)
    suspend fun getItemById(id: Long): Item?
    suspend fun updateItemQuantity(id: Long, quantity: Int)
    suspend fun setItemPinned(id: Long, pinned: Boolean)
    suspend fun searchItems(query: String): List<Item>
    suspend fun clearAll()
}

/**
 * В-memory реализация базы данных
 */
class InMemoryDatabase : Database {
    private val items = mutableListOf<Item>()
    private val containers = mutableListOf<Container>()
    private val shelves = mutableListOf<Shelf>()
    private val polki = mutableListOf<Polka>()
    private val places = mutableListOf<Place>()
    
    override fun getAllItems(): List<Item> = items.toList()
    override fun getAllContainers(): List<Container> = containers.toList()
    override fun getAllShelves(): List<Shelf> = shelves.toList()
    override fun getAllPolki(): List<Polka> = polki.toList()
    override fun getAllPlaces(): List<Place> = places.toList()
    
    override suspend fun insertPlace(place: Place) {
        if (place.id == 0L) {
            places.add(place)
        } else {
            val idx = places.indexOfFirst { it.id == place.id }
            if (idx >= 0) {
                places[idx] = place
            }
        }
    }
    
    override suspend fun updatePlace(place: Place) {
        val idx = places.indexOfFirst { it.id == place.id }
        if (idx >= 0) {
            places[idx] = place
        }
    }
    
    override suspend fun deletePlace(id: Long) {
        places.removeAll { it.id == id }
    }
    
    override suspend fun insertShelf(shelf: Shelf) {
        if (shelf.id == 0L) {
            shelves.add(shelf)
        } else {
            val idx = shelves.indexOfFirst { it.id == shelf.id }
            if (idx >= 0) {
                shelves[idx] = shelf
            }
        }
    }
    
    override suspend fun updateShelf(shelf: Shelf) {
        val idx = shelves.indexOfFirst { it.id == shelf.id }
        if (idx >= 0) {
            shelves[idx] = shelf
        }
    }
    
    override suspend fun deleteShelf(id: Long) {
        shelves.removeAll { it.id == id }
    }
    
    override suspend fun insertPolka(polka: Polka) {
        if (polka.id == 0L) {
            polki.add(polka)
        } else {
            val idx = polki.indexOfFirst { it.id == polka.id }
            if (idx >= 0) {
                polki[idx] = polka
            }
        }
    }
    
    override suspend fun updatePolka(polka: Polka) {
        val idx = polki.indexOfFirst { it.id == polka.id }
        if (idx >= 0) {
            polki[idx] = polka
        }
    }
    
    override suspend fun deletePolka(id: Long) {
        polki.removeAll { it.id == id }
    }
    
    override suspend fun insertContainer(container: Container) {
        if (container.id == 0L) {
            containers.add(container)
        } else {
            val idx = containers.indexOfFirst { it.id == container.id }
            if (idx >= 0) {
                containers[idx] = container
            }
        }
    }
    
    override suspend fun updateContainer(container: Container) {
        val idx = containers.indexOfFirst { it.id == container.id }
        if (idx >= 0) {
            containers[idx] = container
        }
    }
    
    override suspend fun deleteContainer(id: Long) {
        containers.removeAll { it.id == id }
    }
    
    override suspend fun insertItem(item: Item) {
        if (item.id == 0L) {
            items.add(item)
        } else {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                items[idx] = item
            }
        }
    }
    
    override suspend fun updateItem(item: Item) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            items[idx] = item
        }
    }
    
    override suspend fun deleteItem(id: Long) {
        items.removeAll { it.id == id }
    }
    
    override suspend fun getItemById(id: Long): Item? {
        return items.firstOrNull { it.id == id }
    }
    
    override suspend fun updateItemQuantity(id: Long, quantity: Int) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(quantity = quantity)
        }
    }
    
    override suspend fun setItemPinned(id: Long, pinned: Boolean) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(pinned = pinned)
        }
    }
    
    override suspend fun searchItems(query: String): List<Item> {
        return items.filter { 
            it.name.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true) ||
            it.notes.contains(query, ignoreCase = true)
        }
    }
    
    override suspend fun clearAll() {
        items.clear()
        containers.clear()
        shelves.clear()
        polki.clear()
        places.clear()
    }
}
