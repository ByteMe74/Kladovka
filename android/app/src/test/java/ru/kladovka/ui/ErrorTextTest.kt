package ru.kladovka.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перевод сетевых ошибок на человеческий язык.
 *
 * Проверяем две вещи, которые ломаются по-разному. Первая: текст, который
 * человек видит на экране, не должен содержать внутренних терминов Java —
 * раньше при выключенном интернете в окне обновлений появлялось
 * «Unable to resolve host ...: No address associated with hostname», и это
 * значило просто «нет интернета».
 *
 * Вторая, не менее важная: переводчик не должен съедать сообщения сервера.
 * Сервер уже говорит по-русски и точно знает, в чём дело («Это имя уже
 * занято»); если заменить такое сообщение на общее «Что-то пошло не так»,
 * человек потеряет причину, по которой он что-то не смог сделать.
 */
class ErrorTextTest {

    // ------------------------------------------------------- отсутствие сети

    @Test
    fun `нет сети — текст про интернет без английских слов`() {
        // Ровно то сообщение, что было на экране при включённом авиарежиме.
        val raw = """Unable to resolve host "kladovka.dr6ter.ru": No address associated with hostname"""
        val text = ErrorText.human(raw)
        assertEquals(ErrorText.NO_INTERNET, text)
        // Ни одного латинского слова: иначе человек снова увидит внутренности Java.
        assertFalse("в тексте остались английские буквы: $text", text.any { it in 'a'..'z' })
        assertTrue("в тексте должно быть про интернет: $text", text.contains("интернет"))
    }

    @Test
    fun `другие сетевые обрывы тоже говорят про интернет`() {
        assertEquals(ErrorText.NO_INTERNET, ErrorText.human("unknownhost: kladovka.dr6ter.ru"))
        assertEquals(ErrorText.NO_INTERNET, ErrorText.human("Network is unreachable"))
    }

    // ------------------------------------------------------------ сервер молчит

    @Test
    fun `сервер не отвечает — при отказе соединения и таймауте`() {
        assertEquals(ErrorText.SERVER_UNREACHABLE, ErrorText.human("Failed to connect to /10.0.2.2:443"))
        assertEquals(ErrorText.SERVER_UNREACHABLE, ErrorText.human("connect timed out"))
        assertEquals(ErrorText.SERVER_UNREACHABLE, ErrorText.human("Read timed out"))
    }

    // ------------------------------------------------------------------- вход

    @Test
    fun `отказ входа говорит про логин и пароль`() {
        assertEquals(ErrorText.LOGIN_FAILED, ErrorText.human("HTTP 401"))
        assertEquals(ErrorText.LOGIN_FAILED, ErrorText.human("Bad credentials"))
        // Сервер на русском — текст не трогаем, он и так про логин.
        assertEquals("Неверный логин или пароль", ErrorText.human("Неверный логин или пароль"))
    }

    @Test
    fun `ошибка сервера говорит про временные проблемы`() {
        assertEquals(ErrorText.SERVER_TROUBLE, ErrorText.human("HTTP 500"))
        assertEquals(ErrorText.SERVER_TROUBLE, ErrorText.human("HTTP 503"))
    }

    // --------------------------------------------------------- пусто и непонятно

    @Test
    fun `пустая строка даёт осмысленный запасной текст`() {
        // Пустой текст в диалоге выглядит как поломка вёрстки, а «null» —
        // как ошибка в приложении. Ни то, ни другое человеку ничего не говорит.
        for (raw in listOf(null, "", "   ", "null", "NULL")) {
            val text = ErrorText.human(raw)
            assertTrue("пустое сообщение должно давать запасной текст, а не «$text»", text.isNotBlank())
            assertNotEquals("null", text)
            assertEquals(ErrorText.GENERIC, text)
        }
    }

    @Test
    fun `свой запасной текст не перебивается`() {
        // Путь сообщения без текста — он знает, о чём речь, и должен сказать это сам.
        assertEquals(
            "Не удалось открыть доступ",
            ErrorText.human(null, "Не удалось открыть доступ")
        )
    }

    @Test
    fun `служебный текст без русских слов не показывается`() {
        // Сервер говорит по-русски, поэтому английская строка без русских букв —
        // это внутренняя ошибка разбора, а не сообщение для человека.
        assertEquals(ErrorText.GENERIC, ErrorText.human("Expected a ',' or '}'"))
    }

    // -------------------------------------- сообщения сервера доходят как есть

    @Test
    fun `сообщение сервера на русском доходит без изменений`() {
        // Главный риск переводчика: начать подменять чужие осмысленные тексты.
        // Человек должен увидеть причину, а не общее «Что-то пошло не так».
        for (serverMessage in listOf(
            "Это имя уже занято",
            "Недостаточно прав для этого действия",
            "Аккаунт не подтверждён: перейдите по ссылке из письма, затем войдите снова",
            "Сессия отозвана на сервере — например, вы вышли из аккаунта на сайте."
        )) {
            assertEquals(serverMessage, ErrorText.human(serverMessage))
        }
    }

    // ------------------------------------------------------------- исключения

    @Test
    fun `сообщение исключения переводится и приходит готовым`() {
        val e = RuntimeException("""Unable to resolve host "kladovka.dr6ter.ru"""")
        assertEquals(ErrorText.NO_INTERNET, ErrorText.of(e))
        // Ровно то, что кладёт в исключение Repository при ответе сервера без
        // пояснения: «Сервер ответил HTTP 401».
        assertEquals(ErrorText.LOGIN_FAILED, ErrorText.of(Exception("Сервер ответил HTTP 401")))
        assertEquals(ErrorText.SERVER_TROUBLE, ErrorText.of(Exception("Сервер ответил HTTP 500")))
    }
}