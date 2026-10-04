# Убирает щиток «Windows защитил ваш компьютер» на ЭТОЙ машине.
#
# Что не так было. Скрипт-первенец клал сертификат только в хранилище
# «Доверенные издатели» и рапортовал об успехе. Щток от этого не исчезал:
# самоподписанный сертификат сам себе корень, и без записи в «Доверенные
# корневые центры сертификации» цепочка не строится. Проверено на файле:
# Get-AuthenticodeSignature давал Status = UnknownError с причиной UntrustedRoot.
# Скрипт говорил «готово», не проверяя, — поэтому ошибка и жила незамеченной.
#
# Что делает этот скрипт. Кладёт публичную часть сертификата в два хранилища:
#   - «Доверенные корневые центры сертификации» (Root) — без него цепочка
#     не доверена и Windows показывает щиток;
#   - «Доверенные издатели» (TrustedPublisher) — без него издатель не
#     считается доверенным для запуска подписанного кода.
# Потом ПРОВЕРЯЕТ результат на реальном файле и печатает фактический статус.
# Если статус не стал Valid, скрипт сообщает об ошибке и код возврата ненулевой.
#
# Чего этот скрипт НЕ делает. Он не убирает щиток на чужих компьютерах.
# Там сертификат никто не проверял, и щиток появляется правильно. Для чужих
# машин есть два пути: OV-сертификат, который выдаёт удостоверяющий центр
# после проверки организации (платно), или публикация в Microsoft Store,
# где подпись и репутацию даёт сама Microsoft (бесплатно, но с review).
#
# Откат: .\trust-kladovka-signing.ps1 -Remove

[CmdletBinding()]
param(
    # Файл с публичной частью сертификата. По умолчанию — лежит рядом со
    # скриптом, но путь вычисляется в теле, а не здесь: в блоке параметров
    # $PSScriptRoot ещё недоступен, и Join-Path падал с пустым аргументом.
    [string]$CertFile,

    # Файл, на котором проверять результат. Без него скрипт не сможет доказать,
    # что щиток снят, и честно скажет, что не проверил.
    [string]$VerifyFile,

    # Машинные хранилища (требуют прав администратора) вместо пользовательских.
    [switch]$Machine,

    # Откат: убрать сертификат из обоих хранилищ.
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $CertFile) {
    $CertFile = Join-Path $PSScriptRoot 'kladovka-code-signing.cer'
}

if (-not (Test-Path $CertFile)) {
    Write-Host "НЕ НАЙДЕН файл сертификата: $CertFile" -ForegroundColor Red
    Write-Host "Положите рядом с этим скриптом kladovka-code-signing.cer."
    exit 1
}

$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CertFile)
$thumb = $cert.Thumbprint

