plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.kladovka"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.kladovka"
        minSdk = 26
        targetSdk = 35
        // versionCode = версия в схеме сервера (major*100 + minor), чтобы
        // latestApk (v1.30 -> 130) корректно сравнивался с установленной версией
        // 1.49: первые настоящие тесты на JVM — автонумерация имён и сборка
        // данных для интерфейса, 29 тестов. Поведение приложения не менялось,
        // поэтому это minor-подшаг.
        // 1.48 добавило кнопку «Выйти из аккаунта» и внятную реакцию на
        // отозванную сессию. С 1.47 подпись новая, поэтому поверх 1.47 и выше
        // ставится сразу; поверх 1.46 и ниже — не встанет, ключ от 1.46 утерян:
        // нужен «Экспорт (бэкап)», удаление, установка и «Импорт (восстановить)».
        versionCode = 149
        versionName = "1.49"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/kladovka-release.jks")
            storePassword = System.getenv("KLADOVKA_STORE_PASS") ?: findProperty("kladovka.store.pass")?.toString()
            keyAlias = "kladovka-release"
            keyPassword = System.getenv("KLADOVKA_KEY_PASS") ?: findProperty("kladovka.key.pass")?.toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Карта для выбора места (OpenStreetMap, без ключей API)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Инструментационные тесты (синхронизация с реальным сервером)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    // Обычные тесты на JVM: чистая логика без устройства. Инструментационные
    // выше требуют телефона и запускаются вручную, поэтому обычных тестов
    // не было вовсе: задача testDebugUnitTest раньше давала NO-SOURCE.
    testImplementation("junit:junit:4.13.2")
}