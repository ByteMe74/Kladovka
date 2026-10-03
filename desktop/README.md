# Kladovka Desktop

Desktop-приложение для управления складом на базе Compose Desktop.

## Требования

- **JDK 17** или выше
- **Gradle 8.10** или выше
- **JetBrains Compose** (подключается автоматически через Gradle plugin)

## Установка

### 1. Установка JDK 17

Скачайте и установите JDK 17:
- [Adoptium Temurin 17](https://adoptium.net/temurin/releases/?version=17)

### 2. Установка Gradle

#### Вариант 1: Через SDK Manager (рекомендуется)
1. Скачайте [SDK Manager](https://services.gradle.org/dl/8.10/gradle-8.10-bin.zip)
2. Распакуйте в `C:\gradle`
3. Добавьте `C:\gradle\bin` в PATH

#### Вариант 2: Через Chocolatey (Windows)
```powershell
choco install gradle -y
```

#### Вариант 3: Скачать вручную
1. Скачайте [Gradle 8.10](https://services.gradle.org/distributions/gradle-8.10-bin.zip)
2. Распакуйте в удобную директорию
3. Добавьте `bin` директорию в PATH

### 3. Сборка

Откройте терминал в директории проекта и выполните:

```bash
cd kladovka-desktop
gradle build
```

Это создаст исполняемый файл в `build/distributions/kladovka-desktop/`.

### 4. Запуск

После успешной сборки:

```bash
cd build/distributions/kladovka-desktop
kladovka-desktop.exe
```

Или используйте Gradle для запуска:

```bash
gradle run
```

## Структура проекта

```
kladovka-desktop/
├── build.gradle.kts          # Gradle build configuration
├── settings.gradle.kts       # Project settings
├── gradle.properties         # Gradle properties
├── gradle/                   # Gradle wrapper
│   └── wrapper/
├── src/main/
│   ├── java/ru/kladovka/
│   │   ├── DesktopApp.kt     # Точка входа
│   │   ├── data/             # Data layer
│   │   │   ├── Repository.kt
│   │   │   ├── dao/          # SQLite DAOs
│   │   │   └── entities/     # Data classes
│   │   └── ui/               # UI layer
│   │       ├── App.kt        # Main app composable
│   │       ├── MainScreen.kt # Main screen composables
│   │       ├── AddItemScreen.kt
│   │       └── EditShelfScreen.kt
│   └── resources/            # Resources
```

## Основные сущности

- **Item** - вещь (название, количество, категория, ед. измерения)
- **Place** - место (название, координаты)
- **Shelf** - стеллаж (название, место, заметки)
- **Polka** - полка (название, стеллаж, заметки)
- **Container** - контейнер (название, место, расположение, заметки)

## Данные

Данные хранятся в SQLite базе данных. При запуске создаётся файл `kladovka.db` в директории приложения.

## Синхронизация

Приложение поддерживает синхронизацию с сервером через HTTP API. Для настройки синхронизации используйте меню "Сервер".

## Лицензия

MIT License