# -Machine меняет все хранилища разом, иначе это машина для текущего
# пользователя. Пользовательские хранилища не требуют прав администратора,
# и для одной машины их достаточно.
$location = if ($Machine) {
    [System.Security.Cryptography.X509Certificates.StoreLocation]::LocalMachine
} else {
    [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser
}
$where = if ($Machine) { 'машинные' } else { 'пользовательские' }

Write-Host "Сертификат: $($cert.Subject)"
Write-Host "Отпечаток:  $thumb"
Write-Host "Хранилища:  $where"
Write-Host ""

# Порядок важен и не случаен: Root даёт доверие к цепочке, TrustedPublisher
# разрешает издателю подписывать код. Без Root второй пункт не имеет смысла,
# поэтому он и шёл один в прошлой версии — щиток оставался.
$targets = @(
    @{ Name = 'Доверенные корневые центры сертификации'; Store = [System.Security.Cryptography.X509Certificates.StoreName]::Root },
    @{ Name = 'Доверенные издатели';                   Store = [System.Security.Cryptography.X509Certificates.StoreName]::TrustedPublisher }
)

if ($Remove) {
    foreach ($t in $targets) {
        $store = New-Object System.Security.Cryptography.X509Certificates.X509Store($t.Store, $location)
        try {
            $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
            $found = $store.Certificates | Where-Object { $_.Thumbprint -eq $thumb }
            if ($found) {
                $store.Remove($found)
                Write-Host "Убрано из «$($t.Name)»." -ForegroundColor Green
            } else {
                Write-Host "В «$($t.Name)» его не было." -ForegroundColor Yellow
            }
            $store.Close()
        } catch {
            Write-Host "НЕ УДАЛОСЬ убрать из «$($t.Name)»: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    Write-Host ""
    Write-Host "Откат сделан."
    exit 0
}

foreach ($t in $targets) {
    $store = New-Object System.Security.Cryptography.X509Certificates.X509Store($t.Store, $location)
    $already = $false
    try {
        $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
        $already = [bool]($store.Certificates | Where-Object { $_.Thumbprint -eq $thumb })
        $store.Close()
    } catch { }

    if ($already) {
        Write-Host "Уже есть: «$($t.Name)»" -ForegroundColor Green
        continue
    }

    try {
        $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
        $store.Add($cert)
        $store.Close()
        Write-Host "Добавлено:  «$($t.Name)»" -ForegroundColor Green
    } catch {
        Write-Host "НЕ УДАЛОСЬ добавить в «$($t.Name)»." -ForegroundColor Red
        Write-Host "Причина: $($_.Exception.Message)"
        if ($Machine) {
            Write-Host "Нужны права администратора: запустите скрипт из окна PowerShell,"
            Write-Host "открытого по правому щелчку от имени администратора."
        } else {
            Write-Host "Либо запустите с ключом -Machine (нужны права администратора),"
            Write-Host "либо повторите без него — должно хватить пользовательских."
        }
        exit 1
    }
}

Write-Host ""

# Проверка результата. Без неё скрипт рапортует о намерении, а не о факте —
# именно так ошибка с одним хранилищем и прожила незамеченной.
if (-not $VerifyFile) {
    # Скрипт лежит в kladovka/ops, exe — в kladovka-desktop/build/dist, то есть
    # на два уровня выше, а не на один: первый вариант искал kladovka/kladovka-desktop
    # и молча пропускал проверку, рапортуя «сертификаты добавлены».
    $guess = Join-Path $PSScriptRoot '..\..\kladovka-desktop\build\dist\Kladovka.exe'
    if (Test-Path $guess) { $VerifyFile = $guess }
}

if (-not $VerifyFile -or -not (Test-Path $VerifyFile)) {
    Write-Host "Проверить результат не на чем: не найден подписанный Kladovka.exe." -ForegroundColor Yellow
    Write-Host "Сертификаты добавлены, но щиток на этой машине НЕ ПРОВЕРЕН."
    Write-Host "Перезапустите проводник и запустите файл."
    Write-Host ""
    Write-Host "Проверить потом можно так:"
    Write-Host "  Get-AuthenticodeSignature .\Kladovka.exe | Select-Object Status, StatusMessage"
    exit 0
}

Write-Host "Проверяю на реальном файле: $VerifyFile"
$sig = Get-AuthenticodeSignature $VerifyFile
Write-Host "  подписант: $($sig.SignerCertificate.Subject)"
Write-Host "  статус:    $($sig.Status)"

if ($sig.Status -eq 'Valid') {
    Write-Host ""
    Write-Host "Подпись доверена системой — щитка на этой машине не будет." -ForegroundColor Green
    Write-Host "Перезапустите проводник (или машину), если окно уже открыто."
    exit 0
}

Write-Host ""
Write-Host "Статус НЕ Valid. Причина: $($sig.StatusMessage)" -ForegroundColor Red
Write-Host ""
Write-Host "Значит, щиток останется. Что обычно мешает:"
Write-Host "  - сертификат в корневых есть, а подписан не он (сверьте отпечаток);"
Write-Host "  - политика SmartScreen/контроль целостности включены политикой домена;"
Write-Host "  - файл скачан из интернета и несёт метку источника из браузера;"
Write-Host "  - подпись просрочена (NotTimeNested/Expired) или отозвана."
exit 1