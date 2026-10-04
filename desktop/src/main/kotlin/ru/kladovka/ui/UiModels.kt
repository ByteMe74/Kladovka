package ru.kladovka.ui

import ru.kladovka.data.AppData
import ru.kladovka.data.Container
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Polka
import ru.kladovka.data.Shelf
import java.util.Locale

/**
 * Вспомогательные функции отрисовки. Перенесены из Android-приложения
 * (ui/UiModels.kt) без изменений, чтобы подписи и иерархия выглядели одинаково.
 */

private fun AppData.placeById(id: Long?): Place? =
    id?.let { pid -> places.firstOrNull { it.id == pid } }

fun AppData.placeName(id: Long?): String? = placeById(id)?.name

/** Текстовое представление места вещи: «Кладовая · Ящик ёлки · С1» или «Без места». */
fun AppData.locationOf(item: Item): String {
    val container = item.containerId?.let { cid -> containers.firstOrNull { it.id == cid } }
    val shelf = if (container != null) {
        container.shelfId?.let { sid -> shelves.firstOrNull { it.id == sid } }
    } else {
        item.shelfId?.let { sid -> shelves.firstOrNull { it.id == sid } }
    }
    // Место берём от самого глубокого известного уровня.
    //
    // Раньше приоритет был у item.placeId, из-за чего подпись получалась
    // «Балкон · Ящик · Стеллаж 1» для вещи, лежащей в ящике в «Кладовой»:
    // читалось как «ящик на балконе», то есть как ошибка при верных данных.
    // Подпись — это один путь внутри одного места, и ящик на полке в кладовой
    // не может оказаться на балконе. Та же правка и в Android: UiModels и
    // Repository (CSV), иначе одно и то же место показывалось бы по-разному
    // в разных экранах одного приложения.
    val placeId = container?.placeId ?: shelf?.placeId ?: item.placeId
    val parts = mutableListOf<String>()
    placeName(placeId)?.let { parts += it }
    container?.let { parts += it.name }
    if (shelf != null) parts += shelf.name
    return if (parts.isEmpty()) "Без места" else parts.joinToString(" · ")
}

/** Подпись места под названием в карточках. */
fun AppData.placeContextOf(placeId: Long?): String? = placeName(placeId)

fun qtyText(item: Item): String {
    val unit = item.unit.trim()
    return if (unit.isEmpty()) item.quantity.toString() else "${item.quantity} $unit"
}

fun coordsText(place: Place): String? {
    if (place.latitude != null && place.longitude != null) {
        return String.format(Locale.US, "%.5f, %.5f", place.latitude, place.longitude)
    }
    return null
}

/** Модель для отрисовки иерархии на вкладке «Места». */
data class ContainerUi(val container: Container, val items: List<Item>)
data class ShelfUi(val shelf: Shelf, val direct: List<Item>, val containers: List<ContainerUi>)
data class PolkaUi(val polka: Polka, val onShelf: Shelf?)
data class PlacesUi(
    val unplaced: List<Item>,
    val shelves: List<ShelfUi>,
    val polki: List<PolkaUi>,
    val looseContainers: List<ContainerUi>
)

fun buildPlaces(data: AppData): PlacesUi {
    val itemsOnShelf = data.items.filter { it.containerId == null && it.shelfId != null }

    val looseContainers = data.containers
        .filter { it.shelfId == null }
        .map { c -> ContainerUi(c, data.items.filter { it.containerId == c.id }) }

    val shelves = data.shelves.map { shelf ->
        val shelfContainers = data.containers
            .filter { it.shelfId == shelf.id }
            .map { c -> ContainerUi(c, data.items.filter { it.containerId == c.id }) }
        val direct = itemsOnShelf.filter { it.shelfId == shelf.id }
        ShelfUi(shelf, direct, shelfContainers)
    }

    val polki = data.polki.map { p ->
        PolkaUi(p, p.shelfId?.let { sid -> data.shelves.firstOrNull { it.id == sid } })
    }

    return PlacesUi(
        unplaced = data.items.filter { it.containerId == null && it.shelfId == null },
        shelves = shelves,
        polki = polki,
        looseContainers = looseContainers
    )
}

/** Закреплённые вещи идут первыми — как на Android. */
fun List<Item>.sortedForList(): List<Item> =
    sortedWith(compareByDescending<Item> { it.pinned }.thenBy { it.name.lowercase(Locale.getDefault()) })
