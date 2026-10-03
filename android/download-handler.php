<?php
// Кладовка — раздача сборки под ОС посетителя.
//
// Зачем этот файл: лендинг отдавал всем один и тот же APK, поэтому с десктопа
// скачивался файл, который там не запустится. Обработчик смотрит на User-Agent
// (или на ?platform=) и отдаёт подходящий дистрибутив: .exe для Windows,
// .apk для Android, .dmg для macOS, .deb/.rpm для Linux.
//
// По умолчанию — 302 на сам файл, чтобы кнопку «Скачать» можно было повесить
// на href без единой строки JS. Метаданные тем же форматом, что у
// api.php?action=latestApk (versionCode/versionName/url/md5/size), отдаются
// через ?format=json.
declare(strict_types=1);

/**
 * Расширения по ОС, в порядке предпочтения.
 *
 * Порядок важен: User-Agent у Android и iOS содержит «Linux» и «Mac OS X»,
 * поэтому мобильные ветки стоят раньше настольных. В списке для macOS не
 * осталось .app.tar.gz из прошлой версии файла — такого артефакта сборка не
 * производит, а регулярка в kladovkaCollect всё равно не разобрала бы
 * двойное расширение.
 */
const KLADOVKA_EXT = [
    'android' => ['apk'],
    'ios'     => ['ipa'],
    'windows' => ['exe', 'msi'],
    'macos'   => ['dmg'],
    'linux'   => ['deb', 'rpm'],
];

/** ОС по User-Agent. */
function kladovkaDetectOs(string $ua): string
{
    if (preg_match('/Android/i', $ua)) {
        return 'android';
    }
    if (preg_match('/iPhone|iPad|iPod/i', $ua)) {
        return 'ios';
    }
    if (preg_match('/Windows NT|Win64|Win32/i', $ua)) {
        return 'windows';
    }
    if (preg_match('/Macintosh|Mac OS X/i', $ua)) {
        return 'macos';
    }
    if (preg_match('/Linux|X11/i', $ua)) {
        return 'linux';
    }
    return 'unknown';
}

/**
 * Где искать сборки.
 *
 * api.php заглядывает в <root>/download и в <root>, кабинет лежит на уровень
 * выше лендинга — раскладка зависит от того, как обращение разложено по
 * хостингу. Поэтому проверяем все три точки вместо одной: обработчик должен
 * работать и в корне сайта, и в подкаталоге.
 */
function kladovkaDirs(): array
{
    $dirs = [];
    foreach ([__DIR__, __DIR__ . '/download', dirname(__DIR__)] as $d) {
        $real = realpath($d);
        if ($real !== false) {
            $dirs[$real] = true;
        }
    }
    return array_keys($dirs);
}

/**
 * Карта «расширение -> список сборок этого расширения».
 *
 * Сканирует каталог через scandir и разбирает имя регуляркой, а не через glob:
 * glob на Linux различает регистр, а файлы называются то kladovka-v1.2.apk
 * (как в репозитории), то Kladovka-v1.2.apk (как на сервере). Флаг /i снимает
 * вопрос целиком, заодно видны обе схемы разделителей: -v, _v и пробел.
 *
 * md5 здесь не считается — на каждый файл это лишние мегабайты чтения; он нужен
 * только отобранному, см. kladovkaLatest.
 */
function kladovkaCollect(array $dirs): array
{
    $out = [];
    foreach ($dirs as $dir) {
        $entries = scandir($dir);
        if ($entries === false) {
            continue;
        }
        foreach ($entries as $entry) {
            if ($entry === '' || $entry[0] === '.') {
                continue;
            }
            $path = $dir . '/' . $entry;
            if (!is_file($path)) {
                continue;
            }
            $m = [];
            if (!preg_match('/^kladovka[-_ ]v(\d+)(?:\.(\d+))?.*\.([A-Za-z0-9]+)$/i', $entry, $m)) {
                continue;
            }
            $out[strtolower($m[3])][] = [
                'path'  => $path,
                'name'  => $entry,
                'major' => (int)$m[1],
                'minor' => (int)($m[2] ?? 0),
                'size'  => filesize($path),
                'mtime' => filemtime($path),
            ];
        }
    }
    return $out;
}

/**
 * Самая свежая сборка для одного расширения или null, если такой нет.
 *
 * Сортируем по версии из имени, а не по дате файла: залитый заново старый APK
 * не должен считаться обновлением. При равной версии берём более свежий файл.
 */
function kladovkaLatest(array $files, string $ext): ?array
{
    if (empty($files[$ext])) {
        return null;
    }
    $best = null;
    foreach ($files[$ext] as $f) {
        if ($best === null
            || $f['major'] > $best['major']
            || ($f['major'] === $best['major'] && $f['minor'] > $best['minor'])
            || ($f['major'] === $best['major'] && $f['minor'] === $best['minor'] && $f['mtime'] > $best['mtime'])) {
            $best = $f;
        }
    }
    if ($best !== null) {
        $best['ext'] = $ext;
        // Тот же формат versionCode, что у api.php?action=latestApk:
        // приложение сравнивает эти числа между собой.
        $best['versionCode'] = $best['major'] * 100 + $best['minor'];
        $best['versionName'] = 'v' . $best['major'] . '.' . $best['minor'];
        $best['md5'] = md5_file($best['path']);
    }
    return $best;
}

