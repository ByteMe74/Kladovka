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

### Проверка на эмуляторе

Сборка, подпись и совпадение md5 с сервером ещё ничего не говорят о том,
работает ли приложение. До появления эмулятора APK ни разу не запускался.

Эмулятор ставится отдельно:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
C:\Android\cmdline-tools\latest\bin\android.exe sdk --sdk=C:\Android install emulator
C:\Android\cmdline-tools\latest\bin\android.exe sdk --sdk=C:\Android install "system-images;android-35;google_apis;x86_64"
```

Запуск без окна (когда нужно просто проверить, что не падает):

```powershell
C:\Android\emulator\emulator.exe -avd kladovka_test -no-window -no-audio `
  -no-snapshot -gpu swiftshader_indirect -no-boot-anim -accel auto
```

Ждать `sys.boot_completed`, потом ставить и запускать:

```powershell
C:\Android\platform-tools\adb.exe install -r app\build\outputs\apk\release\app-release.apk
C:\Android\platform-tools\adb.exe shell am start -n ru.kladovka/.MainActivity
C:\Android\platform-tools\adb.exe shell dumpsys activity activities | Select-String topResumedActivity
```

Три вещи, которые ловятся только так:

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** означает несовпадение подписей, а не
  поломку сборки. Именно это ждёт того, кто переходит с 1.46 и ниже: там ключ
  другой, и сначала нужно выгрузить данные, удалить старое приложение, поставить
  новое и импортировать.
- **Исключения при старте** видны в `adb logcat -d | Select-String 'FATAL
  EXCEPTION'`.
- **Разрывы слов в кнопках.** На экране 411dp пять вкладок нижней панели дают
  примерно по 82dp на вкладку, и «Контейнеры» не помещалась — подпись
  переносилась на две строки, а панель становилась выше. В диалоге синхронизации
  так же рвалось «Отправить» — посередине слова, на «Отправи» / «ть». Ни тесты,
  ни чтение кода этого не показывают, нужно смотреть на экран.

Координаты для `adb shell input tap` берутся из дерева элементов, а не из
снимка экрана: снимок при просмотре уменьшается, и тапы попадают мимо.

```powershell
C:\Android\platform-tools\adb.exe shell uiautomator dump /sdcard/ui.xml
C:\Android\platform-tools\adb.exe pull /sdcard/ui.xml $env:TEMP\ui.xml
# в ui.xml искать bounds="[x1,y1][x2,y2]" у нужного узла
```

У release-сборки `adb shell run-as ru.kladovka` не работает («package not
debuggable») — базу так не прочитать. Проверить, что записи действительно
сохранились, можно через экспорт: `Экспорт (бэкап)` пишет файл, который видно в
`/sdcard/Download/` и который можно забрать через `adb pull`.

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

Имя файла — **строго** `Kladovka-v<major>.<minor>.apk`. Шаблон с «хвостом»
(`Kladovka-v*.*.apk`) больше не используется: имя вида `Kladovka-v1.0-old.apk`
проходило под 1.00 и «10», то есть считалось за 110 — и оказывалось свежее
собранного `v1.10`. Такое имя теперь просто не сборка, и если в каталоге
осталось только оно, сервер честно отвечает 404.

---

## Windows

```powershell
cd kladovka-desktop
gradle test singleExe
```

Тесты — в модуле `shared`, они же покрывают сортировку, настройки, тексты
ошибок и логику обновления. EXE: `build\dist\Kladovka.exe` (портативный,
установщика нет; данные в `~/.kladovka`).

### Номер версии: три части обязательны

`appVersion` в `build.gradle.kts` — единственное место, откуда берётся версия
для exe, установщика и `VERSIONINFO`. Формат — строго `MAJOR.MINOR.BUILD`:
`jpackage` на двухчастном имени падает с «Correct format: MAJOR.MINOR.BUILD».
Например, `1.1` не собирается, `1.1.0` — да.

