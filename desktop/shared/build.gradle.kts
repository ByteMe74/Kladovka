plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    // api, а не implementation: тип OkHttpClient фигурирует в публичной сигнатуре
    // ApiClient, поэтому обязан быть виден и корневому модулю с UI.
    api("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

/**
 * Наполнение базы демо-данными для ручной проверки интерфейса:
 * `./gradlew :shared:seed`. Непустая база не затрагивается.
 */
tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Наполняет ~/.kladovka/kladovka.db демонстрационными данными"
    mainClass.set("ru.kladovka.data.Seed")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(System.getProperty("user.home") + "/.kladovka")
}
