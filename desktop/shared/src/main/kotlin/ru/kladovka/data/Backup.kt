package ru.kladovka.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Бэкап в формате Android-приложения.
 *
 * Формат намеренно совпадает с тем, что пишет `Repository.exportJson()` на телефоне:
 * корень `{"app":"kladovka","exportedAt":<ms>}` плюс пять массивов. Благодаря этому
 * один и тот же файл годится и для телефона, и для компьютера.
 */
object Backup {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun export(d: AppData, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JsonObject(
            mapOf(
                "app" to JsonPrimitive("kladovka"),
                "exportedAt" to JsonPrimitive(exportedAt),
                "places" to JsonArray(d.places.map { p ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(p.id),
                            "name" to JsonPrimitive(p.name),
                            "latitude" to (p.latitude?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "longitude" to (p.longitude?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "notes" to JsonPrimitive(p.notes)
                        )
                    )
                }),
                "shelves" to JsonArray(d.shelves.map { s ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(s.id),
                            "name" to JsonPrimitive(s.name),
                            "notes" to JsonPrimitive(s.notes),
                            "placeId" to (s.placeId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "location" to JsonPrimitive(s.location)
                        )
                    )
                }),
                "polki" to JsonArray(d.polki.map { p ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(p.id),
                            "name" to JsonPrimitive(p.name),
                            "notes" to JsonPrimitive(p.notes),
                            "shelfId" to (p.shelfId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "placeId" to (p.placeId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null))
                        )
                    )
                }),
                "containers" to JsonArray(d.containers.map { c ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(c.id),
                            "name" to JsonPrimitive(c.name),
                            "shelfId" to (c.shelfId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "placeId" to (c.placeId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "location" to JsonPrimitive(c.location)
                        )
                    )
                }),
                "items" to JsonArray(d.items.map { i ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(i.id),
                            "name" to JsonPrimitive(i.name),
                            "quantity" to JsonPrimitive(i.quantity),
                            "unit" to JsonPrimitive(i.unit),
                            "category" to JsonPrimitive(i.category),
                            "notes" to JsonPrimitive(i.notes),
                            "containerId" to (i.containerId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "shelfId" to (i.shelfId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "placeId" to (i.placeId?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "photoPath" to (i.photoPath?.let { JsonPrimitive(it) } ?: JsonPrimitive(null)),
                            "pinned" to JsonPrimitive(if (i.pinned) 1 else 0),
                            "createdAt" to JsonPrimitive(i.createdAt),
                            "updatedAt" to JsonPrimitive(i.updatedAt)
                        )
                    )
                })
            )
        )
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Разбирает бэкап. Бросает исключение, если это не наш формат. */
    fun parse(text: String): AppData {
        val root = json.parseToJsonElement(text).jsonObject
        val marker = (root["app"] as? JsonPrimitive)?.contentOrNullSafe()
        require(marker == "kladovka") { "Это не бэкап Кладовки" }

        fun <T> arr(key: String, f: (JsonObject) -> T): List<T> =
            (root[key] as? JsonArray)?.map { f(it.jsonObject) } ?: emptyList()

        return AppData(
            places = arr("places") { o ->
                Place(
                    id = o.long("id"), name = o.str("name"),
                    latitude = o.dbl("latitude"), longitude = o.dbl("longitude"),
                    notes = o.str("notes")
                )
            },
            shelves = arr("shelves") { o ->
                Shelf(
                    id = o.long("id"), name = o.str("name"), notes = o.str("notes"),
                    placeId = o.longOrNull("placeId"), location = o.str("location")
                )
            },
            polki = arr("polki") { o ->
                Polka(
                    id = o.long("id"), name = o.str("name"), notes = o.str("notes"),
                    shelfId = o.longOrNull("shelfId"), placeId = o.longOrNull("placeId")
                )
            },
            containers = arr("containers") { o ->
                Container(
                    id = o.long("id"), name = o.str("name"),
                    shelfId = o.longOrNull("shelfId"), placeId = o.longOrNull("placeId"),
                    location = o.str("location")
                )
            },
            items = arr("items") { o ->
                Item(
                    id = o.long("id"), name = o.str("name"),
                    quantity = o.long("quantity").toInt(), unit = o.str("unit"),
                    category = o.str("category"), notes = o.str("notes"),
                    containerId = o.longOrNull("containerId"), shelfId = o.longOrNull("shelfId"),
                    placeId = o.longOrNull("placeId"), photoPath = o.strOrNull("photoPath"),
                    pinned = o.long("pinned") == 1L,
                    createdAt = o.long("createdAt"), updatedAt = o.long("updatedAt")
                )
            }
        )
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    /** Строка; отсутствующее или JSON-null значение трактуется как пустая строка. */
    private fun JsonObject.str(key: String): String = strOrNull(key) ?: ""

    private fun JsonObject.strOrNull(key: String): String? =
        prim(key)?.takeIf { it !is JsonNull }?.content

    /** Число; отсутствующее или JSON-null значение — null. */
    private fun JsonObject.longOrNull(key: String): Long? =
        strOrNull(key)?.trim()?.toLongOrNull()

    private fun JsonObject.long(key: String): Long = longOrNull(key) ?: 0L

    private fun JsonObject.dbl(key: String): Double? =
        strOrNull(key)?.trim()?.toDoubleOrNull()
}
