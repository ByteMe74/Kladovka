plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    // Раньше здесь ещё объявлялись spotless и ru.shestyuk.gradle.docker-buildx
    // (оба с apply false и нигде не применялись). Их не удавалось разрешить —
    // сборка падала на этапе конфигурации, ещё до компиляции. Объявленные, но
    // неиспользуемые плагины — просто помеха, они удалены.
}