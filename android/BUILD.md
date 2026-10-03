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

**Ключ от 1.46 утерян.** Его не оказалось ни на этой машине, ни на сервере, ни
в корзине, ни в истории git, ни в снапшотах timeshift (единственный — июнь
2025). Проверены теневые копии и точки восстановления — доступа нет, Windows
sudo отключён. Сертификат 1.46 был `b89c3c77…`, ни один найденный на обеих
машинах keystore ему не соответствует.

3 октября 2026 сгенерирован новый ключ, им подписан релиз **1.47**:

```
CN=Kladovka, OU=Mobile, O=Kladovka, C=RU,  RSA 4096, до 2056 года
SHA-256: 9F:08:8D:20:5B:9D:B6:46:CF:0A:F9:90:39:94:29:13:6F:14:AF:08:4B:7D:A2:88:93:C5:19:8A:3B:84:FC:5D
```

**1.47 не встанет поверх 1.46** — подписи разные. Пользователям нужно сначала
выгрузить данные («Экспорт (бэкап)»), удалить старую версию, поставить новую и
сделать «Импорт (восстановить)». На лендинг для этого добавлено предупреждение,
которое показывается, пока в каталоге лежит APK старше свежего, и исчезает само,
когда старый файл уберут с сервера.

Копии ключа лежат в `~/Documents` и `~/Nextcloud` — но это тот же диск.
**Настоящая защита — загрузить его в облако или на сервер.** Без этого история
повторится, а сменить подпись дешевле: новый ключ ломает обновление у всех.

### Отладка

`.\gradlew.bat :app:assembleDebug` — APK подписывается отладочным ключом
(`CN=Android Debug`), ставится только после удаления release-версии.

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