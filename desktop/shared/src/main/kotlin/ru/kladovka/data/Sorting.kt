package ru.kladovka.data

import java.util.Locale

/**
 * Порядок списка вещей — те же четыре варианта, что в меню Android-приложения.
 *
 * Живёт в общем модуле рядом с сущностями, а не в UI: сортировка не зависит от
 * Compose и должна проверяться тестами вместе с остальными правилами данных.
 */
enum class SortMode(val title: String) {
    NAME("По имени"),
    QUANTITY("По количеству"),
    CATEGORY("По категории"),
    UPDATED("Сначала изменённые")
}

/**
 * Сортирует вещи с сохранением правила «закреплённые всегда сверху».
 *
 * Правило действует во всех режимах — иначе закрепление перестало бы работать
 * при первом же переключении сортировки, как и на Android.
 */
fun List<Item>.sortedFor(mode: SortMode): List<Item> {
    val byName = { i: Item -> i.name.lowercase(Locale.getDefault()) }
    val pinned = compareByDescending<Item> { it.pinned }
    return when (mode) {
        SortMode.NAME -> sortedWith(pinned.thenBy(byName))
        SortMode.QUANTITY -> sortedWith(pinned.thenBy { it.quantity }.thenBy(byName))
        SortMode.CATEGORY -> sortedWith(
            pinned.thenBy { it.category.lowercase(Locale.getDefault()) }.thenBy(byName)
        )
        SortMode.UPDATED -> sortedWith(pinned.thenByDescending { it.updatedAt }.thenBy(byName))
    }
}