Код версии для сравнения с сервером считается как `major*100 + minor`, то есть
**patch в сравнении не участвует**. Практическое следствие: `1.0.0` и `1.0.1`
имеют одинаковый код, и человек, уже скачавший `1.0.0`, обновления не увидит.
Чтобы обновление дошло, поднимать minor.

### Чистая сборка

`gradle singleExe` падал на пустом `build` с «ожидался ровно один uber-jar,
найдено 0». Причина: вход задачи был объявлен как `inputs.file(провайдер)`,
а провайдер читает каталог, который заполняет зависимость — то есть вычислялся
раньше, чем зависимость отработала. Собирать можно было только повторно, когда
jar от предыдущей сборки ещё лежал на месте. Сейчас входом объявлен каталог,
Gradle следит за ним сам, и сборка с нуля проходит.

Остатки при повторной сборке — отдельная история: Compose-плагин называет
uber-jar по версии и старый не перетирает. Если в `build\compose\jars` их
несколько, удалите каталог перед сборкой.

### Проверка запуска

`build\dist\Kladovka.exe` — **не само приложение, а заглушка**. Она
распаковывает рядом лежащую среду, запускает `java` отдельным процессом и
сразу выходит с кодом 0. Поэтому «процесс exe завершился» ничего не значит:
проверять надо тот, у которого окно.

```powershell
Start-Process build\dist\Kladovka.exe
Start-Sleep 25
Get-Process java | Where-Object MainWindowTitle -eq 'Кладовка'
```

Если окно есть — приложение поднялось. Оно открывает диалог входа на сервер,
если логин ещё не сохранён; адрес сервера в нём показан только для чтения.

Убедиться, что на сервере лежит именно этот файл:

```powershell
$local = (Get-FileHash build\dist\Kladovka.exe -Algorithm MD5).Hash.ToLower()
$remote = (Invoke-WebRequest 'https://kladovka.dr6ter.ru/api.php?action=latestExe').Content | ConvertFrom-Json
$local -eq $remote.md5
```

Снять скриншот окна, не спугнув его:

```powershell
# PrintWindow рисует только содержимое окна — в отличие от CopyFromScreen,
# который захватит всё, что лежит поверх
```

### Подпись EXE

Раздаваемый `Kladovka.exe` подписан подписью кода. Сертификат самоподписанный:
`CN=Kladovka, OU=Mobile, O=Kladovka, L=Chelyabinsk, C=RU`, RSA 4096, срок 10 лет,
EKU «Подписывание кода». Отпечаток `AAA225A6C2EC9DDE492A7C57FE47E97CBDACA3B1`.

Подпись ставится с меткой времени DigiCert, поэтому остаётся верной и после
истечения срока самого сертификата.

```powershell
$cert = New-SelfSignedCertificate -Type CodeSigningCert `
  -Subject "CN=Kladovka, OU=Mobile, O=Kladovka, L=Chelyabinsk, S=Chelyabinsk, C=RU" `
  -KeyAlgorithm RSA -KeyLength 4096 -KeyUsage DigitalSignature `
  -CertStoreLocation "Cert:\CurrentUser\My" -NotAfter (Get-Date).AddYears(10) `
  -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3")
& "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe" sign `
  /fd SHA256 /sha1 $cert.Thumbprint /tr http://timestamp.digicert.com /td SHA256 `
  build\dist\Kladovka.exe
