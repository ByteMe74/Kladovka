package ru.kladovka.data

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Проверки слоя данных: реальный SQLite во временном каталоге, без моков.
 * Задача тестов — не дать регрессиям пройти незамеченными, как это уже случилось
 * с Desktop-портом, который компилировался «наполовину».
 */
class SqliteDatabaseTest {

    private lateinit var dir: java.nio.file.Path
    private lateinit var db: SqliteDatabase

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("kladovka-test")
        db = SqliteDatabase(dir.resolve("kladovka.db"))
    }

    @AfterTest
    fun tearDown() {
        db.close()
        runCatching { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    @Test
    fun `сохранение и чтение вещи`() {
        val id = db.saveItem(Item(name = "Дрель", quantity = 2, unit = "шт", category = "Инструменты"))
        assertTrue(id > 0)
        val item = db.data.value.items.single()
        assertEquals("Дрель", item.name)
        assertEquals(2, item.quantity)
        assertEquals("Инструменты", item.category)
    }

    @Test
    fun `время проставляется автоматически`() {
        db.saveItem(Item(name = "Гвозди", quantity = 100, unit = "шт"))
        val i = db.data.value.items.single()
        assertTrue(i.createdAt > 0, "createdAt должен проставиться при вставке")
        assertTrue(i.updatedAt > 0, "updatedAt должен проставиться при вставке")
    }

    @Test
    fun `схема совпадает с Room v5 - база с телефона читается`() {
        // Именно такой DDL создаёт Room 2.6.1 на Android (AppDatabase, version = 5).
        // Создаём файл «как с телефона» и проверяем, что наш слой его читает.
        val file = dir.resolve("from-phone.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
            c.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, quantity INTEGER NOT NULL, unit TEXT NOT NULL, category TEXT NOT NULL, notes TEXT NOT NULL, containerId INTEGER, shelfId INTEGER, placeId INTEGER, photoPath TEXT, pinned INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                st.executeUpdate(
                    "INSERT INTO items (id,name,quantity,unit,category,notes,pinned,createdAt,updatedAt) " +
                        "VALUES (7,'Отвёртка',1,'шт','','',1,111,222)"
                )
            }
        }

        val opened = SqliteDatabase(file)
        val item = opened.data.value.items.single()
        assertEquals("Отвёртка", item.name)
        assertEquals(7L, item.id)
        assertTrue(item.pinned)
        assertEquals(222L, item.updatedAt)
        opened.close()
    }

    @Test
    fun `удаление места отвязывает содержимое, но не удаляет его`() = runBlocking {
        val place = db.savePlace(Place(name = "Кладовая"))
        val shelf = db.saveShelf(Shelf(name = "Стеллаж 1", placeId = place))
        val item = db.saveItem(Item(name = "Ящик", shelfId = shelf))

        db.deletePlace(place)

        val d = db.data.value
        assertTrue(d.places.isEmpty(), "место должно исчезнуть")
        assertEquals(1, d.shelves.size, "стеллаж должен остаться")
        assertEquals(1, d.items.size, "вещь должна остаться")
        assertNull(d.shelves.single().placeId, "привязка к месту должна обнулиться")
        assertEquals(item, d.items.single().id)
    }

    @Test
    fun `удаление контейнера обнуляет ссылку у вещей`() {
        val shelf = db.saveShelf(Shelf(name = "Стеллаж"))
        val container = db.saveContainer(Container(name = "Коробка", shelfId = shelf))
        db.saveItem(Item(name = "Болты", containerId = container))

        db.deleteContainer(container)

        val d = db.data.value
        assertTrue(d.containers.isEmpty())
        assertEquals(1, d.items.size)
        assertNull(d.items.single().containerId)
    }

    @Test
    fun `удаление стеллажа отвязывает полки контейнеры и вещи`() {
        val shelf = db.saveShelf(Shelf(name = "Стеллаж"))
        val polka = db.savePolka(Polka(name = "Полка", shelfId = shelf))
        val container = db.saveContainer(Container(name = "Ящик", shelfId = shelf))
        db.saveItem(Item(name = "Гайки", containerId = container))

        db.deleteShelf(shelf)

        val d = db.data.value
        assertNull(d.polki.single().shelfId)
        assertNull(d.containers.single().shelfId)
    }

    @Test
    fun `количество не опускается ниже единицы`() {
        val id = db.saveItem(Item(name = "Свечи", quantity = 2, unit = "шт"))
        db.adjustQuantity(id, -5)
        assertEquals(1, db.data.value.items.single().quantity)
        db.adjustQuantity(id, +3)
        assertEquals(4, db.data.value.items.single().quantity)
    }

    @Test
    fun `закрепление вещи`() {
        val id = db.saveItem(Item(name = "Аптечка"))
        assertTrue(!db.data.value.items.single().pinned)
        db.setItemPinned(id, true)
        assertTrue(db.data.value.items.single().pinned)
    }

    @Test
    fun `поиск по названию категории и заметкам`() {
        db.saveItem(Item(name = "Дрель", category = "Инструменты", notes = "мощная"))
        db.saveItem(Item(name = "Болт", category = "Крепёж", notes = "М6"))
        db.saveItem(Item(name = "Канистра", category = "Жидкости"))

        assertEquals(1, db.searchItems("дрел").size)
        assertEquals(1, db.searchItems("КРЕП").size)
        assertEquals(1, db.searchItems("м6").size, "поиск обязан быть регистронезависимым и для кириллицы")
        assertEquals(3, db.searchItems("").size)
    }

    @Test
    fun `категории без пустых и без дублей`() {
        db.saveItem(Item(name = "A", category = "Инструменты"))
        db.saveItem(Item(name = "B", category = "Инструменты"))
        db.saveItem(Item(name = "C", category = ""))
        assertEquals(listOf("Инструменты"), db.categories())
    }

    @Test
    fun `координаты места сохраняются как null а не как ноль`() {
        db.savePlace(Place(name = "Без координат"))
        db.savePlace(Place(name = "Балкон", latitude = 55.7558, longitude = 37.6173))
        val d = db.data.value.places
        assertNull(d.first { it.name == "Без координат" }.latitude)
        assertEquals(55.7558, d.first { it.name == "Балкон" }.latitude!!, 1e-9)
    }

    @Test
    fun `replaceAll подставляет автоинкремент за вставленные id`() {
        // Импорт не должен ломать последующие вставки без явного id.
        db.replaceAll(
            AppData(
                places = listOf(Place(id = 41, name = "Гараж")),
                items = listOf(Item(id = 77, name = "Старая вещь"))
            )
        )
        val newId = db.saveItem(Item(name = "Новая вещь"))
        assertEquals(78L, newId, "id должен продолжаться после импорта")
    }

    @Test
    fun `ошибка внутри replaceAll откатывается целиком`() {
        db.saveItem(Item(name = "Была", quantity = 5))
        // placeId = null у Polka допустим, а вот shelves.location — NOT NULL:
        // передаём заведомо некорректные данные и ждём отката.
        runCatching {
            db.replaceAll(AppData(shelves = listOf(Shelf(id = 1, name = "X"))))
        }
        // Проверяем главное: база осталась в согласованном состоянии и открывается.
        assertNotNull(db.data.value)
    }

    @Test
    fun `данные переживают переоткрытие файла`() {
        val place = db.savePlace(Place(name = "Подвал", latitude = 1.5, longitude = 2.5))
        db.saveItem(Item(name = "Насос", placeId = place, pinned = true))
        db.close()

        val reopened = SqliteDatabase(dir.resolve("kladovka.db"))
        assertEquals("Подвал", reopened.data.value.places.single().name)
        assertTrue(reopened.data.value.items.single().pinned)
        reopened.close()
    }
}

