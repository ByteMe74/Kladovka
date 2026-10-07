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
// 1.50: место контейнера и полки теперь важнее места, проставленного у вещи.
//        Подпись места — один путь внутри одного места; раньше вещь, лежащая
//        в ящике в кладовой, могла показываться как «Балкон · Ящик · Стеллаж 1»,
//        и человек шёл не туда. Правка и в интерфейсе, и в CSV-экспорте, и
//        на десктопе — иначе одно и то же место показывалось бы по-разному.
//        Поведение видимое, поэтому minor-подшаг.
// 1.51: два дефекта в пути новичка, найденные установкой на чистом устройстве.
//        В приложении не было регистрации: серверная часть и ViewModel были
//        готовы с самого начала (showRegister, syncRegister), а формы не
//        существовало — только логин, пароль и кнопка подключения. Теперь вход
//        и регистрация переключаются, и аккаунт создаётся на месте.
//        Второй: отказ входа не был виден. serverLogin честно сообщает «Аккаунт
//        не подтверждён», но окно показывало текст ниже сгиба экрана — человек
//        нажимал «Подключиться» и не видел ничего. Сообщение перенесено под
//        статус. Оба изменения видны человеку сразу, поэтому minor-подшаг.
// 1.52: четыре правки, найденные при смещении фокуса на Android и запуске
//        проверки на самой старой заявленной версии — Android 8.0, API 26.
//        1) При отсутствии сети показывалось сырое исключение Java:
//        «Unable to resolve host ...: No address associated with hostname».
//        Переводчик ошибок существовал, но применялся к одному месту из девяти.
//        2) На экране 1920 px кнопка «Создать аккаунт» не помещалась в окно:
//        длинные метки полей переносились и раздували их вдвое.
//        3) На Android 11+ приложение не видело ни карт по имени, ни
//        обработчиков схем geo:/yandexmaps: — вместо приложения карт
//        открывался браузер. Не хватало блока <queries> в манифесте.
//        4) На ширине 320 dp нижние вкладки молча обрезали слова посередине
//        («Стеллаж», «Контейне»): зазоры съедали треть места, а softWrap = false
//        не делает текст короче. Кегль теперь подбирается измерением.
//        Все четыре видны человеку сразу, поэтому minor-подшаг.
        versionCode = 152
        versionName = "1.52"
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