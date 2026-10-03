plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

compose.desktop {
    application {
        mainClass = "ru.kladovka.MainKt"
        
        nativeDistributions {
            // ...
        }
    }
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    
    // Jetpack Compose components for Desktop
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Kotlinx-serialization для JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    
    // OkHttp для HTTP-запросов к серверу
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Kotlinx-coroutines для асинхронности
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    
    // Shared module
    implementation(project(":shared"))
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}





