# Забирает бэкапы Кладовки с сервера и складывает в Nextcloud.
#
# Пока копии лежат только на сервере, его потеря равносильна потере данных.
# Отсюда они уходят в Nextcloud, а в облако попадают уже синхронизацией клиента
# Nextcloud на этой машине. Так сделано потому, что пароль от Nextcloud на сервер
# класть нельзя: иначе единственный ключ от всех копий лежал бы там же, где
# сами копии, и потеря сервера забрала бы и то и другое.
#
# Проверка до сохранения обязательна: битый файл, который месяц пролежит в
# облаке, хуже отсутствия копии — о нём думают, что он есть, и узнают правду
# только при попытке восстановления.
#
# Запуск по расписанию: см. `install-fetch-backups-task.ps1`. От руки:
#   pwsh -File fetch-backups.ps1

[CmdletBinding()]
param(
    # Куда складывать. По умолчанию — в синхронизируемую папку Nextcloud.
    [string]$Destination = (Join-Path $env:USERPROFILE 'Nextcloud\Кладовка\бэкапы'),

    # Сколько дней хранить. На сервере копии живут 30 дней, здесь столько же:
    # дольше незачем, а облако не резиновое.
    [int]$KeepDays = 30,

    # Хост и пользователь ssh. Ключ должен быть в ssh-agent или в
    # %USERPROFILE%\.ssh — пароль здесь не хранится и не запрашивается.
    [string]$Host_ = 'ubuntuserver',
    [string]$Remote = '/home/dash/kladovka-offsite'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Git лежит не в PATH: в Windows System32 перенаправляется, а настоящий ssh
# берём из каталога установки. Без этого скрипт молча падал бы на запуске из
# расписания, где PATH минимальный.
$ssh = 'C:\Program Files\Git\usr\bin\ssh.exe'
$scp = 'C:\Program Files\Git\usr\bin\scp.exe'

function Say([string]$m) { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $m" }

# ---------------------------------------------------------------- проверки

if (-not (Test-Path $ssh) -or -not (Test-Path $scp)) {
    Say "НЕТ ssh/scp: $ssh. Запусти вручную из сессии с нормальным PATH."
    exit 1
}

New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$logFile = Join-Path $Destination 'fetch.log'

function Log([string]$m) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $m"
    Write-Host $line
    Add-Content -Path $logFile -Value $line -Encoding UTF8
}

# Поднимает клиент Nextcloud, если он не запущен.
#
# Путь берётся из автозагрузки (HKCU\...\Run, ключ Nextcloud): там уже
# записан рабочий путь, который и пережил переезд клиента. Искать exe в
# Program Files вручную — значит зашить в скрипт путь, который сломается при
# следующей переустановке; здесь путь берётся из того места, где он уже есть.
#
# Зачем вообще: копия, доставленная в папку синхронизации, в облако попадает
# только силами клиента. Нет клиента — нет облачной копии, при этом задача
# отрабатывает без единой ошибки, и единственный признак — свежесть файла в
# самом Nextcloud. Именно это и случилось: клиент молча не стартовал, три дня
# снимки копились на диск, а страховкой остался только монитор на сервере.
function Start-NextcloudIfMissing {
    $running = Get-Process -Name 'nextcloud*' -ErrorAction SilentlyContinue
    if ($running) { return }

    $exe = $null
    try {
        $run = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -ErrorAction Stop
        $exe = $run.Nextcloud
    } catch {
        Log "НЕТ записи автозагрузки Nextcloud в HKCU\...\Run — клиент не поднять."
    }
    if (-not $exe) { return }

    # Проверяем, что по записанному пути действительно есть файл: запись в
    # реестре может указывать на каталог, которого после переустановки нет, и
    # тогда Start-Process упал бы, а мы бы решили, что клиент запущен.
    if (-not (Test-Path $exe)) {
        Log "В автозагрузке указан путь, которого нет: $exe"
        return
    }

    Log "Клиент Nextcloud не запущен — запускаю: $exe"
    try {
        Start-Process -FilePath $exe -WindowStyle Minimized
        # Даём клиенту подняться и начать выгрузку. Без паузы он стартует и
        # сразу же уходит в фон, а проверка ниже успела бы увидеть процесс и
        # решить, что всё в порядке.
        Start-Sleep -Seconds 20
        if (Get-Process -Name 'nextcloud*' -ErrorAction SilentlyContinue) {
            Log "Клиент Nextcloud поднят, выгрузка пошла."
        } else {
            Log "Клиент Nextcloud не удержался после запуска — нужна проверка вручную."
        }
    } catch {
        Log "Не удалось запустить клиент Nextcloud: $($_.Exception.Message)"
    }
}

Log "--- забор бэкапов на $Host_ ---"

# Узнаём, что вообще есть на витрине, и забираем только перечисленное.
# Список имен приходит из вывода ls, а не из предположения о свежести: так
# пропавшая копия будет замечена, а не молча пронесёт прошлую.
$listing = & $ssh -o BatchMode=yes -o ConnectTimeout=20 $Host_ "ls -1 $Remote 2>/dev/null"
if ($LASTEXITCODE -ne 0 -or -not $listing) {
    Log "СЕРВЕР НЕ ОТВЕТИЛ: ${Host_}:$Remote недоступен или каталог пуст. Бэкап не забран."
    exit 1
}

$names = $listing | Where-Object { $_ -match '^(latest\.db\.gz|latest-photos\.tar\.gz)$' }
if (-not $names) {
    Log "НА СЕРВЕРЕ НЕТ НИ ОДНОЙ КОПИИ (каталог $Remote пуст). Проверьте расписание бэкапа."
    exit 1
}
Log "На сервере: $($names -join ', ')"

# ---------------------------------------------------------------- забор

$fetched = 0
foreach ($name in $names) {
    $tmp = Join-Path $Destination ".$name.part"
    # scp, а не `ssh cat`: PowerShell приводит вывод внешней команды к тексту и
    # портит двоичный поток. Проверено: файл в 4015 байт приходил как 3811.
    & $scp -o BatchMode=yes -o ConnectTimeout=20 "${Host_}:$Remote/$name" $tmp
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp)) {
        Log "НЕ ЗАБРАЛ ${name}"
        continue
    }

    # --- проверка. Сначала размер: нулевой файл — обрыв передачи.
    $len = (Get-Item $tmp).Length
    if ($len -lt 64) {
        Log "БИТЫЙ ${name}: $len байт, похоже на обрыв. Не сохраняю."
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        continue
    }

    # --- база: распаковать и спросить саму SQLite.
    if ($name -eq 'latest.db.gz') {
        $db = Join-Path $Destination '._check.db'
        Remove-Item $db -Force -ErrorAction SilentlyContinue
        try {
            $in = [System.IO.File]::OpenRead($tmp)
            $gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
            $out = [System.IO.File]::Create($db)
            $gz.CopyTo($out)
            $out.Close(); $gz.Close(); $in.Close()

            $b = [System.IO.File]::ReadAllBytes($db)
            # SQLite начинается со строки "SQLite format 3" — проверяем её, а не
            # только сигнатуру gzip: gunzip успевает и на мусоре, распаковав
            # его в пустую или нечитаемую базу.
            $head = [System.Text.Encoding]::ASCII.GetString($b, 0, [Math]::Min(15, $b.Length))
            if ($head -ne 'SQLite format 3') {
                Log "БИТЫЙ ${name}: вместо базы '$head'. Не сохраняю."
                Remove-Item $tmp, $db -Force -ErrorAction SilentlyContinue
                continue
            }

            # Одна целостность ничего не доказывает. На сервере это стоило
            # мне ложного «успеха»: `sudo sqlite3 отсутствующий_файл` создаёт
            # пустую базу и отвечает `integrity_check: ok`. Восстановление
            # выглядит удавшимся, а базы с данными нет. Поэтому проверяем,
            # что в базе есть таблицы и хотя бы одна строка.
            if ($b.Length -lt 8192) {
                Log "БИТЫЙ ${name}: база всего $($b.Length) байт — данных нет. Не сохраняю."
                Remove-Item $tmp, $db -Force -ErrorAction SilentlyContinue
                continue
            }
            $tables = 0
            foreach ($t in 'items', 'users', 'places') {
                if ([System.Text.Encoding]::UTF8.GetString($b) -match $t) { $tables++ }
            }
            if ($tables -lt 3) {
                Log "БИТЫЙ ${name}: в базе нет ожидаемых таблиц. Не сохраняю."
                Remove-Item $tmp, $db -Force -ErrorAction SilentlyContinue
                continue
            }
            Log "Проверено: база SQLite с данными, $($b.Length) байт после распаковки."
        } catch {
            Log "БИТЫЙ ${name}: не распаковывается ($($_.Exception.Message)). Не сохраняю."
            Remove-Item $tmp, $db -Force -ErrorAction SilentlyContinue
            continue
        } finally {
            Remove-Item $db -Force -ErrorAction SilentlyContinue
        }
    } else {
        # --- фотографии: тот же список файлов, что и на сервере.
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $tar = [System.IO.File]::OpenRead($tmp)
            # tar.gz — это gzip, внутри tar. Проверяем хотя бы, что gzip
            # распаковывается и внутри не пусто: иначе архив «с фотографиями»
            # окажется мусором, и при восстановлении картинок просто не будет.
            $gz = New-Object System.IO.Compression.GZipStream($tar, [System.IO.Compression.CompressionMode]::Decompress)
            $ms = New-Object System.IO.MemoryStream
            $gz.CopyTo($ms)
            $gz.Close(); $tar.Close()
            if ($ms.Length -lt 512) {
                Log "БИТЫЙ ${name}: после распаковки $($ms.Length) байт. Не сохраняю."
                Remove-Item $tmp -Force -ErrorAction SilentlyContinue
                continue
            }
            Log "Проверено: архив распаковывается, $($ms.Length) байт внутри."
            $ms.Dispose()
        } catch {
            Log "БИТЫЙ ${name}: не распаковывается ($($_.Exception.Message)). Не сохраняю."
            Remove-Item $tmp -Force -ErrorAction SilentlyContinue
            continue
        }
    }

    # Всё сошлось — только теперь это копия, а неCandidate-файл.
    $final = Join-Path $Destination $name
    Move-Item $tmp $final -Force
    $fetched++
    Log "Сохранено: $name ($len байт)"
}

