# Сборка

Два приложения из одного репозитория собираются независимо.

| Что | Проект | Что получается |
|---|---|---|
| Android | `kladovka/` | `app/build/outputs/apk/release/app-release.apk` |
| Windows | `kladovka-desktop/` | `kladovka-desktop/build/dist/Kladovka.exe` |

---

## Android

### Что нужно на машине

JDK 21 (`JAVA_HOME`), Android SDK и Gradle. Путь к SDK берётся из
`kladovka/local.properties` — файл машинный, в git не лежит:

```properties
sdk.dir=C\:\\Android
```

На этой машине SDK установлен в `C:\Android` из командных инструментов
Google: `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`
(`compileSdk`/`targetSdk` в проекте — 35).

Если падает «Package … not found», установить недостающее:

```powershell
C:\Android\cmdline-tools\latest\bin\android.exe sdk --sdk=C:\Android install "platforms;android-35" "build-tools;35.0.0"
```

### Сборка

```powershell
cd kladovka
.\gradlew.bat :app:assembleRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`.

### Ключ подписи

Сборка без ключа не проходит: `:app:validateSigningRelease` требует файл
`~/kladovka-release.jks` (то есть `C:\Users\Night\kladovka-release.jks`),
алиас `kladovka-release`, пароли — из `gradle.properties`
(`kladovka.store.pass`, `kladovka.key.pass`) или переменных окружения
`KLADOVKA_STORE_PASS` / `KLADOVKA_KEY_PASS`.

**Ключ сейчас отсутствует.** Установленная на телефонах версия v1.46 подписана
им, и Android не поставит новую сборку поверх старой, если ключ другой:
подпись не совпадёт. Поэтому новый релиз нельзя выпустить, пока ключ не
найден, и выпускать его под новым ключом нельзя — придётся удалять старую
версию вместе с локальной базой и фото.

Отладка: `.\gradlew.bat :app:assembleDebug` — APK подписывается отладочным
ключом (`CN=Android Debug`), ставится только после удаления release-версии.

### Проверка подписи

```powershell
C:\Android\build-tools\35.0.0\apksigner.bat verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

Ожидается `Verified using v2 scheme: true`, v1/v3 — `false`: при `minSdk 26`
подпись JAR (v1) не нужна, а v3 AGP по умолчанию не включает. Имя владельца
сертификата должно совпадать с исходным, а не `CN=Android Debug`.

### Выкладка на сервер

1. Поднять `versionCode`/`versionName` в `app/build.gradle.kts`.
   `versionCode` обязан быть больше, чем у сборки на сервере, — иначе
   `latestApk` в `api.php` отдаст старую. Схема: `versionCode = major*100 + minor`,
   чтобы `v1.46 → 146` правильно сравнивалось с установленной версией.
2. Положить файл в корень сайта под именем `Kladovka-v<versionName>.apk`.
   Поиск идёт по `Kladovka-*.apk`, версия выбирается как `(major, minor)` —
   правило обязано совпадать в `api.php`, `index.php`,
   `cabinet/index.php` и `download-handler.php`.
3. Проверить снаружи: `https://kladovka.dr6ter.ru/api.php?action=latestApk`
   отдаёт новую версию, размер и md5 совпадают с файлом.

---

## Windows

```powershell
cd kladovka-desktop
gradle test singleExe
```

Тесты — в модуле `shared`, они же покрывают сортировку и настройки.
EXE: `build\dist\Kladovka.exe` (портативный, установщика нет; данные в
`~/.kladovka`).

Настоящий установщик (`gradle createDistributable`, jpackage) собирается, но
пока не выкладывается. `packageMsi` не работает: не установлен WiX 3
(`light.exe`).