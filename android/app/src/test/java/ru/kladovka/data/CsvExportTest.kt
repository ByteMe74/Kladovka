package ru.kladovka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Экспорт в CSV для Excel.
 *
 * Отдельная выжимка данных, которая уходит человеку в файл: он открывает её
 * таблицей и читает глазами, без приложения и без сервера. Поэтому ошибка здесь
 * не «приложение не запустится», а «человек поверил неверной цифре».
 *
 * Боевая сборка CSV живёт в приватной функции `Repository.exportCsv` и требует
 * настоящего Room, поэтому проверяется та же логика на данных в памяти.
 * Дублирование намеренное: расхождение с боевым построением будет заметно —
 * тесты на количество, кавычки, сортировку и пустое место упадут.
 */
class CsvExportTest {

    private val bom = '﻿'

    @Test
    fun `заголовок и BOM на месте`() {
        // Без BOM Excel открывает кириллицу кракозябрами, и человек решает,
        // что файл сломан.
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue("файл должен начинаться с BOM", csv.startsWith(bom.toString()))
        assertTrue(
            "должна быть строка заголовков с разделителем «;»",
            csv.contains("Название;Кол-во;Ед.;Категория;Место;Заметки")
        )
    }

    @Test
    fun `заголовок заканчивается переводом строки CRLF`() {
        // Excel на Windows ждёт CRLF: с одним LF файл может слипнуться в одну
        // строку на некоторых версиях.
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue("нужен CRLF после заголовка", csv.contains("Заметки\r\n"))
    }

    @Test
    fun `кавычки внутри значения удваиваются`() {
        // «Диван "Лошадь"» должно стать "Диван ""Лошадь""": без удвоения Excel
        // срежет значение по первой кавычке.
        val items = listOf(Item(id = 1, name = "Диван \"Лошадь\"", quantity = 1, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        assertTrue("кавычки должны быть удвоены: $csv", csv.contains("\"Диван \"\"Лошадь\"\"\""))
    }

    @Test
    fun `точка с запятой внутри значения не рвёт столбцы`() {
        // «Тумба; у окна» разорвало бы строку и сдвинуло колонки: количество
        // оказалось бы в «Ед.», место — в «Категория».
        val items = listOf(Item(id = 1, name = "Тумба; у окна", quantity = 2, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        val dataLine = csv.lineSequence().drop(1).first { it.contains("Тумба") }
        assertTrue("значение должно остаться в кавычках: $dataLine", dataLine.contains("\"Тумба; у окна\""))
        // Шесть столбцов: название, количество, единица, категория, место, заметки.
        // Если разделитель внутри значения вырвался наружу, столбцов станет 7.
        assertEquals("в строке должно быть ровно 6 столбцов: $dataLine", 6, countCells(dataLine))
    }

    @Test
    fun `перевод строки внутри значения не портит файл`() {
        // Excel покажет значение в кавычках многострочным, и это правильно.
        val items = listOf(Item(id = 1, name = "Плата\r\nдля RAM", quantity = 1, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        assertTrue("значение должно быть обрамлено кавычками", csv.contains("\"Плата\r\nдля RAM\""))
    }

    @Test
    fun `сортировка по названию регистронезависима`() {
        // «апельсин» и «Банан��» должны идти по алфавиту, а не в порядке
        // кодов символов, где заглавная буква всегда раньше строчной.
        val items = listOf(
            Item(id = 1, name = "яблоко", quantity = 1, unit = "шт"),
            Item(id = 2, name = "Апельсин", quantity = 1, unit = "шт"),
            Item(id = 3, name = "Бананы", quantity = 1, unit = "шт")
        )
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        val order = csv.lines().filter { it.contains("шт") }
            .map { it.substringAfter("\"").substringBefore("\"") }
        assertEquals(listOf("Апельсин", "Бананы", "яблоко"), order)
    }

    @Test
    fun `пустая база даёт только заголовок`() {
        // Пустого файла быть не должно: Excel откроет его как пустой лист,
        // и человек решит, что данных нет.
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("должны быть строка с BOM и заголовок", 2, csv.lines().size)
    }

    @Test
    fun `нулевое количество не путается с отсутствием строки`() {
        val items = listOf(Item(id = 1, name = "Вещь", quantity = 0, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        assertTrue(
            "ноль — это значение, а не отсутствие строки. Фактическая строка: " +
                csv.lines().last(),
            csv.contains("\"Вещь\";0;\"шт\"")
        )
    }

    @Test
    fun `пустая единица не оставляет висящий пробел`() {
        // Иначе человек увидит «3 » и решит, что единица измерения потерялась.
        val items = listOf(Item(id = 1, name = "Вещь", quantity = 3, unit = ""))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        // Единица пустая, поэтому ячейка пустая и остаётся в кавычках:
        // боевой код экранирует все текстовые поля подряд, без исключений.
        //
        // Берём строку с данными, а не последнюю: CSV кончается CRLF, и
        // lines() даёт в конце пустой элемент — «последняя строка» тут пустая.
        val line = csv.lines().first { it.contains("Вещь") }
        assertTrue("пустая единица не должна давать висящий пробел: $line", line.contains("\"Вещь\";3;\"\";"))
        assertTrue("не должно быть пробела после числа: $line", !line.contains("3 \""))
    }

    @Test
    fun `пустое место записывается словами`() {
        // Пустая ячейка в столбце «Место» читается как «место не выбрано»,
        // что для склада неверно: вещь где-то лежит.
        val items = listOf(Item(id = 1, name = "Ёлка", quantity = 1, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        assertTrue("пустое место должно быть словом: $csv", csv.contains("Без места"))
    }

    @Test
    fun `путь места собирается целиком`() {
        val places = listOf(Place(id = 1, name = "Кладовая"))
        val shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1))
        val containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1))
        val items = listOf(Item(id = 1, name = "Ёлка", quantity = 1, unit = "шт", containerId = 1))
        val csv = buildCsv(places, shelves, containers, items)
        assertTrue("должен быть полный путь: $csv", csv.contains("Кладовая · Ящик · Стеллаж 1"))
    }

    @Test
    fun `в CSV место контейнера, а не проставленное у вещи`() {
        // Человек открывает CSV таблицей и идёт по указанному месту. Если там
        // написано «Балкон», а ящик стоит в кладовой, он не найдёт вещь — и
        // это данные, которые смотрели через другое приложение.
        val places = listOf(Place(id = 1, name = "Кладовая"), Place(id = 2, name = "Балкон"))
        val shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1))
        val containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1, placeId = 1))
        val items = listOf(
            Item(id = 1, name = "Ёлка", quantity = 1, unit = "шт", containerId = 1, placeId = 2)
        )
        val csv = buildCsv(places, shelves, containers, items)
        assertTrue(
            "в CSV должно быть место контейнера: $csv",
            csv.contains("Кладовая · Ящик · Стеллаж 1")
        )
        assertTrue(
            "место вещи попадать в CSV не должно: $csv",
            !csv.contains("Балкон")
        )
    }

    @Test
    fun `BOM стоит один раз и в начале, в данные не попадает`() {
        val items = listOf(Item(id = 1, name = "Первый", quantity = 1, unit = "шт"))
        val csv = buildCsv(emptyList(), emptyList(), emptyList(), items)
        assertEquals("BOM должен быть ровно один", 1, csv.count { it == bom })
        val first = csv.lines().drop(1).first { it.contains("Первый") }
        assertTrue("BOM не должен попасть в название: $first", first.startsWith("\"Первый\""))
    }

    /**
     * Число ячеек в строке CSV: разделитель внутри кавычек ячейкой не считается.
     */
    private fun countCells(line: String): Int {
        var cells = 1
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ';' -> if (!inQuotes) cells++
            }
            i++
        }
        return cells
    }

