package ru.kladovka.data

/**
 * Сущности Кладовки.
 *
 * Схема намеренно повторяет Room-схему Android-приложения (AppDatabase, version = 5),
 * включая имена колонок и типы. Благодаря этому файл `kladovka.db`, выгруженный
 * с телефона, открывается на компьютере без конвертации — и наоборот.
 */

/** Место: «Кладовая», «Балкон», «Гараж» — с опциональной геолокацией. */
data class Place(
    val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = ""
)

/** Стеллаж: крупная конструкция внутри места (v1 называлась «полка»). */
data class Shelf(
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Опциональная привязка к месту. */
    val placeId: Long? = null,
    /** Устаревшее текстовое место (v1); в UI не показывается, но переносится при обмене. */
    val location: String = ""
)

/** Полка (polka) — горизонтальная поверхность. Может стоять на стеллаже и/или в месте. */
data class Polka(
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Опциональная привязка к стеллажу (shelves.id). */
    val shelfId: Long? = null,
    /** Опциональная привязка к месту. */
    val placeId: Long? = null
)

/** Контейнер (ящик, коробка, контейнер). Может стоять на полке или отдельно. */
data class Container(
    val id: Long = 0,
    val name: String,
    val shelfId: Long? = null,
    /** Опциональная привязка к месту. */
    val placeId: Long? = null,
    /** Устаревшее текстовое место (v1). */
    val location: String = ""
)

/** Вещь. Лежит в контейнере, прямо на полке или вообще без места. */
data class Item(
    val id: Long = 0,
    val name: String,
    val quantity: Int = 1,
    val unit: String = "",
    val category: String = "",
    val notes: String = "",
    val containerId: Long? = null,
    val shelfId: Long? = null,
    /** Опциональная привязка к месту. */
    val placeId: Long? = null,
    val photoPath: String? = null,
    /** Закреплённая (⭐) — всегда в начале списка вещей. */
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/** Сводное состояние всех данных — то, что рисует UI. */
data class AppData(
    val items: List<Item> = emptyList(),
    val containers: List<Container> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val polki: List<Polka> = emptyList(),
    val places: List<Place> = emptyList()
) {
    val isEmpty: Boolean
        get() = items.isEmpty() && containers.isEmpty() &&
            shelves.isEmpty() && polki.isEmpty() && places.isEmpty()
}

/** Результат операции обмена с сервером. */
data class SyncReport(
    val pushed: Boolean = false,
    val pulled: Boolean = false,
    val message: String = "",
    val error: String? = null
)

/** Профиль аккаунта на сервере. */
data class UserProfile(
    val id: Long = 0,
    val username: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val createdAt: Long = 0
)
