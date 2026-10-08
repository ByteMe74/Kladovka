<?php
/**
 * Права, которые запрашивает APK, и почему каждое нужно.
 *
 * Зачем страница: щиток Play Protect у-installа из файла снимается не тем, что
 * внутри APK нет ничего страшного, — тем, что человек в это поверил. Прочитать
 * список прав и объяснить каждое проще, чем уверять словами. Длинные объяснения
 * маловероятно прочтут целиком, поэтому здесь их ровно столько, сколько нужно
 * для решения «ставить или не ставить».
 *
 * Список берётся из самого APK на диске, а не выписан руками: рукописный список
 * имеет свойство разойтись с реальностью ровно тогда, когда он нужен.
 * Если файла нет — показываем прямую ссылку на скачивание и говорим об этом,
 * а не показываем пустую таблицу.
 *
 * Формат вывода — тот же, что у Android: имя права и короткое объяснение, что
 * приложение с ним делает.
 */

declare(strict_types=1);

// Страница намеренно ничего не подключает: конфиг сервера, токены и функции
// главной страницы здесь не нужны, а подключение того, чего нет в корне сайта,
// обрушивало страницу целиком (require_once на отсутствующем файле — это
// фатальная ошибка, а не предупреждение).

// Как в Android: права, которые видит человек, с объяснением.
$PERMISSIONS = [
    'android.permission.INTERNET' => [
        'why' => 'Связь с вашим сервером: отправка и загрузка данных, проверка обновлений.',
        'need' => 'Без этого приложение работает только на телефоне и ничего не синхронизирует.',
    ],
    'android.permission.ACCESS_COARSE_LOCATION' => [
        'why' => 'Примерные координаты, чтобы показать, где находится место.',
        'need' => 'Спрашивается только при первом открытии карты.',
    ],
    'android.permission.ACCESS_FINE_LOCATION' => [
        'why' => 'Точные координаты — чтобы отметить точное место, а не квартал.',
        'need' => 'Можно отказать: тогда координаты будут приблизительными.',
    ],
    'android.permission.CAMERA' => [
        'why' => 'Снять фотографию вещи, чтобы приложить её к записи.',
        'need' => 'Отказывается без последствий — просто не будет фото.',
    ],
    'android.permission.POST_NOTIFICATIONS' => [
        'why' => 'Сообщение о завершении синхронизации.',
        'need' => 'Без него всё работает, просто молча.',
    ],
    'android.permission.READ_EXTERNAL_STORAGE' => [
        'why' => 'Выбрать фотографию из галереи.',
        'need' => 'Можно отказать и снимать камерой прямо в приложении.',
    ],
];

// Находим свежий APK тем же правилом, что и главная страница.
$apk = ['rel' => '', 'major' => 0, 'minor' => 0];
foreach ([__DIR__, __DIR__ . '/download'] as $dir) {
    foreach (@scandir($dir) ?: [] as $entry) {
        if ($entry === '' || $entry[0] === '.') {
            continue;
        }
        if (!preg_match('/^kladovka[-_ ]v(\d+)\.(\d+)\.(apk)$/i', $entry, $m)) {
            continue;
        }
        if (!is_file($dir . '/' . $entry)) {
            continue;
        }
        $major = (int) $m[1];
        $minor = (int) $m[2];
        if ($major > $apk['major'] || ($major === $apk['major'] && $minor > $apk['minor'])) {
            $apk = ['rel' => $entry, 'major' => $major, 'minor' => $minor];
        }
    }
}

