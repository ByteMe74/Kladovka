# Кладовка Desktop

Порт Android-приложения «Кладовка» (учёт вещей в кладовке) на Compose Desktop.
Данные — тот же `kladovka.db`, что и на телефоне: схема SQLite повторяет Room
версии 5, поэтому базу можно просто скопировать между ПК и телефоном.

## Требования

| Зачем | Что | Нужно всегда? |
|---|---|---|
| Компиляция Kotlin | **JDK 21** | да |
| Сборка | Gradle 8.10 (или `gradlew.bat` из репозитория) | да |
| Единый exe | `rustc` + `llvm-rc` (LLVM) | только для `singleExe` |

`gradle build` работает **без** rustc и llvm — они нужны только задаче,
собирающей самодостаточный exe.

```powershell
# проверка
java -version     # должна быть 21+
gradle -version   # 8.10+
rustc --version   # только для singleExe
```

## Команды

```powershell
cd kladovka-desktop

gradle run              # запуск из исходников
gradle build            # компиляция + тесты
gradle singleExe        # единый Kladovka.exe  (нужны rustc и llvm-rc)
gradle checkIcons       # проверка, что все используемые иконки отрисовываются
gradle createDistributable   # app-image: папка с exe и рядом
gradle :shared:seed     # демо-данные в ~/.kladovka/kladovka.db
```

## Единый exe

```powershell
gradle singleExe
# -> build\dist\Kladovka.exe
```

Это один самодостаточный файл: Java не нужна ни при сборке у пользователя, ни
при запуске, папка рядом не нужна. Внутри — Rust-заглушка (без внешних crates,
WinAPI вызывается напрямую) с приклеенным payload'ом: jlink-рантайм и uber-jar.

Как это ведёт себя на машине пользователя:

- **Первый запуск** (~8 с): распаковка в `%LOCALAPPDATA%\Kladovka\r-<хэш>`,
  172 файла, ~87 МБ. Появляется маркер `.complete`.
- **Последующие запуски** (~2–3 с): распаковка пропускается.
- **Старые каталоги `r-*`** удаляются, текущий — нет. Без этого каждый билед
  оставлял бы после себя ~87 МБ.
- Java берётся из бандла: запуск работает, когда в PATH нет ни `java`, ни
  `JAVA_HOME`.
- Ошибка первого запуска попадает в `%LOCALAPPDATA%\Kladovka\last-run.log`.

Чтобы убедиться, что в exe не попало повреждённое содержимое, задача после
упаковки перечитывает индекс из готового файла и сверяет SHA-256 каждой записи
с исходником — 171 файл. Совпасть должно всё, иначе сборка падает.

### Проверки, встроенные в сборку

- `verify` в `SingleExe.java` — целостность payload (см. выше).
- `checkIcons` — для каждой иконки, на которую ссылаются **исходники** плюс
  шесть, которые material3 зовёт изнутри, класс загружается из готового jar и
  у результата измеряется непустой вектор. Проверка завязана на исходники, а
  не на список `keptIcons`: иначе вычёркивание строки из списка проходило бы
  обе проверки разом — список валидировал бы сам себя.
- `slimUberJar` — падает, если в отжатом jar не нашлась ни одна иконка из
  `keptIcons`.

## Размер

| Шаг | uber-jar | exe |
|---|---|---|
| исходный | 86,5 МБ | 134,5 МБ |
| − чужие native-библиотеки sqlite | 75,7 МБ | 123,8 МБ |
| − неиспользуемые material-иконки | **38,7 МБ** | **86,8 МБ** |

Что отсекается и почему это безопасно:

- `org/sqlite/native/` — sqlite-jdbc возит библиотеки для шести платформ
  (24 МБ), на Windows нужны 3,6.
- `androidx/compose/material/icons/` — 11 410 классов, пять стилей по 2133
  иконки; используется 18. Список не составлен на глаз: его дал
  `jdeps -verbose:class` по исходному uber-jar.

## Структура

```
kladovka-desktop/
├── build.gradle.kts           # задачи сборки, отжимание jar, упаковка exe
├── settings.gradle.kts        # include(":shared")
├── icons/                     # kladovka.ico/.icns/.png — из вектора Android
├── tools/
│   ├── SingleExe.java         # hash / pack / verify payload
│   ├── IconCheck.java         # проверка иконок на отрисовку
│   ├── IconGen.java           # генерация иконок из геометрии Android-вектора
│   └── launcher/main.rs       # Rust-заглушка: распаковка и запуск JVM
├── src/main/kotlin/ru/kladovka/
│   ├── Main.kt
│   └── ui/                    # MainScreen, SyncScreen, Theme, EditDialogs, UiModels
└── shared/src/
    ├── main/kotlin/ru/kladovka/data/
    │   ├── SqliteDatabase.kt   # реальный SQLite, схема Room v5
    │   ├── ApiClient.kt        # контракт api.php, только https
    │   ├── Entities.kt         # Place, Shelf, Polka, Container, Item
    │   ├── Backup.kt  Settings.kt  Seed.kt
    └── test/kotlin/.../SqliteDatabaseTest.kt   # 20 тестов (SqliteDatabaseTest + BackupTest)
```

UI — в корневом модуле, слой данных — в `:shared`.

## Данные

`~/.kladovka/kladovka.db` (на Windows — `%USERPROFILE%\.kladovka\`).
Файл тот же, что и на Android, конвертация не нужна.

## Ограничения

- `gradle packageMsi` падает: нужен WiX 3 (`light.exe`), в системе не
  установлен. `createDistributable` работает.
- `gradle singleExe` требует rustc и llvm-rc. Обычная сборка — нет.

## Лицензия

MIT License
