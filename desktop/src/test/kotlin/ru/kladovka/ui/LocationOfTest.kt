package ru.kladovka.ui

import ru.kladovka.data.AppData
import ru.kladovka.data.Container
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Shelf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Подпись места вещи на десктопе.
 *
 * Функция одна и та же в двух приложениях, но теста на неё не было ни там, ни
 * тут: правка проходила бы незамеченной, и одно приложение показывало бы место
 * иначе, чем другое, на одних и тех же данных.
 *
 * Проверяем главное решение: место контейнера и место полки важнее места,
 * проставленного у самой вещи. Иначе подпись читается как «ящик на балконе»,
 * хотя ящик в кладовой, и человек идёт не туда.
 */
class LocationOfTest {

    private val places = listOf(
        Place(id = 1, name = "Кладовая"),
        Place(id = 2, name = "Балкон")
    )

    @Test
    fun `путь собирается от общего к частому`() {
        val data = AppData(
            places = places,
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 1))
        )
        assertEquals("Кладовая · Ящик · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `место контейнера важнее проставленного у вещи`() {
        // Порядок в подписи — это порядок от общего к частому: человек читает
        // «в кладовой, а в ней этот ящик на этой полке». Смешивать два разных
        // места в одной подписи нельзя.
        val data = AppData(
            places = places,
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1, placeId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 1, placeId = 2))
        )
        assertEquals("Кладовая · Ящик · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `место полки важнее проставленного у вещи`() {
        val data = AppData(
            places = places,
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", shelfId = 1, placeId = 2))
        )
        assertEquals("Кладовая · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `без контейнера и полки остаётся место вещи`() {
        val data = AppData(places = places, items = listOf(Item(id = 1, name = "Ёлка", placeId = 2)))
        assertEquals("Балкон", data.locationOf(data.items.first()))
    }

    @Test
    fun `место контейнера без места не вытесняет место вещи`() {
        // У контейнера места нет — значит, он ничего не сообщает о
        // расположении, и выигрывать должен ноль. Иначе аккуратно
        // проставленное место вещи пропадало бы, и подпись стала бы «Без места».
        val data = AppData(
            places = places,
            containers = listOf(Container(id = 1, name = "Сумка", shelfId = null, placeId = null)),
            items = listOf(Item(id = 1, name = "Документы", containerId = 1, placeId = 2))
        )
        assertEquals("Балкон · Сумка", data.locationOf(data.items.first()))
    }

    @Test
    fun `вещь без места говорит об этом прямо`() {
        // Пустая строка выглядит как поломка вёрстки: карточка с ничего не
        // показывающим местом.
        assertEquals("Без места", AppData().locationOf(Item(id = 1, name = "Вещь")))
    }

    @Test
    fun `отсутствующий контейнер не обрывает подпись`() {
        // Ссылка на контейнер, которого уже нет: запись удалили на телефоне, а
        // вещь осталась. Показываем то, что есть.
        val data = AppData(places = places, items = listOf(Item(id = 1, name = "Ёлка", containerId = 99, placeId = 1)))
        assertEquals("Кладовая", data.locationOf(data.items.first()))
    }
}