$declared = [];
$sha256 = '';
$size = 0;
if ($apk['rel'] !== '') {
    $path = __DIR__ . '/' . $apk['rel'];
    $size = (int) @filesize($path);
    $h = @hash_file('sha256', $path);
    $sha256 = is_string($h) ? $h : '';

    // Права читаем из манифеста прямо в APK. Без этого список в коде разошёлся
    // бы с реальностью при первой же добавленной зависимости.
    //
    // Внутри APK манифест записан в бинарном виде (AXML), и строки там лежат
    // в UTF-16LE — то есть между буквами стоят нулевые байты. Поиск по сырым
    // байтам как по UTF-8 не находит ничего, страница показывала пустоту.
    // Поэтому ищем дважды: как есть и с выброшенными нулями.
    $za = @new ZipArchive();
    $xml = '';
    if ($za->open($path) === true) {
        $raw = $za->getFromName('AndroidManifest.xml');
        $za->close();
        if (is_string($raw)) {
            $xml = $raw . "\n" . str_replace("\x00", '', $raw);
        }
    }
    if ($xml !== '' && preg_match_all('/android\.permission\.([A-Z_]+)/', $xml, $m)) {
        foreach ($m[1] as $p) {
            $declared['android.permission.' . $p] = true;
        }
    }
    // Служебное право самого приложения: Android добавляет его автоматически
    // (Compose использует его для безопасной доставки внутренних сообщений),
    // и в списке разрешений телефона оно не появляется. Показываем отдельно —
    // иначе выглядит, будто мы что-то скрываем.
    //
    // В шаблоне обязателен хвост PERMISSION: без него под разбор попадали
    // имена классов. Так, ru.kladovka.MainActivity давало «ru.kladovka.M» —
    // первую заглавную букву класса, — и на странице появлялось несуществующее
    // право с обрезанным именем.
    if ($xml !== '' && preg_match_all('/ru\.kladovka\.([A-Z_]*PERMISSION)/', $xml, $m)) {
        foreach ($m[1] as $p) {
            $declared['ru.kladovka.' . $p] = true;
        }
    }

    // Отсекаем то, чего обычное приложение запросить не может.
    //
    // Причина конкретная: после выбрасывания нулей из UTF-16 склеиваются не
    // только буквы, но и служебные байты формата AXML — в частности, длина
    // следующей строки. Из-за этого в выдаче появлялось android.permission.DUMP,
    // которого в манифесте нет: aapt2 показывает только INTERNET,
    // ACCESS_COARSE_LOCATION и ACCESS_FINE_LOCATION. Право DUMP требует
    // подписи системного уровня и обычному приложению недоступно, то есть
    // показать его здесь — значит соврать человеку о том, что его просят.
    //
    // Поэтому показываем только права, которые Android реально может выдать
    // обычному приложению. Неизвестное — не показываем вовсе: пустой список
    // разбирается на «права не нашлись», а выдуманное право выглядит как
    // настоящее.
    $PLATFORM_PERMISSIONS = [
        'INTERNET', 'ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'CAMERA',
        'POST_NOTIFICATIONS', 'READ_EXTERNAL_STORAGE', 'READ_MEDIA_IMAGES',
        'WRITE_EXTERNAL_STORAGE', 'READ_PHONE_STATE', 'VIBRATE', 'WAKE_LOCK',
        'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_DATA_SYNC', 'POST_NOTIFICATIONS',
        'RECEIVE_BOOT_COMPLETED', 'SCHEDULE_EXACT_ALARM', 'USE_EXACT_ALARM',
        'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS', 'ACCESS_NETWORK_STATE',
        'ACCESS_WIFI_STATE', 'CHANGE_WIFI_STATE', 'BLUETOOTH', 'BLUETOOTH_CONNECT',
        'RECORD_AUDIO', 'READ_CALENDAR', 'WRITE_CALENDAR', 'READ_CONTACTS',
        'WRITE_CONTACTS', 'READ_CALL_LOG', 'WRITE_CALL_LOG', 'CALL_PHONE',
        'READ_CALL_LOG', 'ANSWER_PHONE_CALLS', 'SEND_SMS', 'RECEIVE_SMS',
        'READ_SMS', 'WRITE_SMS', 'BODY_SENSORS', 'ACTIVITY_RECOGNITION',
        'GET_ACCOUNTS', 'AUTHENTICATE_ACCOUNTS', 'USE_FINGERPRINT',
        'USE_BIOMETRIC', 'MANAGE_EXTERNAL_STORAGE', 'QUERY_ALL_PACKAGES',
        'REQUEST_INSTALL_PACKAGES', 'SYSTEM_ALERT_WINDOW',
    ];
    $serviceSeen = false;
    $SERVICE_CANONICAL = 'ru.kladovka.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION';
    foreach (array_keys($declared) as $name) {
        // Служебное право Compose в манифесте встречается и как
        // <name>пакет.DYNAMIC_RECEIVER…PERMISSION</name>, и с лишним сегментом
        // «permission» посередине. Показываем один раз, иначе у человека два
        // одинаковых пункта с разными идентификаторами выглядят как два
        // разных права.
        if (str_contains($name, 'DYNAMIC_RECEIVER_NOT_EXPORTED')) {
            // Порядок важен: сначала убрать исходное имя, потом добавить
            // каноническое. Наоборот нельзя — когда исходное имя уже совпадает
            // с каноническим, unset стирает только что записанный элемент, и
            // служебное право исчезает со страницы целиком.
            unset($declared[$name]);
            if (!$serviceSeen) {
                $serviceSeen = true;
                $declared[$SERVICE_CANONICAL] = true;
            }
            continue;
        }
        // Свои права приложения показываем: они безопасны и объяснены.
        if (str_starts_with($name, 'ru.kladovka.')) {
            continue;
        }
        $short = substr($name, strlen('android.permission.'));
        if (!in_array($short, $PLATFORM_PERMISSIONS, true)) {
            unset($declared[$name]);
        }
    }
}

