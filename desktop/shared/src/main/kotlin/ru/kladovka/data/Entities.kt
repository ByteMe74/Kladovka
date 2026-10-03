package ru.kladovka.data

data class Place(
    val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = ""
)

data class Shelf(
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    val placeId: Long? = null
)

data class Polka(
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    val shelfId: Long? = null,
    val placeId: Long? = null
)

data class Container(
    val id: Long = 0,
    val name: String,
    val shelfId: Long? = null,
    val placeId: Long? = null
)

data class Item(
    val id: Long = 0,
    val name: String,
    val quantity: Int,
    val unit: String,
    val category: String,
    val notes: String,
    val containerId: Long? = null,
    val shelfId: Long? = null,
    val placeId: Long? = null,
    val photoPath: String? = null,
    val pinned: Boolean = false
)

data class AppData(
    val items: List<Item> = emptyList(),
    val containers: List<Container> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val polki: List<Polka> = emptyList(),
    val places: List<Place> = emptyList()
)
