package ru.kladovka.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Место: «Кладовая», «Балкон», «Гараж» — с опциональной геолокацией. */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = ""
)

/** Стеллаж: крупная конструкция внутри места (v1 называлась «полка»). */
@Entity(tableName = "shelves")
data class Shelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Опциональная привязка к месту. */
    val placeId: Long? = null,
    /** Устаревшее текстовое место (v1); больше не используется в UI. */
    val location: String = ""
)

/** Полка (polka) — горизонтальная поверхность. Может стоять на стеллаже и/или в месте. */
@Entity(tableName = "polki")
data class Polka(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Опциональная привязка к стеллажу (shelves.id). */
    val shelfId: Long? = null,
    /** Опциональная привязка к месту. */
    val placeId: Long? = null
)

/** Контейнер (ящик, коробка, контейнер). Может стоять на полке или отдельно. */
@Entity(tableName = "containers")
data class Container(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shelfId: Long? = null,
    /** Опциональная привязка к месту. */
    val placeId: Long? = null,
    /** Устаревшее текстовое место (v1); больше не используется в UI. */
    val location: String = ""
)

/** Вещь. Лежит в контейнере, прямо на полке или вообще без места. */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)