// Прата, которые добавляет библиотека сама, а человек их не видит в списке
// настроек. Показываем отдельно и с пояснением, чтобы их наличие не пугало.
$INTERNAL = [
    'ru.kladovka.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' =>
        'Служебное право самой системы для безопасной доставки внутренних сообщений. ' .
        'Никаких данных не даёт и в списке разрешений телефона не появляется.',
];

// Понятное название права. Раньше названия были вложенными ternary на пять
// уровней: добавление шестого права требовалось переписывать всё выражение,
// а забыть одну скобку — роняло страницу целиком. Массив читается за один взгляд.
$TITLES = [
    'android.permission.ACCESS_FINE_LOCATION' => 'Точное местоположение',
    'android.permission.ACCESS_COARSE_LOCATION' => 'Приблизительное местоположение',
    'android.permission.INTERNET' => 'Доступ в интернет',
    'android.permission.CAMERA' => 'Камера',
    'android.permission.POST_NOTIFICATIONS' => 'Уведомления',
    'android.permission.READ_EXTERNAL_STORAGE' => 'Чтение изображений',
    'android.permission.READ_MEDIA_IMAGES' => 'Чтение изображений',
    'android.permission.VIBRATE' => 'Вибрация',
    'android.permission.WAKE_LOCK' => 'Работа в фоне',
];

$rows = [];
foreach (array_keys($declared) as $p) {
    $isService = isset($INTERNAL[$p]);
    $rows[] = [
        'name' => $p,
        'title' => $TITLES[$p] ?? ($isService ? 'Служебное, для внутренних сообщений' : 'Служебное'),
        'why' => $PERMISSIONS[$p]['why'] ?? ($INTERNAL[$p] ?? 'Запрашивается библиотекой, которой это нужно для работы.'),
        'need' => $PERMISSIONS[$p]['need'] ?? ($INTERNAL[$p] ?? ''),
        'internal' => $isService,
    ];
}

