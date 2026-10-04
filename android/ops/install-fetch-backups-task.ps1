# Ставит задачу Windows: забирать бэкапы Кладовки с сервера в Nextcloud.
#
# Скрипт на сервере снимает копию в 3:30. Забрать её надо позже: в 4:20. Если
# совпадёт по времени, может выйти так, что забор придёт раньше бэкапа и заберёт
# вчерашнюю копию — формально всё в порядке, свежесть неизвестна. Полчаса
# разрыва гарантируют, что копия уже есть.
#
# Запуск под своей учётной записью: ключ ssh лежит в %USERPROFILE%\.ssh, и при
# запуске от SYSTEM или другого пользователя ключ не найдётся. Пароля в задаче
# нет — он и не нужен, вход по ключу.

[CmdletBinding()]
param(
    [Parameter()][string]$TaskName = 'Кладовка — забор бэкапов в Nextcloud',
    [string]$Time = '04:20',
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Имя задачи без двоеточия: планировщик Windows его не принимает и отвечает
# «Параметр задан неверно» — причём не на имени файла, а на всей регистрации,
# так что ошибка выглядит не related к тому, что на самом деле не так.
$script = Join-Path $PSScriptRoot 'fetch-backups.ps1'

if ($Remove) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Задача «$TaskName» снята."
    exit 0
}

if (-not (Test-Path $script)) { throw "Не найден $script" }

$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`"" `
    -WorkingDirectory (Split-Path $script)

$trigger = New-ScheduledTaskTrigger -Daily -At ([datetime]::ParseExact($Time, 'HH:mm', $null))

# Ходить в интернет и читать Nextcloud нужно всем пользователям: задача от
# нашего имени, а каталог синхронизации общий. Средний приоритет — чтобы
# восстановление не ждало нагрузки.
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType Interactive -RunLevel Limited

# Ноутбук мог быть выключен в момент запуска. Без StartWhenAvailable
# пропущенный запуск просто потерялся бы, а это ровно тот случай, когда забор
# нужен сильнее всего.
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
    -DontStopIfGoingOnBatteries -AllowStartIfOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 15) `
    -MultipleInstances IgnoreNew

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Principal $principal -Settings $settings `
    -Description 'Забирает бэкап Кладовки с сервера и складывает в Nextcloud. Задача нужна, потому что без неё копии лежат только на сервере, и его потеря равна потере данных.'

Write-Host "Задача «$TaskName» создана: ежедневно в $Time, от имени $env:USERNAME."
Write-Host "Проверка:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Отмена:   .\install-fetch-backups-task.ps1 -Remove"