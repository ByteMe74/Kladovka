<#
    Подписывает Kladovka.exe сертификатом проекта.

    Отдельный скрипт, а не логика внутри Gradle, по двум причинам, обе
    проверены на практике:

      * При запуске из Gradle отпечаток сертификата не доходил до сборки:
        задача видела пустое значение и тихо оставляла файл без подписи.
    * Поиск сертификата в хранилище средствами сборки зависит от того,
      под каким пользователем идёт Gradle, и от того, как передан вывод
        процесса. Скрипт, запущенный обычным образом, работает всегда.

    Ни ключа, ни пароля здесь нет: сертификат берётся из хранилища
    пользователя. Если его нет — скрипт честно сообщает об этом и завершается
    с ошибкой, а не делает вид, что подпись есть.

    Проверяет результат сам: после подписи перечитывает статус файла. Иначе
    знаки «успешно» от signtool ничего не значили бы — файл мог остаться
    неподписанным.
#>
param(
    [Parameter(Mandatory = $true)][string]$Exe,
    [string]$CertName = "Kladovka"
)

if (-not (Test-Path $Exe)) {
    Write-Host "Файл не найден: $Exe" -ForegroundColor Red
    exit 1
}

$cert = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.Subject -match ("CN=" + [regex]::Escape($CertName)) } |
    Select-Object -First 1

if ($null -eq $cert) {
    Write-Host "Сертификат «$CertName» не найден в хранилище пользователя." -ForegroundColor Red
    Write-Host "EXE останется без подписи: Windows покажет щиток." -ForegroundColor Yellow
    exit 2
}

$signtool = @(
    "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe",
    "C:\Program Files (x86)\Windows Kits\10\App Certification Kit\signtool.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $signtool) {
    Write-Host "signtool не найден (Windows SDK не установлен)." -ForegroundColor Red
    exit 3
}

# Метка времени обязательна: без неё подпись перестанет проверяться, когда
# сертификат истечёт, и файл начнёт вести себя так, будто сломан.
& $signtool sign /sha1 $cert.Thumbprint /fd sha256 `
    /tr http://timestamp.digicert.com /td sha256 $Exe
if ($LASTEXITCODE -ne 0) {
    Write-Host "signtool вернул код $LASTEXITCODE" -ForegroundColor Red
    exit 4
}

$sig = Get-AuthenticodeSignature $Exe
Write-Host "Подписант: $($sig.SignerCertificate.Subject)"
Write-Host "Статус:    $($sig.Status)"
Write-Host "Метка времени: $(if ($sig.TimeStamperCertificate) { $sig.TimeStamperCertificate.Subject } else { 'нет' })"

if ($sig.Status -eq "NotSigned") {
    Write-Host "Файл остался без подписи." -ForegroundColor Red
    exit 5
}
Write-Host "Подпись есть." -ForegroundColor Green
exit 0