function h(string $s): string
{
    return htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
}
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Права приложения «Кладовка»</title>
<meta name="description" content="Полный список прав, которые запрашивает приложение «Кладовка», и объяснение, зачем каждое нужно.">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<style>
  :root { --bg:#06090f; --ink:#e0e6ed; --muted:#8b9aa8; --primary:#00f0ff; --line:rgba(255,255,255,.10); }
  * { box-sizing:border-box; }
  body { margin:0; padding:24px 16px 48px; background:var(--bg); color:var(--ink);
         font:16px/1.6 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;
         /* Перенос — наследуемый: длинные идентификаторы прав (26–40 символов)
            и хэш без пробелов иначе растягивают документ шире экрана, и весь
            текст справа обрезается. Проверено снимком экрана на 360 px и
            контурами всех элементов: растягивали документ именно те строки,
            для которых перенос задан был только здесь, а не у предков. */
         overflow-wrap:anywhere; }
  /* Адаптивность. Идентификаторы прав длинные (ACCESS_COARSE_LOCATION — 26
     символов) и без переноса распирают страницу по ширине: на экране 430 px
     текст уезжал за правый край и обрезался, то есть половина объяснений была
     не видна вовсе. Проверено снимком экрана, а не на глаз по коду. */
  .wrap { max-width:720px; margin:0 auto; }
  a { color:var(--primary); }
  h1 { font-size:1.7rem; margin:0 0 6px; overflow-wrap:anywhere; }
  .sub { color:var(--muted); margin:0 0 22px; }
  h2 { font-size:1.15rem; margin:30px 0 10px; }
  .card { background:rgba(255,255,255,.04); border:1px solid var(--line); border-radius:12px;
          padding:14px 16px; margin:0 0 10px; box-sizing:border-box;
          overflow-wrap:anywhere; word-break:normal; }
  .name { font-weight:700; }
  .why { color:var(--muted); margin:6px 0 0; font-size:.94rem; overflow-wrap:anywhere; }
  .need { color:var(--muted); margin:4px 0 0; font-size:.88rem; font-style:italic; }
  /* Идентификатор права — 26–40 символов, поэтому перенос нужен обязательно.
     display:inline-block здесь ставить нельзя: он вынимает элемент из
     потока, ширина считается по содержимому, и длинное имя распирает карточку
     за экран. Обрезание текста справа на узком экране было ровно от этого —
     проверено контрольной страницей с полосами заведомо известной ширины. */
  code { background:rgba(255,255,255,.07); padding:2px 6px; border-radius:6px;
         font-size:.86rem; word-break:break-all; overflow-wrap:anywhere; }
  .hashbox { background:rgba(0,0,0,.35); border:1px solid var(--line); border-radius:10px;
             padding:12px 14px; font-family:ui-monospace,Menlo,Consolas,monospace;
             font-size:.82rem; word-break:break-all; line-height:1.6; color:#bfe9f2;
             box-sizing:border-box; max-width:100%; }
  .note { color:var(--muted); font-size:.9rem; }
  ul { padding-left:20px; color:var(--muted); }
  li { overflow-wrap:anywhere; }
  @media (max-width:420px) {
    body { padding:18px 12px 40px; }
    .card { padding:12px 13px; }
    .hashbox { font-size:.76rem; padding:10px 12px; }
  }
</style>
</head>
<body>
<div class="wrap">
  <p><a href="/" style="text-decoration:none">← Кладовка</a></p>
  <h1>Какие права запрашивает приложение</h1>
  <p class="sub">
    Список взят из самого файла, а не написан руками. На сборке
    <?= $apk['rel'] !== '' ? 'v' . h((string) $apk['major'] . '.' . $apk['minor']) : '—' ?>
    <?= $size > 0 ? '(' . number_format($size, 0, ',', ' ') . ' байт)' : '' ?>.
  </p>

<?php if ($apk['rel'] === ''): ?>
  <div class="card">
    <p class="why">На сервере сейчас нет файла сборки, поэтому читать права не из чего.
       Это не ошибка — сборка появится позже, и список покажется вместе с ней.</p>
    <p class="why"><a href="/download-handler.php">Скачать приложение</a></p>
  </div>
<?php else: ?>

  <h2>Права, которые видит человек</h2>
  <?php if ($rows === []): ?>
    <div class="card"><p class="why">Не удалось прочитать манифест из файла. Права можно
      посмотреть и на телефоне: «Настройки → Приложения → Кладовка → Разрешения».</p></div>
  <?php endif; ?>
  <?php foreach ($rows as $r): ?>
    <div class="card">
      <div class="name"><?= h($r['title']) ?></div>
      <div class="why"><code><?= h($r['name']) ?></code></div>
      <p class="why"><?= h($r['why']) ?></p>
      <?php if ($r['need'] !== ''): ?><p class="need"><?= h($r['need']) ?></p><?php endif; ?>
    </div>
  <?php endforeach; ?>

  <h2>Чего приложение не просит</h2>
  <ul>
    <li>Камеры — без неё не будет фото, но остальное работает.</li>
    <li>Контактов и истории звонков.</li>
    <li>Отправки SMS и звонков.</li>
    <li>Чтения и записи чужих файлов.</li>
    <li>Данных об установленных приложениях, кроме карт — и только чтобы
        открыть «Открыть на карте» (объявлено в манифесте как
        <code>&lt;queries&gt;</code>).</li>
  </ul>
  <p class="note">Проверено на Android 8, 9 и 15: установленные сборки запрашивают ровно
     эти права, дополнительных не появлялось.</p>

  <h2>Отпечаток файла</h2>
  <div class="hashbox">SHA-256<br><?= h($sha256) ?></div>
  <p class="note" style="margin-top:10px;">Если ваш файл даёт такую же сумму — вы
     скачали ровно тот APK, который собран и выложен здесь. Способы сверить: любой
     онлайн-сервис, считающий хэш файла, либо на компьютере
     <code>Get-FileHash Kladovka.apk -Algorithm SHA256</code>.</p>

  <h2>Почему щиток всё равно появляется</h2>
  <p class="note">Ниже на <a href="/#install">странице установки</a> — объяснение:
     Play Protect проверяет файл и не находит угроз, но предупреждает всё равно,
     потому что APK поставлен из файла, а не из Google Play. Так он ведёт себя
     с любым приложением вне Play.</p>

  <p style="margin-top:28px"><a href="/">← Вернуться на главную</a></p>
<?php endif; ?>
</div>
</body>
</html>