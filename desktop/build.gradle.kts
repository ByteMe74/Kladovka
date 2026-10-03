import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Версия приложения — одно место для сборки exe, установщика и ресурса VERSIONINFO. */
val appVersion = "1.0.0"

/**
 * Код версии для сравнения с сервером — та же схема, что у Android
 * (`major*100 + minor`), чтобы latestExe и latestApp считали одинаково.
 * Взят из appVersion, а не написан руками: две строки со временем
 * разъедутся, и обновление либо не заметится, либо будет предлагаться вечно.
 */
val appVersionCode: Int = run {
    val parts = appVersion.split('.')
    val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
    val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
    require(major * 100 + minor > 0) { "Не разобрать версию $appVersion как major.minor" }
    major * 100 + minor
}

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

compose.desktop {
    application {
        mainClass = "ru.kladovka.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Kladovka"
            packageVersion = appVersion
            description = "Кладовка — учёт вещей"

            // Значок приложения — тот же, что на Android: бирюзовая подложка #00696B,
            // белый короб и янтарные вещи. Файлы генерируются tools/IconGen.java
            // из геометрии вектора Android-приложения. Формат у платформ разный.
            windows {
                iconFile.set(rootProject.file("icons/kladovka.ico"))
                menu = true
                menuGroup = "Кладовка"
                upgradeUuid = "6f9a1c22-4f0e-4c1e-9f2b-2c0b6d5a7e31"
            }
            macOS {
                iconFile.set(rootProject.file("icons/kladovka.icns"))
            }
            linux {
                iconFile.set(rootProject.file("icons/kladovka-512.png"))
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // materialIconsExtended весит 36 МБ и нужен только ради шести иконок
    // (Archive, Inventory2, Layers, PushPin, Remove, Sync) — в material-icons-core
    // их нет. Вынести их в проект отдельным файлом — очевидная будущая оптимизация,
    // см. tools/IconGen.java рядом.
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation(project(":shared"))
}

kotlin {
    jvmToolchain(21)
}

/**
 * Значок окна при запуске.
 *
 * File в проводнике и значок в окне — это два разных места. В Explorer
 * показывается иконка `Kladovka.exe`, она вшита в ресурсы заглушки через
 * llvm-rc. А само окно создаёт не заглушка, а порождённый ею `java.exe`, и
 * у окна свой значок — тот, что задан через `Window(icon = ...)`. Без него
 * Windows рисует дефолтный, и на панели задач висит не Кладовка.
 *
 * Источник один — `icons/kladovka-256.png`, его пишет `tools/IconGen.java`.
 * Копия в src/main/resources не заводится осознанно: IconGen перезаписывает
 * icons/, а вторая копия молча разошлась бы с тем, что вшито в .rc.
 */
val appIconDir = layout.buildDirectory.dir("generated/app-icon")

val stageAppIcon by tasks.registering(Copy::class) {
    group = "build"
    description = "Кладёт значок приложения в classpath для Window(icon = ...)"
    from(layout.projectDirectory.file("icons/kladovka-256.png"))
    into(appIconDir)
}

sourceSets["main"].resources.srcDir(appIconDir)

tasks.named("processResources") { dependsOn(stageAppIcon) }

// ================================================================= Версия в classpath
//
// Приложению нужно знать свою версию, чтобы отличить «есть обновление» от
// «сервер отстаёт». Раньше номер жил только в build.gradle.kts, и в коде его
// не было: сверить не с чем было. Пишем его ресурсом рядом со значком, из того
// же appVersion, — иначе версия в свойствах файла и версия в приложении
// разъедутся при первой же правке одной из двух строк.
val versionResourceDir = layout.buildDirectory.dir("generated/version")

val writeVersionResource by tasks.registering {
    val outDir = versionResourceDir
    val v = appVersion
    val vc = appVersionCode.toString()
    inputs.property("version", v)
    inputs.property("versionCode", vc)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        // Схема та же, что у Android: major*100+minor, чтобы сервер и клиент
        // считали одно и то же. Здесь major.minor, patch в код не входит —
        // патчи сервер не различает.
        val f = File(dir, "kladovka-version.properties")
        f.writeText(
            """
            version=$v
            versionCode=$vc
            versionMajor=${appVersion.substringBefore('.')}
            versionMinor=${appVersion.split('.').getOrElse(1) { "0" }}
            """.trimIndent() + "\n"
        )
    }
}

sourceSets["main"].resources.srcDir(versionResourceDir)
tasks.named("processResources") { dependsOn(writeVersionResource) }

// ================================================================= Единый .exe
//
// Собирает один файл Kladovka.exe: заглушка из Rust с иконкой приложения,
// к которой приклеен jlink-рантайм и uber-jar. При запуске лаунчер распаковывает
// их в %LOCALAPPDATA%\Kladovka\r-<хэш> и стартует JVM — рядом с exe ничего нет,
// и установленная Java не требуется.

val isWindowsOs = System.getProperty("os.name").startsWith("Windows")

fun onPath(name: String): Boolean {
    val pathVar = System.getenv("PATH") ?: return false
    val exts = if (isWindowsOs) listOf(".exe", ".cmd", ".bat", "") else listOf("")
    return pathVar.split(File.pathSeparatorChar).any { dir ->
        dir.isNotBlank() && exts.any { File(dir, name + it).isFile }
    }
}

/**
 * Запуск внешней программы.
 *
 * Именно providers.exec, а не Exec- задача с блоком exec {}: в Exec- задаче такой
 * блок выполняется сразу при создании задачи, и хэш payload читался бы раньше,
 * чем посчитан. Здесь всё отложено до времени выполнения.
 */
fun execTool(env: Map<String, String>, name: String, args: Array<out String>): String {
    // ignoreExitValue обязателен: иначе providers.exec бросает исключение сам,
    // не дав прочитать stderr, а ради него вызов и затевался.
    val out = providers.exec {
        commandLine(listOf(name) + args)
        environment(env)
        isIgnoreExitValue = true
    }
    val stdout = out.standardOutput.asText.get()
    val stderr = out.standardError.asText.get().trim()
    val code = out.result.get().exitValue
    if (code != 0) {
        throw GradleException(
            "$name завершился с кодом $code\n" +
            if (stderr.isEmpty()) stdout.trim() else stderr
        )
    }
    return stdout
}

fun captureTool(name: String, vararg args: String): String =
    execTool(emptyMap(), name, args)

fun captureToolEnv(env: Map<String, String>, name: String, vararg args: String): String =
    execTool(env, name, args)

fun runTool(name: String, vararg args: String) {
    val text = captureTool(name, *args).trim()
    if (text.isNotEmpty()) logger.lifecycle(text)
}

fun runToolEnv(env: Map<String, String>, name: String, vararg args: String) {
    val text = captureToolEnv(env, name, *args).trim()
    if (text.isNotEmpty()) logger.lifecycle(text)
}

val buildTools = javaToolchains
val singleExeBuild = layout.buildDirectory.dir("singleexe")
val runtimeDir = singleExeBuild.map { it.dir("runtime") }
val toolsClasses = singleExeBuild.map { it.dir("tools-classes") }
val rcFile = singleExeBuild.map { it.file("kladovka.rc") }
val resFile = singleExeBuild.map { it.file("kladovka.res") }
val hashTxt = singleExeBuild.map { it.file("payload-hash.txt") }
val stubExe = singleExeBuild.map { it.file("launcher-stub.exe") }
val finalExe = layout.buildDirectory.file("dist/Kladovka.exe")
val iconIco = file("icons/kladovka.ico")

/** Набор модулей jlink. jdeps по умолчанию не видит зависимости по рефлексии,
 *  поэтому список задан явно: TLS нужен OkHttp, java.logging и java.prefs —
 *  самому Compose/Skiko. */
val jlinkModules = listOf(
    "java.base", "java.desktop", "java.sql", "java.logging", "java.management",
    "java.naming", "java.prefs", "java.xml", "java.instrument", "java.net.http",
    "jdk.crypto.ec", "jdk.unsupported", "jdk.zipfs",
).joinToString(",")

val rawUberJar = layout.buildDirectory.dir("compose/jars").map { dir ->
    // Имя задаёт Compose-плагин; отжатый jar называется иначе и сюда не попадает.
    val jars = dir.asFile.listFiles { f ->
        f.name.startsWith("Kladovka-") && f.name.endsWith(".jar")
    }.orEmpty()
    require(jars.size == 1) {
        "ожидался ровно один uber-jar в ${dir.asFile}, найдено ${jars.size}: " +
        jars.joinToString { it.name }
    }
    jars.single()
}

/**
 * Иконки, на которые в jar кто-то ссылается.
 *
 * Список не составлен на глаз: его дал `jdeps -verbose:class` по исходному
 * uber-jar — то есть по всем классам сразу. 12 иконок нужны нашему UI,
 * ещё 6 material3 использует внутри своих компонентов (SegmentedButton,
 * ExposedDropdownMenu, DatePicker, Snackbar), поэтому без них компоненты
 * падали бы NoClassDefFoundError в момент открытия диалога, а не при сборке.
 *
 * Остальные ~11 400 классов в `androidx/compose/material/icons/` — это пять
 * стилей по 2133 иконки, из которых приложение не использует ни одного.
 * Это 84 МБ распакованных, пятая часть веса exe.
 */
val keptIcons = setOf(
    "filled/Add", "filled/Archive", "filled/ArrowDropDown", "filled/Check",
    "filled/Clear", "filled/Close", "filled/DateRange", "filled/Edit",
    "filled/Inventory2", "filled/Layers", "filled/Place", "filled/PushPin",
    "filled/Refresh", "filled/Remove", "filled/Search", "filled/Settings", "filled/Sync",
    "automirrored/filled/KeyboardArrowLeft",
    "automirrored/filled/KeyboardArrowRight",
)

/** `ArchiveKt.class` -> `Archive`; `ArchiveKt$Inner.class` -> `Archive`. */
fun iconBase(fileName: String): String? {
    if (!fileName.endsWith(".class")) return null
    val base = fileName.substringBefore('$').removeSuffix(".class")
    return if (base.endsWith("Kt")) base.removeSuffix("Kt") else null
}

/**
 * Uber-jar после отжимания.
 *
 * Две вещи, которые приложение не использует, но тащит в каждом экземпляре:
 * нативные библиотеки sqlite для чужих платформ (24 МБ, нужны 3,6) и
 * остальные стили material-иконок (84 МБ, используются 18 классов).
 * Остальное — Compose, OkHttp, наш код — не трогаем.
 */
val uberJar = layout.buildDirectory.file("compose/jars/kladovka-slim.jar")

val slimUberJar by tasks.registering {
    description = "Оставляет в uber-jar только используемые библиотеки и иконки"
    dependsOn("packageUberJarForCurrentOS")
    inputs.file(rawUberJar)
    outputs.file(uberJar)
    doLast {
        val src = rawUberJar.get()
        val dst = uberJar.get().asFile
        dst.parentFile.mkdirs()
        delete(dst)

        val sqlitePrefix = "org/sqlite/native/"
        val iconsPrefix = "androidx/compose/material/icons/"
        val seenIcons = mutableSetOf<String>()
        // Имя ресурса с версией: его обязано остаться в отжатом jar, иначе
        // приложение не будет знать своей версии (см. проверку ниже).
        val versionResource = "kladovka-version.properties"
        val keptVersionResource = mutableListOf<String>()

        fun keep(name: String): Boolean {
            if (name.startsWith(sqlitePrefix)) {
                // Путь вида org/sqlite/native/<ОС>/<архитектура>/<файл>. Сравнение
                // регистронезависимое: в jar лежит "Windows", и при проверке на
                // "windows" фильтр молча удалял всё, включая нужную библиотеку.
                val os = name.removePrefix(sqlitePrefix).substringBefore('/')
                return os.equals("Windows", ignoreCase = true)
            }
            if (!name.startsWith(iconsPrefix)) return true
            val parts = name.removePrefix(iconsPrefix).split('/')
            // Корень пакета: Icons.class, Icons$Filled.class, IconsKt.class.
            // Нужен всем — это сам объект Icons, без него не работает Icons.Filled.
            if (parts.size < 2) return true
            val base = iconBase(parts.last()) ?: return false
            val style = parts.dropLast(1).joinToString("/")
            val key = "$style/$base"
            if (key in keptIcons) {
                seenIcons += key
                return true
            }
            return false
        }

        var dropped = 0L
        var keptWindows = 0
        ZipOutputStream(dst.outputStream().buffered()).use { out ->
            ZipFile(src).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    if (!keep(e.name)) {
                        dropped += e.compressedSize
                        continue
                    }
                    if (e.name.startsWith(sqlitePrefix)) keptWindows++
                    if (e.name == versionResource) {
                        keptVersionResource += String(
                            zip.getInputStream(e).readBytes(),
                            Charsets.UTF_8
                        ).trim()
                    }
                    out.putNextEntry(ZipEntry(e.name))
                    zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        if (keptWindows == 0) {
            throw GradleException(
                "В отжатом jar не осталось ни одной нативной библиотеки sqlite " +
                "для Windows — приложение не сможет открыть базу. Проверьте фильтр."
            )
        }
        // Полнота списка иконок: если хотя бы одной не оказалось, значит имя в
        // keptIcons не совпало с тем, что лежит в jar, и приложение упадёт в
        // момент отрисовки вкладки. Лучше узнать об этом здесь.
        val missing = keptIcons - seenIcons
        if (missing.isNotEmpty()) {
            throw GradleException(
                "В исходном jar не нашлись иконки: ${missing.joinToString()}. " +
                "Проверьте имена в keptIcons — без них приложение упадёт при отрисовке."
            )
        }
        // Ресурс с версией обязан выжить отсечение: без него UpdateChecker не
        // знает своей версии, молча считает её нулевой, и обновление не
        // предлагается никогда — при этом сборка собирается и запускается, то
        // есть поломка выглядит как «работает, но обновлений нет».
        if (keptVersionResource.isEmpty()) {
            throw GradleException(
                "В отжатом jar нет $versionResource. Приложение не знает своей " +
                "версии и не сможет предложить обновление. Проверьте, что задача " +
                "writeVersionResource подключена к processResources."
            )
        }
        logger.lifecycle("версия в jar: ${keptVersionResource.first()}")
        val saved = src.length() - dst.length()
        logger.lifecycle(
            "отсечено ${"%.1f".format(dropped / 1024.0 / 1024.0)} МБ " +
            "(нативные библиотеки sqlite чужих платформ + неиспользуемые material-иконки); " +
            "uber-jar: ${"%.1f".format(src.length() / 1024.0 / 1024.0)} -> " +
            "${"%.1f".format(dst.length() / 1024.0 / 1024.0)} МБ"
        )
        if (saved <= 0) {
            throw GradleException("отжимание не дало результата — проверьте фильтр")
        }
    }
}

/** Кодировка вывода дочерней JVM. Без неё русский текст из упаковщика
 *  печатается в кодовой странице консоли и в логе сборки превращается в мусор. */
val utf8Out = arrayOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")

fun jdk21bin(name: String): String {
    val home = buildTools.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get().metadata.installationPath.asFile
    return File(home, "bin/$name").absolutePath
}

/**
 * Прогоняет каждую иконку, оставленную в отжатом jar, и смотрит, что она
 * строит непустой вектор.
 *
 * Нужна отдельно от проверки имён в slimUberJar. Отсутствующий класс иконки
 * не ломает ни сборку, ни запуск: он выстреливает NoClassDefFoundError уже в
 * момент отрисовки вкладки, то есть у пользователя, а не у нас. Совпадение
 * имён в списке при этом ничего не доказывает — доказывает только прогон.
 */
val checkIcons by tasks.registering {
    group = "verification"
    description = "Проверяет, что все оставленные в jar иконки отрисовываются"
    dependsOn(slimUberJar)
    inputs.file(uberJar)
    inputs.file(layout.projectDirectory.file("tools/IconCheck.java"))
    doLast {
        runTool(
            jdk21bin("java.exe"),
            *utf8Out,
            "-cp", uberJar.get().asFile.absolutePath,
            layout.projectDirectory.file("tools/IconCheck.java").asFile.absolutePath,
            layout.projectDirectory.asFile.absolutePath,
            *keptIcons.toTypedArray(),
        )
    }
}

val compileSingleExeTools by tasks.registering(JavaCompile::class) {
    description = "Компилирует упаковщик tools/SingleExe.java"
    source(file("tools/SingleExe.java"))
    destinationDirectory.set(toolsClasses)
    // Утилите нужен только JDK — внешних зависимостей нет.
    classpath = files()
    options.release.set(21)
    inputs.file("tools/SingleExe.java")
    outputs.dir(toolsClasses)
}

val jlinkRuntime by tasks.registering {
    group = "distribution"
    description = "Собирает минимальный JRE через jlink"
    inputs.property("modules", jlinkModules)
    outputs.dir(runtimeDir)
    doFirst { delete(runtimeDir) }
    doLast {
        runTool(
            jdk21bin("jlink.exe"),
            "--add-modules", jlinkModules,
            "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2",
            "--output", runtimeDir.get().asFile.absolutePath,
        )
    }
}

/** Ресурсы Windows: иконка (чтобы в проводнике и на панели задач был наш значок,
 *  а не стандартный) и VERSIONINFO (имя, описание, версия в свойствах файла). */
val compileWindowsResources by tasks.registering {
    description = "Готовит .res с иконкой и сведениями о версии"
    inputs.file(iconIco)
    inputs.property("version", appVersion)
    outputs.file(resFile)
    doLast {
        if (!onPath("llvm-rc")) {
            throw GradleException(
                "Не найден llvm-rc — без него в .exe не будет иконки.\n" +
                "Ставится вместе с LLVM: https://releases.llvm.org/"
            )
        }
        val rc = rcFile.get().asFile
        rc.parentFile.mkdirs()
        val v = appVersion.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0)
        val numbers = v.take(4).joinToString(",") { it.toString() }
        rc.writeText(
            buildString {
                // Русский текст в ресурсе требует UTF-8. Прагма #pragma code_page
                // llvm-rc не поддерживает — кодовая страница задаётся флагом /C.
                appendLine("1 ICON \"${iconIco.absolutePath}\"")
                appendLine("1 VERSIONINFO")
                appendLine("FILEVERSION $numbers")
                appendLine("PRODUCTVERSION $numbers")
                appendLine("FILEOS 0x4L")
                appendLine("FILETYPE 0x1L")
                appendLine("BEGIN")
                appendLine("  BLOCK \"StringFileInfo\"")
                appendLine("  BEGIN")
                appendLine("    BLOCK \"040904B0\"")
                appendLine("    BEGIN")
                appendLine("      VALUE \"CompanyName\", \"Кладовка\"")
                appendLine("      VALUE \"FileDescription\", \"Кладовка — учёт вещей\"")
                appendLine("      VALUE \"ProductName\", \"Kladovka\"")
                appendLine("      VALUE \"InternalName\", \"Kladovka\"")
                appendLine("      VALUE \"OriginalFilename\", \"Kladovka.exe\"")
                appendLine("      VALUE \"ProductVersion\", \"$appVersion\"")
                appendLine("      VALUE \"FileVersion\", \"$appVersion\"")
                appendLine("    END")
                appendLine("  END")
                appendLine("  BLOCK \"VarFileInfo\"")
                appendLine("  BEGIN")
                appendLine("    VALUE \"Translation\", 0x419, 1252")
                appendLine("  END")
                appendLine("END")
            },
            Charsets.UTF_8,
        )
        runTool("llvm-rc.exe", "/C", "65001", "/FO", resFile.get().asFile.absolutePath, rc.absolutePath)
    }
}

/** Хэш содержимого payload: он вшит в лаунчер и определяет, какую распаковку
 *  считать актуальной. Считается до сборки заглушки, потому что входит в неё. */
val payloadHash by tasks.registering {
    description = "Считает хэш содержимого payload"
    dependsOn(jlinkRuntime, slimUberJar, compileSingleExeTools)
    inputs.dir(runtimeDir)
    inputs.file(uberJar)
    outputs.file(hashTxt)
    doLast {
        val hash = captureTool(
            jdk21bin("java.exe"),
            *utf8Out,
            "-cp", toolsClasses.get().asFile.absolutePath,
            "SingleExe", "hash",
            runtimeDir.get().asFile.absolutePath,
            uberJar.get().asFile.absolutePath,
        ).trim()
        val h = hashTxt.get().asFile
        h.parentFile.mkdirs()
        h.writeText("$hash\n")
        logger.lifecycle("payload хэш: $hash")
    }
}

val compileLauncher by tasks.registering {
    description = "Собирает заглушку-лаунчер на Rust"
    dependsOn(compileWindowsResources, payloadHash)
    inputs.file("tools/launcher/main.rs")
    outputs.file(stubExe)
    doLast {
        if (!onPath("rustc")) {
            throw GradleException(
                "Не найден rustc. Он нужен только для сборки заглушки exe,\n" +
                "обычная сборка (gradle build) его не требует: https://rustup.rs"
            )
        }
        runToolEnv(
            mapOf("KLADOVKA_PAYLOAD_HASH" to hashTxt.get().asFile.readText().trim()),
            "rustc",
            "-O", "--edition", "2021",
            "-C", "panic=abort",
            "-C", "codegen-units=1",
            "-C", "strip=symbols",
            "-C", "link-arg=${resFile.get().asFile.absolutePath}",
            "tools/launcher/main.rs",
            "-o", stubExe.get().asFile.absolutePath,
        )
    }
}

val packSingleExe by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Приклеивает payload к заглушке — получается Kladovka.exe"
    dependsOn(compileLauncher, checkIcons)
    inputs.file(stubExe)
    inputs.dir(runtimeDir)
    inputs.file(uberJar)
    outputs.file(finalExe)
    classpath = files(toolsClasses)
    mainClass.set("SingleExe")
    jvmArgs(*utf8Out)
    doFirst {
        delete(finalExe)
        args(
            "pack",
            stubExe.get().asFile.absolutePath,
            finalExe.get().asFile.absolutePath,
            runtimeDir.get().asFile.absolutePath,
            uberJar.get().asFile.absolutePath,
        )
    }
    doLast {
        // Перечитываем индекс из готового exe и сверяем с исходниками: упаковка
        // может отработать «успешно», оставив неверные смещения.
        runTool(
            jdk21bin("java.exe"),
            *utf8Out,
            "-cp", toolsClasses.get().asFile.absolutePath,
            "SingleExe", "verify",
            finalExe.get().asFile.absolutePath,
            runtimeDir.get().asFile.absolutePath,
            uberJar.get().asFile.absolutePath,
        )
        val size = finalExe.get().asFile.length()
        logger.lifecycle(
            "\nГотово: ${finalExe.get().asFile.absolutePath}  (${size / 1024 / 1024} МБ)\n" +
            "Запускайте этот файл на любом компьютере с Windows — папки рядом не нужно,\n" +
            "Java тоже не нужна. При первом старте распаковка в %LOCALAPPDATA%\\Kladovka."
        )
    }
}

tasks.register("singleExe") {
    group = "distribution"
    description = "Единый переносимый Kladovka.exe — одним файлом, без установленной Java"
    dependsOn(packSingleExe)
}
