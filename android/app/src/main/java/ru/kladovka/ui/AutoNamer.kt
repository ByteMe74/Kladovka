package ru.kladovka.ui

/**
 * Следующее автоматическое имя вида «Полка 5» или «Вещь 3».
 * Ищет максимальный числовой суффикс среди существующих имён (например, «Полка 7»)
 * и возвращает следующий номер — без совпадений, даже если часть записей удалена.
 */
fun nextAutoName(existingNames: List<String>, prefix: String): String {
    val re = Regex("^${Regex.escape(prefix)}\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
    val max = existingNames.asSequence()
        .mapNotNull { re.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull() ?: 0
    return "$prefix ${max + 1}"
}