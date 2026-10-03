package ru.kladovka.data

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Настройки и порядок списка.
 *
 * Обе вещи появились вместе с выравниванием десктопа под Android: токен должен
 * переживать перезапуск (иначе логин приходилось вводить каждый раз), а
 * сортировка вещей — вести себя так же, как на телефоне, включая правило
 * «закреплённые сверху».
 */
class SettingsAndSortingTest {

    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("kladovka-settings")
    }

    @AfterTest
    fun tearDown() {
        runCatching {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `токен и имя пользователя переживают перезапуск`() {
        val saved = AppSettings(
            themeMode = ThemeMode.DARK,
            serverUrl = "https://example.test",
            dataDir = dir.toString(),
            token = "токен-из-входа",
            username = "vasya"
        )
        SettingsStore.save(dir, saved)

        val loaded = SettingsStore.load(dir)
        assertEquals("токен-из-входа", loaded.token)
        assertEquals("vasya", loaded.username)
        assertEquals("https://example.test", loaded.serverUrl)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
    }

    @Test
    fun `пароль сохраняется ради тихого автологина`() {
        // Как в Android-приложении: там пароль тоже лежит в настройках и по нему
        // делается syncLogin при каждом запуске. Без него после отзыва токена
        // сервером восстановиться нечем.
        SettingsStore.save(
            dir,
            AppSettings(dataDir = dir.toString(), token = "t", username = "vasya", password = "секретный")
        )
        assertEquals("секретный", SettingsStore.load(dir).password)
    }

    @Test
    fun `выход стирает и токен, и пароль`() {
        SettingsStore.save(
            dir,
            AppSettings(dataDir = dir.toString(), token = "токен", username = "vasya", password = "пароль")
        )
        // Ровно то, что делает кнопка «Выйти»: сохраняем настройки с пустыми
        // учётными данными.
        SettingsStore.save(
            dir,
            SettingsStore.load(dir).copy(token = "", username = "", password = "")
        )
        val loaded = SettingsStore.load(dir)
        assertEquals("", loaded.token)
        assertEquals("", loaded.username)
        assertEquals("", loaded.password)
        // Остальные настройки не должны пострадать.
        assertEquals(ApiClient.DEFAULT_URL, loaded.serverUrl)
    }

    @Test
    fun `в пустых настройках токена нет`() {
        val loaded = SettingsStore.load(dir)
        assertEquals("", loaded.token)
        assertEquals("", loaded.username)
    }

    private val items = listOf(
        Item(id = 1, name = "Болты", quantity = 5, category = "Крепёж", pinned = false, updatedAt = 300),
        Item(id = 2, name = "Аккумулятор", quantity = 1, category = "Электро", pinned = false, updatedAt = 100),
        Item(id = 3, name = "антифриз", quantity = 12, category = "Жидкости", pinned = false, updatedAt = 200),
        Item(id = 4, name = "Ящик", quantity = 1, category = "Крепёж", pinned = true, updatedAt = 50)
    )

    @Test
    fun `по имени сортировка не зависит от регистра`() {
        val names = items.sortedFor(SortMode.NAME).map { it.name }
        // Порядок по кодам символов: аккумулятор < антифриз < болты < ящик,
        // а закреплённый «Ящик» всё равно идёт первым.
        assertEquals(listOf("Ящик", "Аккумулятор", "антифриз", "Болты"), names)
    }

    @Test
    fun `по количеству`() {
        val got = items.sortedFor(SortMode.QUANTITY).map { it.name }
        assertEquals(listOf("Ящик", "Аккумулятор", "Болты", "антифриз"), got)
    }

    @Test
    fun `по категории внутри категории идёт по имени`() {
        val got = items.sortedFor(SortMode.CATEGORY).map { it.name }
        // ж < к < э, внутри «Крепёж» — Болты, а закреплённый «Ящик» первым.
        assertEquals(listOf("Ящик", "антифриз", "Болты", "Аккумулятор"), got)
    }

    @Test
    fun `сначала изменённые идёт по убыванию времени`() {
        val got = items.sortedFor(SortMode.UPDATED).map { it.name }
        assertEquals(listOf("Ящик", "Болты", "антифриз", "Аккумулятор"), got)
    }

    @Test
    fun `закреплённая вещь остаётся первой в любом режиме`() {
        for (mode in SortMode.entries) {
            val first = items.sortedFor(mode).first()
            assertEquals("Ящик", first.name, "режим ${mode.title}: закреплённая должна быть первой")
            assertTrue(first.pinned)
        }
    }
}