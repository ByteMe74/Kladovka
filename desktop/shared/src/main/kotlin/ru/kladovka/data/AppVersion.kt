package ru.kladovka.data

import java.util.Properties

/**
 * Версия этой сборки.
 *
 * Значения приходят ресурсом `kladovka-version.properties`, который пишет
 * задача `writeVersionResource` из `appVersion` в build.gradle.kts. Отдельная
 * константа в коде разошлась бы с версией в свойствах файла: человек видит
 * «1.1» в проводнике, а приложение считает себя «1.0», и обновление
 * предлагается ему вечно либо не предлагается вовсе.
 *
 * Если ресурса нет (запуск из исходников мимо Gradle, обрезанный classpath) —
 * возвращаем нули: приложение работает, но проверку обновления пропускает.
 */
object AppVersion {

    private val props: Properties = Properties()

    init {
        // Ресурс в classpath у сборки singleExe лежит рядом со значком; отсутствие
        // не должно ронять запуск, поэтому берём молча.
        runCatching {
            AppVersion::class.java.classLoader
                .getResourceAsStream("kladovka-version.properties")
                ?.use { props.load(it) }
        }
    }

    /** «1.0.0» — как показывают свойства файла. */
    val name: String = props.getProperty("version").orEmpty()

    /** major*100 + minor: та же шкала, что у сервера и у Android. */
    val code: Int = props.getProperty("versionCode")?.toIntOrNull() ?: 0

    /** Кратко для интерфейса: «1.0». */
    val shortName: String
        get() = if (name.isEmpty()) "" else name.substringBeforeLast('.').ifEmpty { name }

    /** Известна ли версия вообще. */
    val known: Boolean get() = code > 0
}