/**
 * Публичный URL файла, если он лежит внутри DOCUMENT_ROOT, иначе null.
 *
 * null означает «переадресовать нечем» — файл найден вне публичной части
 * (например, обработчик положили в подкаталог, а сборки лежат рядом с ним), и
 * отдавать придётся потоком.
 */
function kladovkaUrl(string $path): ?string
{
    $docRoot = $_SERVER['DOCUMENT_ROOT'] ?? '';
    if ($docRoot === '') {
        return null;
    }
    $real = realpath($path);
    $doc = realpath($docRoot);
    if ($real === false || $doc === false) {
        return null;
    }
    $doc = rtrim(str_replace('\\', '/', $doc), '/') . '/';
    $real = str_replace('\\', '/', $real);
    if (strncmp($real, $doc, strlen($doc)) !== 0) {
        return null;
    }
    $rel = implode('/', array_map('rawurlencode', explode('/', substr($real, strlen($doc)))));
    $host = $_SERVER['HTTP_HOST'] ?? '';
    if ($host === '') {
        return '/' . $rel;
    }
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    return $scheme . '://' . $host . '/' . $rel;
}

/** Ответ с ошибкой: JSON для ?format=json, иначе страница для браузера. */
function kladovkaFail(string $message, int $code): void
{
    $format = strtolower(trim((string)($_GET['format'] ?? '')));
    http_response_code($code);
    if ($format === 'json') {
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE);
        exit;
    }
    $safe = htmlspecialchars($message, ENT_QUOTES, 'UTF-8');
    $known = implode(', ', array_keys(KLADOVKA_EXT));
    header('Content-Type: text/html; charset=utf-8');
    echo <<<HTML
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Кладовка — загрузка недоступна</title>
<style>body{font:16px/1.6 system-ui,-apple-system,Segoe UI,sans-serif;background:#06090f;color:#e0e6ed;margin:0;padding:48px 24px}h1{font-size:24px;margin:0 0 12px}p{max-width:60ch;color:#a8b6c4}code{color:#00f0ff}a{color:#00f0ff}</style>
</head>
<body>
<h1>Не получилось подготовить файл</h1>
<p>$safe</p>
<p>Поддерживаемые платформы: <code>$known</code>. Можно указать вручную:
<code>?platform=windows</code>, <code>?platform=android</code>.</p>
<p><a href="/">Вернуться на главную</a></p>
</body>
</html>
HTML;
    exit;
}

// ===== определение платформы =====

$os = kladovkaDetectOs($_SERVER['HTTP_USER_AGENT'] ?? '');
$requested = strtolower(trim((string)($_GET['platform'] ?? '')));
if ($requested !== '') {
    // Значение попадает только в isset() по белому списку; в текст ошибки не идёт.
    if (!preg_match('/^[a-z]+$/', $requested) || !isset(KLADOVKA_EXT[$requested])) {
        kladovkaFail('Неизвестная платформа.', 400);
    }
    $os = $requested;
}

// ===== поиск файла =====

$files = kladovkaCollect(kladovkaDirs());

$pick = null;
foreach (KLADOVKA_EXT[$os] ?? [] as $ext) {
    $pick = kladovkaLatest($files, $ext);
    if ($pick !== null) {
        break;
    }
}
$servedFor = $os;

// Сборки под эту ОС на сервере нет. Отдавать 404 — значит показать посетителю
// «битую» ссылку; отдаём APK и честно помечаем подмену в заголовке, чтобы это
// было видно и в логах, и в ответе ?format=json.
if ($pick === null) {
    $pick = kladovkaLatest($files, 'apk');
    $servedFor = 'android';
}
if ($pick === null) {
    kladovkaFail(
        'На сервере нет ни одной сборки. Положите kladovka-v<версия>.apk рядом с этим файлом '
        . 'или в подкаталог download.',
        404
    );
}

header('X-Kladovka-Os: ' . $os);
header('X-Kladovka-Served: ' . $servedFor);

// ===== ответ =====

$format = strtolower(trim((string)($_GET['format'] ?? '')));
if ($format === 'json') {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'ok'          => true,
        'os'          => $os,
        'servedFor'   => $servedFor,
        'fallback'    => $servedFor !== $os,
        'versionCode' => $pick['versionCode'],
        'versionName' => $pick['versionName'],
        'file'        => $pick['name'],
        'url'         => kladovkaUrl($pick['path']),
        'md5'         => $pick['md5'],
        'size'        => $pick['size'],
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$url = kladovkaUrl($pick['path']);
if ($url !== null) {
    header('Location: ' . $url, true, 302);
    exit;
}

// Файл вне публичной части — переадресовать не на что, отдаём потоком.
header('Content-Type: application/octet-stream');
header('Content-Disposition: attachment; filename="' . $pick['name'] . '"');
header('Content-Length: ' . $pick['size']);
readfile($pick['path']);
exit;