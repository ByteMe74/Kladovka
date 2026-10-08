<#
    Рисует баннер-превью 1200x630 для ссылки на сайт.

    Зачем он: без og:image ссылка на «Кладовку» в WhatsApp, Telegram или почте
    показывается пустой полосой — без картинки и без описания. У нас в `<head>`
    не было ни одного og-тега, то есть превью не формировался вовсе.

    Что на баннере: настоящий снимок экрана приложения, а не нарисованный макет.
    Тот же принцип, что со скриншотами на странице: человек видит то, что
    действительно будет на телефоне.

    Запуск: powershell -File tools/make-og-image.ps1 -Out social-preview.png
    Из index.php файл тоже генерируется (там та же картинка — см. OG-блок),
    поэтому этот скрипт нужен, чтобы держать в репозитории исходник, из
    которого картинка получается, а не только результат.
#>
param(
    [string]$Out = "social-preview.png",
    [string]$IconUrl = "https://kladovka.dr6ter.ru/icon-512.png",
    # Имя параметра не `$Shot`: переменные в PowerShell не различают регистр,
    # и `$shot = Image::FromFile(...)` молча переписал бы `$Shot`, то есть адрес.
    # Дальше `$shot.Height` обращался бы к свойству строки, и падал.
    [string]$ShotUrl = "https://kladovka.dr6ter.ru/screens/01-main.png"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$W = 1200
$H = 630

function Get-Image($url) {
    $cache = Join-Path ([System.IO.Path]::GetTempPath()) ("og-" + [System.IO.Path]::GetFileNameWithoutExtension($url))
    if (-not (Test-Path $cache)) {
        # Прогресс загрузки не должен попасть в возвращаемое значение: иначе
        # $path станет массивом, и Image::FromFile получит не строку, а вся
        # выведенная Invoke-WebRequest информация. Ошибки — наоборот, наружу.
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $url -OutFile $cache -UseBasicParsing
    }
    # -ErrorAction Stop и принудительная строка: путь всегда строка.
    return [string](Get-Item $cache -ErrorAction Stop).FullName
}

$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Фон — тот же тёмный, что на сайте.
$bg = [System.Drawing.Color]::FromArgb(255, 12, 14, 18)
$g.Clear($bg)

# Мягкое свечение слева, чтобы картинка не стояла плоско на фоне.
$glow = New-Object System.Drawing.Rectangle(-260, 40, 900, 560)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $glow,
    [System.Drawing.Color]::FromArgb(70, 0, 240, 255),
    [System.Drawing.Color]::FromArgb(0, 0, 240, 255),
    35.0)
$g.FillEllipse($brush, $glow)
$brush.Dispose()

# Снимок экрана — справа, в рамке телефона. Пропорции снимка 200:444, то есть
# почти 1:2.2: в прямоугольник по ширине он не влезает, поэтому высота задана
# первой, а ширина считается от неё. Иначе телефон на превью либо вылезает за
# край, либо снимок приходится обрезать по половине списка.
$shotPath = Get-Image $ShotUrl
$shotImg = [System.Drawing.Image]::FromFile($shotPath)
$innerH = 520
$innerW = [int]($shotImg.Width * ($innerH / $shotImg.Height))
$frameW = $innerW + 40
$frameH = $innerH + 40
$fx = $W - $frameW - 70
$fy = [int](($H - $frameH) / 2)

$frame = New-Object System.Drawing.Rectangle($fx, $fy, $frameW, $frameH)
$frameBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 30, 34, 40))
$g.FillRectangle($frameBrush, $frame)
$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(120, 0, 240, 255)), 2
$g.DrawRectangle($border, $frame)
$border.Dispose()
$g.DrawImage($shotImg, (New-Object System.Drawing.Rectangle(($fx + 20), ($fy + 20), $innerW, $innerH)))
$shotImg.Dispose()

# Иконка приложения.
$iconPath = Get-Image $IconUrl
$iconImg = [System.Drawing.Image]::FromFile($iconPath)
$g.DrawImage($iconImg, (New-Object System.Drawing.Rectangle(80, 76, 104, 104)))
$iconImg.Dispose()

# Текст. Левая часть — от 80 до телефона, то есть примерно 700 px: всё, что
# длиннее, вылезало за рамку.
$titleFont = New-Object System.Drawing.Font("Segoe UI", 72, [System.Drawing.FontStyle]::Bold)
$subFont = New-Object System.Drawing.Font("Segoe UI", 31)
$tagFont = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
$domFont = New-Object System.Drawing.Font("Segoe UI", 22)
$white = [System.Drawing.Color]::FromArgb(255, 240, 245, 250)
$muted = [System.Drawing.Color]::FromArgb(255, 160, 175, 190)
$cyan = [System.Drawing.Color]::FromArgb(255, 0, 240, 255)

$g.DrawString("Кладовка", $titleFont, (New-Object System.Drawing.SolidBrush $white), 78, 210)
$g.DrawString("складской учёт на телефоне", $subFont, (New-Object System.Drawing.SolidBrush $muted), 82, 312)

$tag = "Android · Windows · свой сервер"
$g.DrawString($tag, $tagFont, (New-Object System.Drawing.SolidBrush $cyan), 82, 392)
$g.DrawString("kladovka.dr6ter.ru", $domFont, (New-Object System.Drawing.SolidBrush $muted), 82, 468)

$g.Dispose()
$bmp.Save((Resolve-Path (Split-Path $Out -Parent) -ErrorAction SilentlyContinue).Path + "\" + (Split-Path $Out -Leaf), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "Готово: $Out ($W x $H)"