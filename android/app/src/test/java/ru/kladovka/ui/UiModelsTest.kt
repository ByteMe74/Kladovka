package ru.kladovka.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.kladovka.data.Container
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Polka
import ru.kladovka.data.Shelf

/**
 * Сборка данных для интерфейса: подписи мест, количество, координаты.
 *
 * Здесь нет ни базы, ни Android — чистые функции над списками, поэтому тесты
 * работают на JVM без устройства.
 *
 * Проверяем то, что выглядит безобидным, но ломает интерфейс незаметно:
 * «Без места» вместо пустой строки, правильное разрешение места через
 * контейнер, когда у вещи нет прямой ссылки на стеллаж, и количество без
 * дробной части у целого числа.
 */
class UiModelsTest {

    // ------------------------------------------------------------- locationOf

    @Test
    fun `вещь без места говорит об этом прямо`() {
        // Пустая строка выглядит как поломка вёрстки: карточка с ничего не
        // показывающим местом. «Без места» — осмысленный текст.
        assertEquals("Без места", AppData().locationOf(Item(id = 1, name = "Вещь")))
    }

    @Test
    fun `место вещи собирается из вложенности`() {
        val data = AppData(
            places = listOf(Place(id = 1, name = "Кладовая")),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", shelfId = 1))
        )
        assertEquals("Кладовая · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `место контейнера подставляется вместе со стеллажом`() {
        // Вещь ссылается только на контейнер. Стеллаж и место берутся по цепочке
        // контейнер → стеллаж → место; если цепочку не пройти, в подписи будет
        // только «Контейнер 1» и человек не поймёт, где оно.
        val data = AppData(
            places = listOf(Place(id = 1, name = "Кладовая")),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 1))
        )
        assertEquals("Кладовая · Ящик · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `сначала место, потом контейнер, потом стеллаж`() {
        // Порядок в подписи — это порядок от общего к частому: человек читает
        // «Кладовая, а в ней этот ящик на этом стеллаже».
        val data = AppData(
            places = listOf(Place(id = 1, name = "Гараж")),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 2", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Коробка", shelfId = 1)),
            items = listOf(Item(id = 1, name = "Инструмент", containerId = 1))
        )
        assertEquals("Гараж · Коробка · Стеллаж 2", data.locationOf(data.items.first()))
    }

    @Test
    fun `место контейнера уступает явно выбранному месту вещи`() {
        // НАЙДЕННОЕ РАСХОЖДЕНИЕ, поведение зафиксировано как есть.
        //
        // Место у вещи и у контейнера выбираются независимо (ItemEditScreen
        // отдаёт два отдельных селектора), поэтому вещь может числиться в
        // «Балконе», а лежать в ящике, который стоит в «Кладовой».
        //
        // Сейчас приоритет у item.placeId, и подпись получается
        // «Балкон · Ящик · Стеллаж 1» — человек читает это как «ящик на
        // балконе», хотя ящик в кладовой. Выглядит как ошибка, хотя данные
        // введены верно.
        //
        // Менять порядок без решения владельца нельзя: возможен и обратный
        // замысел — человек явно приписал вещь к другому месту, чтобы
        // учитывать её там, где физически её нет. Тест зафиксирует текущее
        // поведение, чтобы перемена была осознанной.
        val data = AppData(
            places = listOf(
                Place(id = 1, name = "Кладовая"),
                Place(id = 2, name = "Балкон")
            ),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1, placeId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 1, placeId = 2))
        )
        assertEquals("Балкон · Ящик · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `без явно выбранного места берётся место контейнера`() {
        // Обратный случай: своего места у вещи нет, и тогда подпись опирается
        // на контейнер — это правильное поведение, и его тоже фиксируем.
        val data = AppData(
            places = listOf(Place(id = 1, name = "Кладовая"), Place(id = 2, name = "Балкон")),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1, placeId = 1)),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 1, placeId = null))
        )
        assertEquals("Кладовая · Ящик · Стеллаж 1", data.locationOf(data.items.first()))
    }

    @Test
    fun `контейнер без стеллажа не выдумывает его`() {
        // Контейнер может висеть без стеллажа. Дописывать пустую часть нельзя —
        // иначе в подписи окажется «Ящик · » с висящим разделителем.
        val data = AppData(
            containers = listOf(Container(id = 1, name = "Сумка", shelfId = null)),
            items = listOf(Item(id = 1, name = "Документы", containerId = 1))
        )
        assertEquals("Сумка", data.locationOf(data.items.first()))
    }

    @Test
    fun `отсутствующий контейнер не обрывает подпись`() {
        // Ссылка на контейнер, которого уже нет (запись удалили на другом
        // устройстве, а вещь осталась). Показывать надо то, что есть.
        val data = AppData(
            places = listOf(Place(id = 1, name = "Кладовая")),
            items = listOf(Item(id = 1, name = "Ёлка", containerId = 99, placeId = 1))
        )
        assertEquals("Кладовая", data.locationOf(data.items.first()))
    }

    @Test
    fun `null в месте означает без места`() {
        // Приходит из Room как null, а не «»: разные типы пустоты нельзя смешивать.
        val data = AppData()
        assertEquals("Без места", data.locationOf(Item(id = 1, name = "Вещь", placeId = null)))
    }

    // ------------------------------------------------------------- qtyText

    @Test
    fun `количество с единицей измерения`() {
        assertEquals("5 шт", qtyText(Item(id = 1, name = "Вещь", quantity = 5, unit = "шт")))
        assertEquals("1 л", qtyText(Item(id = 1, name = "Вода", quantity = 1, unit = "л")))
    }

    @Test
    fun `без единицы показывается голое число`() {
        assertEquals("7", qtyText(Item(id = 1, name = "Вещь", quantity = 7, unit = "")))
    }

    @Test
    fun `пробелы в единице не дают двойного пробела`() {
        // Единица хранится как ввёл человек: « шт » — и наивная склейка дала бы
        // «5  шт » с двумя пробелами, а где-то в вёрстке это ещё и перенос.
        assertEquals("5 шт", qtyText(Item(id = 1, name = "Вещь", quantity = 5, unit = "  шт  ")))
    }

    // ------------------------------------------------------------- coordsText

    @Test
    fun `координаты форматируются одинаково в любой локали`() {
        // Через Locale.US намеренно: в русской локали десятичная запятая
        // смешалась бы с разделителем координат, и текст стал бы неоднозначным.
        val text = coordsText(Place(id = 1, name = "Дом", latitude = 55.75234, longitude = 37.61567))
        assertEquals("55.75234, 37.61567", text)
    }

    @Test
    fun `без одной из координат подписи нет`() {
        // Половина координат бесполезна: человек нажал бы и не попал.
        assertNull(coordsText(Place(id = 1, name = "Дом", latitude = 55.75, longitude = null)))
        assertNull(coordsText(Place(id = 1, name = "Дом", latitude = null, longitude = 37.61)))
        assertNull(coordsText(Place(id = 1, name = "Дом")))
    }

    // ------------------------------------------------------------- placeName

    @Test
    fun `имя места ищется по id`() {
        val data = AppData(places = listOf(Place(id = 1, name = "Кладовая"), Place(id = 2, name = "Балкон")))
        assertEquals("Кладовая", data.placeName(1))
        assertEquals("Балкон", data.placeName(2))
    }

    @Test
    fun `нет места — нет и имени`() {
        // null, а не пустая строка: пустая строка заняла бы место в подписи.
        assertNull(AppData().placeName(1))
        assertNull(AppData().placeName(null))
    }

    // ------------------------------------------------------------- buildPlaces

    @Test
    fun `полка показывается вместе со всем, что на ней`() {
        val data = AppData(
            places = listOf(Place(id = 1, name = "Кладовая")),
            shelves = listOf(Shelf(id = 1, name = "Стеллаж 1", placeId = 1)),
            polki = listOf(Polka(id = 1, name = "Полка 1", shelfId = 1)),
            containers = listOf(Container(id = 1, name = "Ящик", shelfId = 1)),
            items = listOf(
                Item(id = 1, name = "Вещь на полке", containerId = null, shelfId = 1),
                Item(id = 2, name = "Вещь в ящике", containerId = 1)
            )
        )
        val ui = buildPlaces(data)
        assertEquals(1, ui.shelves.size)
        val shelfUi = ui.shelves.first()
        // Вес�� прямо на стеллаже и внутри контейнера считаются разными вещами
        // и не должны сливаться.
        assertEquals(listOf("Вещь на полке"), shelfUi.direct.map { it.name })
        assertEquals(1, shelfUi.containers.size)
        assertEquals(listOf("Вещь в ящике"), shelfUi.containers.first().items.map { it.name })
        assertEquals("Стеллаж 1", ui.polki.firstOrNull { it.polka.name == "Полка 1" }?.onShelf?.name ?: "Полка потерялась")
    }

    @Test
    fun `контейнер без стеллажа попадает в раздел свободных`() {
        // Потерянный контейнер не должен исчезнуть из интерфейса: человек
        // увидит «свои» вещи пропавшими и начнёт искать причину.
        val data = AppData(
            containers = listOf(Container(id = 1, name = "Сумка", shelfId = null)),
            items = listOf(Item(id = 1, name = "Документы", containerId = 1))
        )
        val ui = buildPlaces(data)
        assertEquals(1, ui.looseContainers.size)
        assertEquals("Сумка", ui.looseContainers.first().container.name)
        assertEquals(listOf("Документы"), ui.looseContainers.first().items.map { it.name })
    }

    @Test
    fun `вещь без места и без контейнера считается неразмещённой`() {
        val data = AppData(
            items = listOf(Item(id = 1, name = "Дырокол"), Item(id = 2, name = "Ёлка", shelfId = 1))
        )
        val ui = buildPlaces(data)
        assertEquals(listOf("Дырокол"), ui.unplaced.map { it.name })
    }

    @Test
    fun `пустая база даёт пустой интерфейс, а не исключение`() {
        val ui = buildPlaces(AppData())
        assertEquals(0, ui.unplaced.size)
        assertEquals(0, ui.shelves.size)
        assertEquals(0, ui.looseContainers.size)
    }
}