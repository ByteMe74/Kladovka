# Состояние и план Kladovka

Проверено по коду, а не по намерениям. Подробности по безопасности — в
[`IMPROVEMENTS.md`](IMPROVEMENTS.md), по сборке десктопа — в
[`../kladovka-desktop/README.md`](../kladovka-desktop/README.md).

## Что сделано

### Android — `kladovka/`

Kotlin, Jetpack Compose, Room-схема версии 5.

- `ui/AppViewModel.kt` — ViewModel с логикой синхронизации
- `data/Repository.kt` — работа с Room + сетевые вызовы
- `data/AppDatabase.kt`, `data/Daos.kt`, `data/Entities.kt` — схема
- `ui/MainScreen.kt` — 5 вкладок: Места, Стеллажи, Полки, Контейнеры, Вещи
- Экраны редактирования всех пяти сущностей, `SyncDialog.kt`
- `android:usesCleartextTraffic` отключён — приложение ходит только по HTTPS

### Desktop — `kladovka-desktop/`

Порт Android-версии на **Compose Desktop**. Первоначальный план (Tauri + Rust +
React) отменён: общий код на Kotlin даёт паритет с телефоном без написания
второго UI, а база `kladovka.db` переносится между ПК и телефоном без
конвертации.

- Слой данных (`SqliteDatabase`, `ApiClient`, `Entities`, `Backup`) переписан
  под реальный SQLite и контракт `api.php`
- Иконки вкладок, значок приложения, счётчики — перенесены с Android
- **`gradle singleExe`** → `build\dist\Kladovka.exe`, один самодостаточный
  файл: Java не нужна ни при сборке у пользователя, ни при запуске
- Размер: 134,5 → 86,8 МБ (отсечены чужие native-библиотеки sqlite и
  неиспользуемые material-иконки; обе операции закрыты проверками в сборке)

### Сервер — `kladovka/api.php`

961 строка. Эндпоинты: `login`, `register`, `import`, `export`, `share`,
`unshare`, `profile`, `latestApk`, `upload_photo`, `logout`, `confirm`.
Rate-limiting есть для входа и регистрации.

### Веб — `kladovka/`

- `landing-index.php` — страница. Главная кнопка ведёт на
  `download-handler.php`, который подбирает файл по ОС посетителя; рядом
  остались прямые ссылки на APK и, если сборка выложена на сервер, на EXE
- `cabinet-index.php` — кабинет
- `download-handler.php` — раздача сборки под ОС. По умолчанию отвечает 302 на
  сам файл, поэтому вешается на `href` без JS; `?format=json` отдаёт
  `versionCode`/`versionName`/`url`/`md5`/`size` в том же формате, что и
  `api.php?action=latestApk`. Если сборки под ОС посетителя на сервере нет,
  отдаёт APK и честно помечает подмену заголовком `X-Kladovka-Served`

Поиск файла во всех четырёх PHP идёт перебором каталога регуляркой, а не через
`glob`: glob в PHP регистрозависим на всех платформах, включая Windows, и шаблон
`Kladovka-*.apk` не находит лежащий в репозитории `kladovka-v1.2.apk`. Проверено
на живом сервере — `api.php?action=latestApk` отвечал 404, а кнопка «Приложение»
в кабинете не появлялась вовсе. Версия сравнивается как `(major, minor)` из
имени файла — перезалитый старый APK не должен снова стать «актуальным» — и
правило обязано совпадать везде, иначе ссылки на разные поверхности разойдутся
версиями. `scandir()` везде закрыт проверкой `is_dir()`: без неё Warning уходит
прямо в ответ и ломает заголовки.

## Что осталось

| # | Что | Почему не сделано |
|---|---|---|
| 1 | Сменить пароль админа на сервере | нужен доступ к хосту; см. `IMPROVEMENTS.md` |
| 2 | Токены с таблицей, сроком жизни и отзывом | правка `api.php`, закрывает и O(n)-проверку токена |
| 3 | Убрать приём токена из `?token=` / `?api_key=` | остаётся Bearer-заголовок |
| 4 | Rate-limiting для `import`/`export`/`upload_photo` | сейчас только для входа и регистрации |
| 5 | Подпись файлов (signtool / codesign / gpg) | нет сертификата |
| 6 | Автообновление | `ApiClient.latestApk` написан, но UI его не вызывает |
| 7 | `packageMsi` | не установлен WiX 3 (`light.exe`); `createDistributable` работает |

## Проверки, встроенные в сборку десктопа

- `SingleExe verify` — SHA-256 каждой из 171 записи payload сверяется с
  исходником после упаковки
- `checkIcons` — каждая иконка, на которую ссылаются исходники, плюс шесть,
  которые material3 зовёт изнутри, грузится из готового jar и должна дать
  непустой вектор
- `slimUberJar` — падает, если в отжатом jar не нашлась ни одна ожидаемая
  иконка
- `:shared:test` — 20 тестов (`SqliteDatabaseTest` + `BackupTest`)

## Не относится к проекту

- `/deskforce/` — чужой чекаут RustDesk, лежит в рабочем каталоге. Не трогаем,
  добавлен в `.gitignore`.
