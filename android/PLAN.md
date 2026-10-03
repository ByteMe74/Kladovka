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

- `landing-index.php` — страница; кнопка «Скачать APK» ведёт прямо на файл
  `Kladovka-*.apk` (строка 408), то есть всегда отдаёт APK, какой бы ОС ни
  зашёл
- `cabinet-index.php` — кабинет
- `download-handler.php` — умеет определить ОС по User-Agent и отдать свой
  дистрибутив (`Kladovka-v%s.%s.exe` / `.msi` / `.dmg` / `.apk`), но **нигде
  не подключён**: во всём репозитории на него нет ни одной ссылки. Лежит
  мёртвым грузом — кнопка на странице в него не указывает

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
| 8 | Подключить `download-handler.php` к кнопке на лендинге | файл готов и определяет ОС, но на него нет ни одной ссылки; пока кнопка всегда отдаёт APK |

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
