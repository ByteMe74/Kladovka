package ru.kladovka.ui

import ru.kladovka.data.*

data class AppData(
    val items: List<Item> = emptyList(),
    val containers: List<Container> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val polki: List<Polka> = emptyList(),
    val places: List<Place> = emptyList()
)
