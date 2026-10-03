# План улучшений Kladovka

## Анализ текущей архитектуры

### Android-приложение (Kotlin, Jetpack Compose)
- **AppViewModel.kt** (589 строк) — главный ViewModel с логикой синхронизации
- **Repository.kt** — работа с БД Room + сетевые вызовы к серверу
- **AppDatabase.kt** — Room-схема (Places, Shelves, Polki, Containers, Items)
- **MainScreen.kt** — главный экран (5 вкладок: Места, Стеллажи, Полки, Контейнеры, Вещи)
- Экраны редактирования: ItemEditScreen, ContainerEditScreen, PlaceEditScreen, ShelfEditScreen, PolkaEditScreen
- SyncDialog.kt — диалог синхронизации

### Веб-кабинет
- `C:\Users\Night\kladovka-cabinet\index.php` — базовый PHP-интерфейс

### Серверная часть
- `C:\Users\Night\kladovka\api.php` — основной API-файл
- `C:\Users\Night\kladovka-server-api.php` — версия сервера
- API-эндпоинты: login, register, import/export, share/unshare, profile, latestApk, upload_photo

### Текущие проблемы
1. **Скачивание по кнопке** — всегда скачивается один APK, без учёта ОС
2. **Нет подписи файлов** — дистрибутивы не подписаны
3. **Windows-приложение отсутствует** — только Android + веб
4. **Кабинет и Android** — требуют улучшения UI/UX и функциональности

## Этап 1: Windows-приложение (Tauri + Rust)

### Архитектура
```
kladovka-desktop/
├── Cargo.toml                    # Зависимости Rust
├── src/
│   ├── main.rs                   # Точка входа
│   ├── lib.rs                    # Библиотека API
│   └── tauri/
│       └── capabilities/
│           └── default.toml
├── tauri/
│   ├── tauri.conf.json           # Конфигурация
│   ├── capabilities/
│   │   └── default.toml
│   └── capabilities/
│       └── default.toml
├── src-tauri/
│   ├── build.rs
│   ├── Cargo.toml
│   ├── rust-toolchain.toml
│   ├── entitlements.mac.plist
│   ├── entitlements.mac.inherit.plist
│   ├── entitlements.windows.plist
│   ├── entitlements.windows.inherit.plist
│   ├── icons/
│   ├── bundle/
│   │   ├── windows/
│   │   │   ├── installer.nsi
│   │   │   └── AppxManifest.xml
│   │   └── macos/
│   │       ├── entitlements.plist
│   │       └── info.plist
│   └── src/
│       ├── main.rs
│       ├── lib.rs
│       └── capabilities/
│           └── default.toml
├── src/
│   ├── App.tsx
│   ├── main.tsx
│   ├── components/
│   │   ├── LoginScreen.tsx
│   │   ├── Dashboard.tsx
│   │   ├── ItemList.tsx
│   │   ├── ItemEdit.tsx
│   │   ├── SyncDialog.tsx
│   │   └── ...
│   ├── hooks/
│   │   └── useApi.ts
│   ├── store/
│   │   └── index.ts
│   ├── types/
│   │   └── index.ts
│   ├── utils/
│   │   ├── api.ts
│   │   ├── crypto.ts
│   │   └── sign.ts
│   └── assets/
│       └── ...
├── package.json
├── tsconfig.json
├── vite.config.ts
├── .gitignore
├── README.md
└── SECURITY.md
```

### Функционал
- Полная синхронизация с сервером
- Редактирование всех сущностей
- Совместный учёт
- Подписка на обновления
- Подпись кода (электронная)

## Этап 2: Исправление скачивания по кнопке

### Логика выбора дистрибутива
```javascript
// Определение ОС
function getOS() {
    const ua = navigator.userAgent;
    if (/Windows/.test(ua)) return 'windows';
    if (/Macintosh|Mac OS X/.test(ua)) return 'macos';
    if (/Linux/.test(ua)) return 'linux';
    if (/Android/.test(ua)) return 'android';
    return 'windows'; // по умолчанию
}

// Маппинг версий
const VERSIONS = {
    windows: { exe: 'v1.43', msi: 'v1.43' },
    macos: { dmg: 'v1.43', zip: 'v1.43' },
    linux: { deb: 'v1.43', rpm: 'v1.43' },
    android: { apk: 'v1.43' }
};
```

## Этап 3: Подпись файлов

### Для Windows
- SignTool (signtool.exe)
- Использование сертификата с хранилища или файла .pfx

### Для macOS
-_codesign
- Использование сертификата из Keychain

### Для Linux
- gpg --sign
- Использование GPG-ключа

## Этап 4: Улучшение Android-приложения

### Обновления
- Обновление Gradle до актуальной версии
- Обновление Kotlin до 2.0+
- Обновление Compose до stable
- Оптимизация памяти и производительности

### UI/UX улучшения
- Улучшенные анимации
- Better error handling
- Accessibility improvements

## Этап 5: Улучшение веб-кабинета

### Обновления
- Современный дизайн (совместимый с Android)
- Полная функциональность редактирования
- Улучшенная безопасность
- Поддержка offline-режима

## Этап 6: Паритет функций

### Список функций для всех платформ
- [x] Добавление/редактирование мест
- [x] Добавление/редактирование стеллажей
- [x] Добавление/редактирование полок
- [x] Добавление/редактирование контейнеров
- [x] Добавление/редактирование вещей
- [x] Синхронизация с сервером
- [x] Совместный учёт
- [x] Бэкап/восстановление
- [x] Поиск
- [ ] Подпись кода
- [ ] Автообновление

## Приступаю к реализации?