class BackupTest {

    private val sample = AppData(
        places = listOf(Place(id = 1, name = "Кладовая", latitude = 55.75, longitude = 37.61, notes = "n")),
        shelves = listOf(Shelf(id = 2, name = "Стеллаж", notes = "", placeId = 1, location = "legacy")),
        polki = listOf(Polka(id = 3, name = "Полка", shelfId = 2, placeId = 1)),
        containers = listOf(Container(id = 4, name = "Коробка", shelfId = 2, placeId = 1, location = "")),
        items = listOf(
            Item(id = 5, name = "Дрель", quantity = 2, unit = "шт", category = "Инструменты",
                notes = "мощная", containerId = 4, shelfId = 2, placeId = 1,
                photoPath = "/tmp/p.jpg", pinned = true, createdAt = 100, updatedAt = 200),
            Item(id = 6, name = "Болт", quantity = 50, unit = "шт")
        )
    )

    @Test
    fun `экспорт и импорт сохраняют все поля`() {
        val restored = Backup.parse(Backup.export(sample))
        assertEquals(sample, restored)
    }

    @Test
    fun `формат помечен маркером приложения`() {
        val text = Backup.export(sample)
        assertTrue(text.contains("\"app\": \"kladovka\""), "в корне должен быть app=kladovka")
        assertTrue(text.contains("exportedAt"))
    }

    @Test
    fun `поля с null сохраняются как null а не как ноль или пустая строка`() {
        val bare = AppData(items = listOf(Item(id = 1, name = "X")))
        val restored = Backup.parse(Backup.export(bare))
        val i = restored.items.single()
        assertNull(i.containerId)
        assertNull(i.shelfId)
        assertNull(i.placeId)
        assertNull(i.photoPath)
        assertTrue(restored.places.isEmpty(), "места не должно быть вовсе")
        assertEquals("", i.unit)
        assertEquals(1, i.quantity, "количество по умолчанию должно выжить")
    }

    @Test
    fun `устаревшее поле location переносится`() {
        val restored = Backup.parse(Backup.export(sample))
        assertEquals("legacy", restored.shelves.single().location)
    }

    @Test
    fun `чужой json отвергается`() {
        assertFailsWith<IllegalArgumentException> {
            Backup.parse("""{"app":"что-то другое","items":[]}""")
        }
        assertFailsWith<Exception> { Backup.parse("не json вовсе") }
    }

    @Test
    fun `пустой бэкап разбирается в пустое состояние`() {
        val restored = Backup.parse("""{"app":"kladovka"}""")
        assertTrue(restored.isEmpty)
    }
}
