# Разово убирает предупреждение «Неизвестный издатель» у Кладовки на ЭТОЙ машине.
#
# Зачем это нужно. Приложение подписано настоящей подписью кода, но
# самоподписанным сертификатом: удостоверяющий центр за деньги выдаёт только
# тем, чью организацию он проверил, а самоподписанный центр никто не проверял.
# Windows поэтому показывает щиток. Это не поломка подписи — подпись верна, просто
# корень ей не доверяет.
#
# Что делает скрипт. Кладёт публичную часть сертификата в хранилище
# «Доверенные издатели» на этой машине. После этого Windows считает издателя
# известным и щиток не показывает. Действие разовое и только для этой машины:
# на чужих компьютерах щиток останется, и это правильно — там файл никто не
# проверял.
#
# Требует прав администратора: хранилище доверенных издателей общее для машины.
#
# Откат: удалить сертификат из хранилища (см. в конце файла).

[CmdletBinding()]
param(
    # Файл с публичной частью сертификата. Лежит рядом со скриптом.
    [string]$CertFile = (Join-Path $PSScriptRoot 'kladovka-code-signing.cer'),

    # Откат: убрать сертификат из доверенных издателей.
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not (Test-Path $CertFile)) {
    Write-Host "НЕ НАЙДЕН файл сертификата: $CertFile" -ForegroundColor Red
    Write-Host "Положите рядом с этим скриптом kladovka-code-signing.cer."
    exit 1
}

# Читаем отпечаток, чтобы не добавлять один сертификат дважды.
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CertFile)
$thumb = $cert.Thumbprint
Write-Host "Сертификат: $($cert.Subject)"
Write-Host "Отпечаток:  $thumb"
Write-Host "Действует:  $($cert.NotBefore.ToString('dd.MM.yyyy')) — $($cert.NotAfter.ToString('dd.MM.yyyy'))"

$store = New-Object System.Security.Cryptography.X509Certificates.X509Store(
    [System.Security.Cryptography.X509Certificates.StoreName]::TrustedPublisher,
    [System.Security.Cryptography.X509Certificates.StoreLocation]::LocalMachine)

if ($Remove) {
    $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
    $found = $store.Certificates | Where-Object { $_.Thumbprint -eq $thumb }
    if ($found) {
        $store.Remove($found)
        Write-Host "Удалён из доверенных издателей." -ForegroundColor Green
    } else {
        Write-Host "В доверенных издателях его не было — нечего удалять." -ForegroundColor Yellow
    }
    $store.Close()
    exit 0
}

# Проверяем, не доверяем ли мы уже этому издателю.
$store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
$already = $store.Certificates | Where-Object { $_.Thumbprint -eq $thumb }
$store.Close()

if ($already) {
    Write-Host "Этот издатель уже в доверенных — щитка быть не должно." -ForegroundColor Green
    Write-Host "Если он всё равно появляется, перезапустите проводник или машину."
    exit 0
}

# Добавляем. Требует прав администратора: без них запись в машинное хранилище
# падает с отказом в доступе, и это ожидаемо.
try {
    $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
    $store.Add($cert)
    $store.Close()
} catch {
    Write-Host ""
    Write-Host "НЕ УДАЛОСЬ добавить в доверенные издатели." -ForegroundColor Red
    Write-Host "Причина: $($_.Exception.Message)"
    Write-Host ""
    Write-Host "Нужны права администратора: запустите этот скрипт из окна,"
    Write-Host "открытого от имени администратора."
    exit 1
}

Write-Host ""
Write-Host "Готово. Издатель Kladovka добавлен в доверенные на этой машине." -ForegroundColor Green
Write-Host "Перезапустите проводник (или машину), чтобы щиток исчез."
Write-Host ""
Write-Host "Откат: .\trust-kladovka-signing.ps1 -Remove"