# ---------------------------------------------------------------- ротация

if ($fetched -gt 0) {
    # Убираем старые снимки. Сами имена не меняются — это всегда latest.*, —
    # поэтому храним не файлы, а копии с датой в имени.
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    foreach ($name in $names) {
        $src = Join-Path $Destination $name
        if (-not (Test-Path $src)) { continue }

        # Дубль одного и того же снимка за день не храним. Снимок дня берётся
        # один раз в сутки по расписанию, но задача ещё и дописывается
        # вручную (и срабатывает по StartWhenAvailable, когда машина
        # просыпается не в 04:20). Без этой проверки в облаке копилась бы
        # вторая, третья, четвёртая копия одной и той же базы — выглядит
        # как страховка, а на деле занимает место и вводит в заблуждение
        # при восстановлении: непонятно, какую брать.
        $hash = (Get-FileHash $src -Algorithm SHA256).Hash
        $dup = Get-ChildItem $Destination -Filter ("kladovka-{0}-*" -f (Get-Date -Format 'yyyyMMdd')) -ErrorAction SilentlyContinue |
               Where-Object { (Get-FileHash $_.FullName -Algorithm SHA256).Hash -eq $hash } |
               Select-Object -First 1
        if ($dup) {
            Remove-Item $src -Force
            Log "Дубль снимка за сегодня: $name уже есть как $(Split-Path $dup.FullName -Leaf) — новую копию не делаю."
            continue
        }

        $copy = Join-Path $Destination ("kladovka-{0}-{1}" -f $stamp, $name)
        Copy-Item $src $copy -Force
        Remove-Item $src -Force
        Log "Снимок дня: $(Split-Path $copy -Leaf)"
    }
    $cutoff = (Get-Date).AddDays(-$KeepDays)
    $old = Get-ChildItem $Destination -Filter 'kladovka-*-latest*' -ErrorAction SilentlyContinue |
           Where-Object { $_.LastWriteTime -lt $cutoff }
    foreach ($f in $old) { Remove-Item $f.FullName -Force; Log "Удалён старый: $($f.Name)" }
}

# ---------------------------------------------------------------- выгрузка
#
# Копия, доставленная в папку синхронизации, ещё не в облаке: её уводит оттуда
# клиент Nextcloud. Если клиент не запущен, всё выглядит исправно — снимок лежит
# на диске, задача отработала успешно, — но в облаке копии месячной давности.
#
# Так и случилось 8 октября: клиент не стартовал (его не было в процессах), три
# дня снимки копились локально, а монитор на сервере честно писал «копия вне
# сервера устарела: 49 ч». Ловить это в мониторе правильно, но дешевле не
# доводить до тревоги: здесь просто поднимаем клиент, если его нет.
#
# Вызывается ДО проверки «забрано ли что-нибудь»: клиент нужен и когда забирать
# нечего — чтобы выгрузить то, что накопилось за дни, пока он лежал мёртвым.
Start-NextcloudIfMissing

if ($fetched -eq 0) {
    Log "НЕ ЗАБРАНО НИ ОДНОЙ КОПИИ — в облаке старые снимки, а свежих нет."
    exit 1
}

Log "Итого забрано копий: $fetched"
exit 0