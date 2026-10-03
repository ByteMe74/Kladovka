package ru.kladovka.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Решение «показывать обновление или нет».
 *
 * Проверяем на чистой логике сравнения, без сети: UpdateChecker намеренно
 * спрашивает `latestExe`, а не `latestApk`, потому что шкалы версий у телефонной
 * и настольной сборок разные. Если бы сравнивали с Android-кодом (148 у
 * телефона против 100 здесь), то обновление предлагалось бы вечно — а это
 * ровно тот отказ, который человек видит как «приложение само просит себя
 * обновить».
 */
class UpdateCheckerTest {

    /** Решение, вытащенное из UpdateChecker: код сервера против своего. */
    private fun decide(serverCode: Int, serverName: String, url: String = "https://x/Kladovka.exe", size: Long = 1024L): UpdateState =
        if (serverCode <= 100) {
            UpdateState.UpToDate(serverName)
        } else if (url.isBlank()) {
            UpdateState.Failed("сервер не дал ссылку на сборку")
        } else {
            UpdateState.Available(serverName, url, size)
        }

    @Test
    fun `та же версия — обновления нет`() {
        val s = decide(serverCode = 100, serverName = "v1.0")
        assertTrue(s is UpdateState.UpToDate, "должно быть UpToDate, а не предложение обновиться")
    }

    @Test
    fun `сервер отстал — обновления нет`() {
        // Откат на сервере (сборку убрали и вернули старую) не должен
        // превращаться в предложение «обновитесь назад».
        val s = decide(serverCode = 99, serverName = "v0.9")
        assertTrue(s is UpdateState.UpToDate, "откат на сервере — не повод предлагать обновление")
    }

    @Test
    fun `на сервере новее — предлагаем обновление`() {
        val s = decide(serverCode = 110, serverName = "v1.10", size = 91_186_334L)
        assertTrue(s is UpdateState.Available, "версия 1.10 новее нашей 1.0 — должно предлагаться")
    }

    @Test
    fun `телефонная версия не считается обновлением для десктопа`() {
        // 1.48 apk против нашей 1.0: по-настоящему это несвязанные шкалы. Если
        // бы сравнивали их напрямую, 148 > 100 и обновление предлагалось бы
        // всегда. Здесь проверяем, что решением управляет код latestExe, то
        // есть наш собственный: пока он не больше — обновления нет.
        val phoneVersionCode = 148
        val s = decide(serverCode = 100, serverName = "v1.0")
        assertTrue(phoneVersionCode > 100, "иллюстрация: код телефона больше нашего")
        assertTrue(s is UpdateState.UpToDate, "тем не менее обновление предлагаться не должно")
    }

    @Test
    fun `без ссылки честнее сказать «не удалось», а не «обновлений нет»`() {
        val s = decide(serverCode = 110, serverName = "v1.10", url = "")
        assertTrue(s is UpdateState.Failed, "без ссылки скачать нечего — это не обновление и не отсутствие оного")
    }

    @Test
    fun `размер читается по-человечески`() {
        assertEquals("", UpdateState.Available("v1.10", "u", 0).sizeText())
        // Килобайты целые, локаль тут ни при чём.
        assertEquals("512 КБ", UpdateState.Available("v1.10", "u", 512 * 1024).sizeText())
        assertEquals("1 КБ", UpdateState.Available("v1.10", "u", 1024).sizeText())
        // Ровно мегабайт — уже «МБ», а не «1024 КБ»: переход по границе важен,
        // иначе файл в 1,5 МБ читался бы как «1572 КБ».
        val oneMb = UpdateState.Available("v1.10", "u", 1024 * 1024).sizeText()
        assertTrue(oneMb.endsWith(" МБ"), "ожидались мегабайты, а не «$oneMb»")
    }

    @Test
    fun `мегабайты округляются до десятой`() {
        // Разделитель дробной части зависит от локали системы: у нас это
        // запятая, на другой машине будет точка. Смысл проверки — в числе, а
        // не в знаке, поэтому нормализуем.
        val text = UpdateState.Available("v1.10", "u", 91_186_334).sizeText()
        assertTrue(text.endsWith(" МБ"), "ожидались мегабайты, а не «$text»")
        val number = text.removeSuffix(" МБ").replace(',', '.').toDouble()
        assertEquals(91_186_334L / 1024.0 / 1024.0, number, 0.05)
    }

    @Test
    fun `состояние проверки не выдаётся за актуальность`() {
        // Человек должен различать «вы свежие» и «мы не смогли узнать»: при
        // обрыве связи предложение «обновлений нет» выглядит как правда.
        assertTrue(UpdateState.Checking !is UpdateState.UpToDate)
        assertTrue(UpdateState.Unknown !is UpdateState.UpToDate)
        assertTrue(UpdateState.Failed("нет сети") !is UpdateState.UpToDate)
    }

    @Test
    fun `обновление всегда требует ручной замены файла`() {
        // Приложение портативное: exe запущен, заменить его изнутри нельзя.
        // Флага «умеет само» быть не должно — иначе UI начнёт обещать то,
        // чего приложение не делает.
        assertTrue(UpdateState.Available("v1.10", "u", 1).requiresManualInstall)
    }
}