```

Проверка **до** того, как сертификату доверять на этой машине:

```powershell
Get-AuthenticodeSignature build\dist\Kladovka.exe | Select-Object Status, StatusMessage
# Status: UnknownError
# StatusMessage: цепочка обработана, но обработка прервана на корневом
#   сертификате, у которого отсутствует отношение доверия с поставщиком доверия
```

`UntrustedRoot` — это не поломка подписи. Подпись верна, метка времени есть,
просто корень ей не доверяет. Defender при этом файле не срабатывает:
проверено `MpCmdRun -Scan`, «found no threats».

#### Что делает скрипт доверия

`kladovka/ops/trust-kladovka-signing.ps1` кладёт сертификат в **два** хранилища:

| Хранилище | Зачем |
|---|---|
| Доверенные корневые центры сертификации (Root) | без него цепочка не доверена |
| Доверенные издатели (TrustedPublisher) | без него издатель не доверен для кода |

Раньше скрипт писал только во второе хранилище и рапортовал об успехе — щиток
от этого не исчезал, и ошибка жила незамеченной. Теперь скрипт **проверяет
результат** на реальном `Kladovka.exe` и возвращает ненулевой код, если статус
не стал `Valid`.

```powershell
# Из окна администратора (для машинных хранилищ), либо без прав для пользовательских:
.\kladovka\ops\trust-kladovka-signing.ps1
# Откат:
.\kladovka\ops\trust-kladovka-signing.ps1 -Remove
```

Проверено на файле: до — `UnknownError`, после — `Valid`, после отката снова
`UnknownError`. Откат проверен запуском, а не обещанием.

#### Почему щиток остаётся на чужих компьютерах

Самоподписанный сертификат не убирает «Неизвестный издатель». Убирает его
только сертификат, выданный удостоверяющим центром после проверки организации:
OV или EV, платно, с документами и подтверждением личности. Это не задача кода.

На чужих компьютерах щиток останется — и это правильно: там файл никто не
проверял, и доверять ему нельзя по определению.

Пути, доступные без кода:

| Путь | Стоимость | Что даёт |
|---|---|---|
| OV-сертификат | платно, ежегодно | щиток снимается на всех машинах |
| Microsoft Store | бесплатно | подпись и репутация Microsoft, щитка нет |
| Подача на проверку SmartScreen | бесплатно | снимает блокировку по репутации, но не «неизвестный издатель» |

Триггер для SmartScreen — не байты файла, а метка источника (`Zone.Identifier`),
которую ставит браузер при скачивании. Файл без этой метки щитка не вызывает.
Это проверено: скачанный через `Invoke-WebRequest` файл метки не несёт и
запускается без вопросов, а с меткой `ZoneId=3` SmartScreen начинает её читать.

---

### Версия

`appVersion = "1.0.0"` в `build.gradle.kts` — единственное место, где версия
задаётся. Из неё же собираются:

- ресурс `kladovka-version.properties` внутри jar (`AppVersion`),
- `VERSIONINFO` заглушки exe,
- имя пакета установщика.

Код версии (`major*100 + minor`, та же шкала, что у сервера и Android)
**вычисляется из `appVersion`**, а не написан рядом руками: две строки со
временем разъездутся, и обновление либо не заметится, либо будет предлагаться
вечно. `slimUberJar` падает, если ресурс с версией не дожил до отжатого jar, —
иначе поломка выглядела бы как «работает, но обновлений нет».

### Обновление на рабочем месте

Приложение само себя не заменяет: exe запущен, и заменить его изнутри нельзя.
Оно один раз тихо спрашивает `latestExe` и, если на сервере есть сборка с
большим кодом, показывает значок в шапке; окно обновления открывает ссылку в
браузере и объясняет, что закрыть Кладовку и заменить файл.

`latestExe` — отдельное действие не из-за красоты: у телефонной сборки своя
шкала (там уже 1.48), и сравнение `148 > 100` предлагало бы обновление всегда.

### Выкладка на сервер

Имя — строго `Kladovka-v<major>.<minor>.exe`, то же правило, что для APK.
Проверка: `https://kladovka.dr6ter.ru/api.php?action=latestExe` отдаёт
`versionCode`, равный вычисленному из `appVersion`, а `md5` совпадает с файлом.

Настоящий установщик (`gradle createDistributable`, jpackage) собирается, но
пока не выкладывается. `packageMsi` не работает: не установлен WiX 3
(`light.exe`).