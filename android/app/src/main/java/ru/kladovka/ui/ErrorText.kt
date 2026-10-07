package ru.kladovka.ui

/**
 * Перевод технических сообщений сети и сервера на человеческий язык.
 *
 * Зачем отдельный объект, а не функция внутри окна синхронизации: сетевые
 * ошибки приходят из девяти мест в [AppViewModel] — вход, отзыв сессии,
 * регистрация, смена профиля, «дать доступ», «отозвать доступ», отправка,
 * загрузка, проверка обновления. Раньше переводилась только одна из них
 * (та, что попадала в сообщение окна), остальные показывали человеку то,
 * что написала Java. Например, при выключенном интернете на экране появлялось
 * «Unable to resolve host ...: No address associated with hostname».
 *
 * Главное правило: сообщения сервера уже написаны по-русски для человека
 * («Это имя уже занято», «Аккаунт не подтверждён...»), и их надо показывать
 * как есть. Переводчик узнаёт только известные технические обороты и
 * подменяет их; всё остальное проходит без изменений.
 */
object ErrorText {

    /** Нет сети: не нашлось имя сервера. */
    const val NO_INTERNET = "Нет подключения к интернету. Проверьте связь и попробуйте ещё раз."

    /** Сеть есть, но сервер не отвечает. */
    const val SERVER_UNREACHABLE = "Сервер не отвечает. Проверьте интернет и попробуйте ещё раз."

    /** Неверный логин или пароль (так же говорит сервер). */
    const val LOGIN_FAILED = "Неверный логин или пароль. Проверьте их и попробуйте снова."

    /** Сервер отказал в доступе — нужны права или вход. */
    const val ACCESS_DENIED = "Сервер отказал в доступе. Проверьте логин и пароль."

    /** На сервере что-то сломалось — надо повторить позже. */
    const val SERVER_TROUBLE = "На сервере временные проблемы. Попробуйте позже."

    /** Не подтвердилась безопасность соединения. */
    const val NO_SECURE_CONNECTION = "Не удалось подтвердить безопасное соединение с сервером."

    /** Когда исключение не сказало ничего внятного. */
    const val GENERIC = "Что-то пошло не так. Попробуйте ещё раз."

    /**
     * Переводит сообщение об ошибке.
     *
     * @param raw что пришло от сети/сервера; null и пустая строка — «ничего не сказал»
     * @param fallback что показать, если сказать нечего
     */
    fun human(raw: String?, fallback: String = GENERIC): String {
        val text = raw?.trim().orEmpty()
        // Некоторые исключения приходят вообще без сообщения: тогда вместо текста
        // в экране оказывается пустое место или слово «null».
        if (text.isEmpty() || text.equals("null", ignoreCase = true)) return fallback
        val r = text.lowercase()
        return when {
            r.contains("unable to resolve host") ||
                r.contains("no address associated with hostname") ||
                r.contains("unknownhost") ||
                r.contains("network is unreachable") ||
                r.contains("не удалось разрешить") ->
                NO_INTERNET
            r.contains("failed to connect") ||
                r.contains("connect timed out") ||
                r.contains("timed out") ||
                r.contains("timeout") ->
                SERVER_UNREACHABLE
            // Русские варианты отказа во входе намеренно не перечислены: их
            // присылает сам сервер («Неверный логин или пароль»), и менять
            // собственную формулировку сервера незачем.
            r.contains("bad credentials") ->
                LOGIN_FAILED
            // 401 разбираем раньше прочих «4xx»: это отказ во входе, и человеку
            // нужны слова про логин, а не про доступ вообще.
            r.contains("http 401") -> LOGIN_FAILED
            r.contains("http 4") -> ACCESS_DENIED
            r.contains("http 5") -> SERVER_TROUBLE
            r.contains("certificate") || r.contains("trust anchor") -> NO_SECURE_CONNECTION
            // Ни одного русского слова: это не сообщение сервера (сервер говорит
            // по-русски), а служебный текст вроде «Expected a ',' or '}'».
            // Показывать его человеку незачем.
            !hasRussianLetters(text) -> fallback
            // Сообщение сервера: человек, который его писал, уже сказал всё сам.
            else -> text
        }
    }

    /** То же, но для исключения: null-сообщение заменяется запасным текстом. */
    fun of(e: Throwable?, fallback: String = GENERIC): String = human(e?.message, fallback)

    /** Есть ли в тексте хоть одна русская буква. */
    private fun hasRussianLetters(text: String): Boolean = text.any {
        it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё'
    }
}