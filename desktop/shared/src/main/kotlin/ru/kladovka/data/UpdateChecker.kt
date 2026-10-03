package ru.kladovka.data

/**
 * Что приложению известно о свежей сборке на сервере.
 *
 * Отдельный тип вместо того, чтобы разбрасывать по экрану `versionCode` и
 * `url`: решение «показывать кнопку или нет» принимается в одном месте, и
 * состояние «проверяем / нечем» не путается с «новой версии нет».
 */
sealed interface UpdateState {

    /** Ещё не проверяли. */
    data object Unknown : UpdateState

    /** Проверяем прямо сейчас. */
    data object Checking : UpdateState

    /** Свежее — предлагать нечего. */
    data class UpToDate(val serverVersion: String) : UpdateState

    /**
     * Есть обновление.
     *
     * [url] ведёт на файл, а не на страницу: приложение портативное, ставится
     * копированием, и «открыть ссылку» здесь означает скачать новый exe, который
     * человек заменит сам. Самообновления нет — см. [requiresManualInstall].
     */
    data class Available(
        val serverVersion: String,
        val url: String,
        val sizeBytes: Long
    ) : UpdateState {

        /** Настольная сборка ставится заменой файла, автоматически она себя не заменит. */
        val requiresManualInstall: Boolean get() = true

        /**
         * «91,0 МБ» — так размер читается в разговоре.
         *
         * Разделитель дробной части берётся из локали системы намеренно: у
         * русского интерфейса десятичная запятая верна, и «91.0 МБ» рядом с
         * русским текстом выглядит опечаткой. Тесты сравнивают эту строку через
         * нормализацию, потому что на другой машине локаль другая.
         */
        fun sizeText(): String = when {
            sizeBytes <= 0L -> ""
            sizeBytes < 1024L * 1024L -> "${sizeBytes / 1024} КБ"
            else -> String.format("%.1f МБ", sizeBytes / 1024.0 / 1024.0)
        }
    }

    /**
     * Проверка не удалась — сети нет или сервер молчит.
     *
     * Не путаем с [UpToDate]: человек должен различать «вы актуальны» и «мы не
     * смогли узнать», иначе при обрыве связи он решит, что обновлений не бывает.
     */
    data class Failed(val reason: String) : UpdateState
}

/**
 * Проверка обновлений.
 *
 * Спрашивает `latestExe`, а не `latestApk`: у телефонной сборки своя шкала
 * версий (там уже 1.48), и сравнение с ней для десктопа бессмысленно — 148
 * против 100 не значит «новее». Отдельное действие на сервере сделано именно
 * поэтому.
 *
 * Логика сравнения намеренно простая: больше код — значит новее. Сборка с
 * меньшим кодом (откат на сервере) обновлением не считается.
 */
class UpdateChecker(private val api: ApiClient) {

    suspend fun check(): UpdateState = runCatching {
        if (!AppVersion.known) {
            // Версия неизвестна — сравнивать не с чем, и предлагать обновление
            // вслепую хуже, чем не предлагать.
            return@runCatching UpdateState.UpToDate("неизвестно")
        }
        val latest = api.latestVersion()
            ?: return@runCatching UpdateState.Failed("сервер не ответил")

        if (latest.versionCode <= AppVersion.code) {
            UpdateState.UpToDate(latest.versionName)
        } else if (latest.url.isBlank()) {
            UpdateState.Failed("сервер не дал ссылку на сборку")
        } else {
            UpdateState.Available(latest.versionName, latest.url, latest.size)
        }
    }.getOrElse { e ->
        UpdateState.Failed(e.message ?: "не удалось проверить")
    }
}