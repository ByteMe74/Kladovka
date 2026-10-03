<?php
// Кладовка — универсальный обработчик загрузок (определяет ОС и раздаёт файлы)
declare(strict_types=1);

// Определяем ОС по User-Agent
function detectOs(): string {
    $ua = $_SERVER['HTTP_USER_AGENT'] ?? '';
    
    if (preg_match('/Windows NT (\d+\.\d+)/i', $ua, $m)) {
        return 'windows';
    }
    if (preg_match('/Macintosh.*Mac OS X (\d+_\d+)/i', $ua, $m)) {
        return 'macos';
    }
    if (preg_match('/Linux.*x86_64/i', $ua, $m)) {
        return 'linux';
    }
    if (preg_match('/Android/i', $ua, $m)) {
        return 'android';
    }
    if (preg_match('/iPhone|iPad/i', $ua, $m)) {
        return 'ios';
    }
    return 'unknown';
}

// Корень проекта
$root = dirname(__DIR__);
$downloadDir = $root . '/download';

// Маппинг ОС -> файлы
$files = [
    'windows' => [
        'msi' => 'Kladovka-v%s.%s.msi',
        'exe' => 'Kladovka-v%s.%s.exe',
    ],
    'macos' => [
        'dmg' => 'Kladovka-v%s.%s.dmg',
        'app' => 'Kladovka-v%s.%s.app.tar.gz',
    ],
    'linux' => [
        'deb' => 'Kladovka-v%s.%s-amd64.deb',
        'rpm' => 'Kladovka-v%s.%s.x86_64.rpm',
    ],
    'android' => [
        'apk' => 'Kladovka-v%s.%s.apk',
    ],
    'ios' => [
        'ipa' => 'Kladovka-v%s.%s.ipa',
    ],
];

// Получаем версию из последнего доступного файла
$versionCode = 0;
$versionName = '0.0';
$foundFile = null;

foreach ($files as $os => $types) {
    foreach ($types as $ext => $pattern) {
        $globPattern = sprintf($pattern, '*');
        $matches = glob($root . '/' . $globPattern);
        if ($matches) {
            $file = max($matches, fn($a, $b) => filemtime($a) <=> filemtime($b));
            if ($foundFile === null || filemtime($file) > filemtime($foundFile)) {
                $foundFile = $file;
                if (preg_match('/v(\d+)\.(\d+)/i', basename($file), $m)) {
                    $versionCode = (int)$m[1] * 100 + (int)$m[2];
                    $versionName = $m[1] . '.' . $m[2];
                }
            }
        }
    }
}

if ($foundFile === null) {
    http_response_code(404);
    header('Content-Type: application/json');
    echo json_encode(['error' => 'Файлы не найдены']);
    exit;
}

// Определяем ОС клиента
$os = detectOs();
$platform = $_GET['platform'] ?? $os;

// Ищем файл для конкретной платформы
$targetFile = null;
if (isset($files[$platform])) {
    foreach ($files[$platform] as $ext => $pattern) {
        $globPattern = sprintf($pattern, preg_replace('/\d+\.\d+/', '*', $versionName));
        $matches = glob($root . '/' . $globPattern);
        if ($matches) {
            $targetFile = max($matches, fn($a, $b) => filemtime($a) <=> filemtime($b));
            break;
        }
    }
}

// Если файл не найден для конкретной платформы, пробуем найти любой доступный
if ($targetFile === null) {
    foreach ($files as $os => $types) {
        foreach ($types as $ext => $pattern) {
            $globPattern = sprintf($pattern, preg_replace('/\d+\.\d+/', '*', $versionName));
            $matches = glob($root . '/' . $globPattern);
            if ($matches) {
                $targetFile = max($matches, fn($a, $b) => filemtime($a) <=> filemtime($b));
                break 2;
            }
        }
    }
}

if ($targetFile === null) {
    http_response_code(404);
    header('Content-Type: application/json');
    echo json_encode(['error' => 'Файл для этой платформы не найден']);
    exit;
}

// Отдаём файл
$scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$host   = $_SERVER['HTTP_HOST'] ?? 'kladovka.dr6ter.ru';
$rel    = '/' . ltrim(str_replace($root, '', $targetFile), '/');
$url    = $scheme . '://' . $host . $rel;

header('Content-Type: application/json');
echo json_encode([
    'ok' => true,
    'url' => $url,
    'versionCode' => $versionCode,
    'versionName' => $versionName,
    'md5' => md5_file($targetFile),
    'size' => filesize($targetFile),
]);
