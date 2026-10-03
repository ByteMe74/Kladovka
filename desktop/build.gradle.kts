import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            packageVersion = "1.0.0"
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
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation(project(":shared"))
}

kotlin {
    jvmToolchain(21)
}
