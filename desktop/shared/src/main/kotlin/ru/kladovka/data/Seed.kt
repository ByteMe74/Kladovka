package ru.kladovka.data

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Наполнение базы демонстрационными данными.
 *
 * Служебная утилита для разработки: `./gradlew :shared:seed`.
 * Идёт через тот же [SqliteDatabase], что и приложение, поэтому одновременно
 * служит сквозной проверкой слоя данных — и наглядным материалом для скриншотов
 * и ручной проверки интерфейса на настоящих данных, а не на пустых списках.
 */
object Seed {

    fun seed(db: SqliteDatabase) {
        if (!db.data.value.isEmpty) return // не трогаем непустую базу

        val garage = db.savePlace(Place(name = "Гараж", latitude = 55.7512, longitude = 37.6184, notes = "Дальний правый бокс"))
        val pantry = db.savePlace(Place(name = "Кладовка"))
        val balcony = db.savePlace(Place(name = "Балкон", latitude = 55.7402, longitude = 37.6471, notes = "Только подтверждённые вещи"))

        val shelf = db.saveShelf(Shelf(name = "Стеллаж А", placeId = garage))
        val shelf2 = db.saveShelf(Shelf(name = "Стеллаж Б", placeId = pantry))

        db.savePolka(Polka(name = "Верхняя полка", shelfId = shelf, placeId = garage, notes = "Гвозди и шурупы"))
        db.savePolka(Polka(name = "Средняя полка", shelfId = shelf, placeId = garage))

        val box = db.saveContainer(Container(name = "Ящик с инструментом", shelfId = shelf, placeId = garage))
        val box2 = db.saveContainer(Container(name = "Коробка ёлочных игрушек", shelfId = shelf2, placeId = pantry))
        // У контейнера нет поля notes — так устроена и схема Room на Android.
        db.saveContainer(Container(name = "Канистры", placeId = balcony))

        db.saveItem(Item(
            name = "Дрель-шуруповёрт", quantity = 1, unit = "шт", category = "Инструменты",
            notes = "Заряд батареи почти сел", containerId = box, pinned = true
        ))
        db.saveItem(Item(
            name = "Набор ключей", quantity = 1, unit = "набор", category = "Инструменты",
            notes = "8–19 мм", containerId = box
        ))
        db.saveItem(Item(
            name = "Шурупы 4×40", quantity = 240, unit = "шт", category = "Крепёж",
            notes = "Цинковые, в пакете", shelfId = shelf
        ))
        db.saveItem(Item(
            name = "Гвозди 3×70", quantity = 500, unit = "шт", category = "Крепёж",
            shelfId = shelf, placeId = garage
        ))
        db.saveItem(Item(
            name = "Ёлочные игрушки", quantity = 34, unit = "шт", category = "Новый год",
            containerId = box2
        ))
        db.saveItem(Item(
            name = "Гирлянда", quantity = 3, unit = "шт", category = "Новый год",
            containerId = box2, pinned = true
        ))
        db.saveItem(Item(
            name = "Канистра 20 л", quantity = 2, unit = "шт", category = "Жидкости",
            placeId = balcony, notes = "Одна пустая"
        ))
        db.saveItem(Item(
            name = "Аптечка", quantity = 1, unit = "набор", category = "Безопасность",
            notes = "Проверить срок годности лекарств", placeId = pantry, pinned = true
        ))
        db.saveItem(Item(
            name = "Удобрение для растений", quantity = 1, unit = "уп.", category = "Дом",
            placeId = balcony
        ))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir: Path = if (args.isNotEmpty()) Paths.get(args[0]) else Paths.get(System.getProperty("user.home"), ".kladovka")
        val db = SqliteDatabase(dir.resolve("kladovka.db"))
        seed(db)
        val d = db.data.value
        println("Засеяно: мест=${d.places.size}, стеллажей=${d.shelves.size}, полок=${d.polki.size}, " +
            "контейнеров=${d.containers.size}, вещей=${d.items.size}")
        db.close()
    }
}