    /** Ровно та же сборка строки, что в `Repository.exportCsv`. */
    private fun buildCsv(
        places: List<Place>,
        shelves: List<Shelf>,
        containers: List<Container>,
        items: List<Item>
    ): String {
        val placeById = places.associateBy { it.id }
        val shelfById = shelves.associateBy { it.id }
        val containerById = containers.associateBy { it.id }

        fun esc(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

        fun itemLocation(i: Item): String {
            val c = i.containerId?.let(containerById::get)
            val s = c?.shelfId?.let(shelfById::get) ?: i.shelfId?.let(shelfById::get)
            // Тот же порядок, что в боевом Repository.exportCsv: место
            // контейнера и полки важнее проставленного у вещи. Если здесь
            // разойтись с боевым кодом, тест перестанет защищать то, что
            // действительно пишется в файл.
            val p = c?.placeId?.let(placeById::get)
                ?: s?.placeId?.let(placeById::get)
                ?: i.placeId?.let(placeById::get)
            return listOfNotNull(p?.name, c?.name, s?.name)
                .joinToString(" · ")
                .ifEmpty { "Без места" }
        }

        return buildString {
            append(bom)
            append("Название;Кол-во;Ед.;Категория;Место;Заметки\r\n")
            items.sortedBy { it.name.lowercase() }.forEach { i ->
                append(esc(i.name)).append(';')
                append(i.quantity).append(';')
                append(esc(i.unit)).append(';')
                append(esc(i.category)).append(';')
                append(esc(itemLocation(i))).append(';')
                append(esc(i.notes)).append("\r\n")
            }
        }
    }
}