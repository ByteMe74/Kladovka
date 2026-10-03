plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.1"
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":server-api"))
    
    // Compose Desktop
    implementation("org.jetbrains.compose.ui:ui")
    implementation("org.jetbrains.compose.ui:ui-graphics")
    implementation("org.jetbrains.compose.ui:ui-tooling")
    implementation("org.jetbrains.compose.material3:material3")
    
    // Корутины
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.2")
}
