<?php
// Кладовка — единая точка API.
// PHP 8.3 + SQLite (PDO). Схема как в Android-приложении.
//
// Действия (action):
//   login                 POST {password}              -> {token}
//   login                 POST {username,password}     -> {token}
//   register              POST {username,password}     -> {ok}  (rate-limited)
//   export                GET  (auth)                  -> весь бэкап JSON
//   import                POST (auth) {json}           -> полная замена данных
//   list                  GET  (auth)                  -> {places,shelves,polki,containers,items}
//   save_place|shelf|polka|container|item POST (auth) {...}
//   delete_place|shelf|polka|container|item POST (auth) {id}  (связи обнуляются, не удаляются)
//   upload_photo          POST (auth) multipart {photo} -> {url}
//   logout                GET/POST (auth)              -> {ok} (токены статистичны)
//
// Аутентификация: заголовок Authorization: Bearer <token> (или X-Api-Key для
//   ключа из конфига). Приём токена из query-строки убран — см. $token ниже.
//   Админ-токен = HMAC(session_secret); API-ключ из конфига = тоже админ.
//   Пользовательский токен = HMAC(user_id + secret).
// Защита от перебора: 6 неудачных за 10 мин -> 429. Регистрация: 3 с одного IP за сутки.

declare(strict_types=1);

$CFG = require __DIR__ . '/../server-config.php';

function issueToken(array $cfg): string {
    return hash_hmac('sha256', 'kladovka-session', $cfg['session_secret']);
}
function issueUserToken(array $cfg, int $userId): string {
    return hash_hmac('sha256', 'kladovka-user-' . $userId, $cfg['session_secret']);
}
function checkAuth(array $cfg, ?string $token): bool {
    return userIdForToken($cfg, $token) !== null;
}
// Возвращает id пользователя по его токену (или null для админ-токена/неверного)
function userIdForToken(array $cfg, ?string $token): ?int {
    if ($token === null || $token === '') return null;
    // API-ключ из конфига (заголовок X-Api-Key или Bearer) — права администратора
    if (hash_equals((string)($cfg['api_key'] ?? ''), $token)) return 0;
    if (hash_equals(issueToken($cfg), $token)) return 0; // 0 = админ
    // Отозванные после выхода из аккаунта: токен статистичный, поэтому иначе он
    // продолжал бы работать вечно. Выход обязан быть настоящим, иначе «вышел»
    // означает «сессия в браузере стёрта, а доступ всё ещё выдан».
    $revoked = revoked_user_ids($cfg);
    $pdo = db($cfg);
    $stmt = $pdo->prepare('SELECT id FROM users LIMIT 1000');
    $stmt->execute();
    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
        $id = (int)$row['id'];
        if (isset($revoked[$id])) continue;
        if (hash_equals(issueUserToken($cfg, $id), $token)) return $id;
    }
    return null;
}

/** id пользователей, чей вход отозван. Админ (0) здесь не бывает и не отзывается. */
function revoked_user_ids(array $cfg): array {
    try {
        $rows = db($cfg)->query('SELECT user_id FROM revoked_users')->fetchAll(PDO::FETCH_COLUMN);
    } catch (Throwable) {
        return []; // таблицы ещё нет — запрос до первой миграции
    }
    $out = [];
    foreach ($rows as $r) $out[(int)$r] = true;
    return $out;
}

/** Отозвать вход пользователя (выход из аккаунта). */
function revoke_user(PDO $pdo, int $userId): void {
    if ($userId <= 0) return; // админа отозвать нельзя
    $stmt = $pdo->prepare('INSERT OR REPLACE INTO revoked_users (user_id, at) VALUES (:id, :at)');
    $stmt->execute([':id' => $userId, ':at' => time()]);
}

/** Снять отзыв: новый вход снова выдаёт рабочий токен. */
function unrevoke_user(PDO $pdo, int $userId): void {
    if ($userId <= 0) return;
    $stmt = $pdo->prepare('DELETE FROM revoked_users WHERE user_id = :id');
    $stmt->execute([':id' => $userId]);
}

// ---------- Отправка письма с подтверждением почты ----------
function sendConfirmMail(array $cfg, string $email, string $username, string $token): bool {
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'kladovka.dr6ter.ru';
    $link = $scheme . '://' . $host . '/api.php?action=confirm&u=' . rawurlencode($username) . '&t=' . rawurlencode($token);
    // Всё тело кодируем в base64, чтобы MIME оставался чистым ASCII (без этого
    // локальная доставка dr6ter.ru требует SMTPUTF8 и письмо улетает в bounce).
    $body = "Здравствуйте, {$username}!\n\n"
        . "Для подтверждения почты на сайте Кладовка перейдите по ссылке:\n"
        . $link . "\n\n"
        . "Ссылка действительна 24 часа. Если вы не регистрировались — просто проигнорируйте письмо.\n";
    $subject = '=?UTF-8?B?' . base64_encode('Кладовка: подтвердите почту') . '?=';
    // Имя отправителя тоже RFC-2047-кодируем: всё письмо остаётся чистым ASCII
    // (иначе локальный relay требует SMTPUTF8 и письмо улетает в bounce)
    $fromName = '=?UTF-8?B?' . base64_encode('Кладовка') . '?=';
    // Отправляем с noreply@dr6ter.ru — для этого домена настроены SPF + DKIM (rspamd),
    // письма не попадут в спам. Изменение From для postfix: MAIL FROM тоже берём этот адрес.
    $headers = "From: {$fromName} <noreply@dr6ter.ru>\r\n"
        . "MIME-Version: 1.0\r\n"
        . "Content-Type: text/plain; charset=utf-8\r\n"
        . "Content-Transfer-Encoding: base64\r\n";
    return @mail($email, $subject, base64_encode($body), $headers, '-fnoreply@dr6ter.ru');
}

function db(array $cfg): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dir = dirname($cfg['db']);
        if (!is_dir($dir)) mkdir($dir, 0755, true);
        $pdo = new PDO('sqlite:' . $cfg['db']);
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->exec('PRAGMA journal_mode=WAL');
        $pdo->exec('PRAGMA foreign_keys=ON');
        // Не падать с "database is locked" при параллельных записях (телефон + кабинет)
        $pdo->exec('PRAGMA busy_timeout=10000');
        schema($pdo);
    }
    return $pdo;
}
function schema(PDO $pdo): void {
    $pdo->exec("CREATE TABLE IF NOT EXISTS places (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        latitude REAL,
        longitude REAL,
        notes TEXT NOT NULL DEFAULT '',
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0
    )");
    $pdo->exec("CREATE TABLE IF NOT EXISTS shelves (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        notes TEXT NOT NULL DEFAULT '',
        placeId INTEGER,
        location TEXT NOT NULL DEFAULT '',
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0
    )");
    $pdo->exec("CREATE TABLE IF NOT EXISTS containers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        shelfId INTEGER,
        placeId INTEGER,
        location TEXT NOT NULL DEFAULT '',
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0
    )");
    $pdo->exec("CREATE TABLE IF NOT EXISTS items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        quantity INTEGER NOT NULL DEFAULT 1,
        unit TEXT NOT NULL DEFAULT '',
        category TEXT NOT NULL DEFAULT '',
        notes TEXT NOT NULL DEFAULT '',
        containerId INTEGER,
        shelfId INTEGER,
        placeId INTEGER,
        photoPath TEXT,
        pinned INTEGER NOT NULL DEFAULT 0,
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0
    )");
    // Полки (уровень иерархии между стеллажами и контейнерами; есть в Android-приложении)
    $pdo->exec("CREATE TABLE IF NOT EXISTS polki (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        notes TEXT NOT NULL DEFAULT '',
        shelfId INTEGER,
        placeId INTEGER,
        createdAt INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL DEFAULT 0,
        ownerId INTEGER NOT NULL DEFAULT 0
    )");
    // Таблица пользователей (регистрация через веб)
    $pdo->exec("CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        email TEXT NOT NULL DEFAULT '',
        password_hash TEXT NOT NULL,
        email_verified INTEGER NOT NULL DEFAULT 0,
        confirm_token TEXT NOT NULL DEFAULT '',
        confirm_expires INTEGER NOT NULL DEFAULT 0,
        confirm_sent_at INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL DEFAULT 0,
        ip TEXT NOT NULL DEFAULT ''
    )");
    // Миграция: колонки подтверждения почты (для баз, созданных до этой фичи)
    $uCols = array_column($pdo->query("PRAGMA table_info(users)")->fetchAll(PDO::FETCH_ASSOC), 'name');
    $uMigrations = [
        'email' => "ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''",
        'email_verified' => 'ALTER TABLE users ADD COLUMN email_verified INTEGER NOT NULL DEFAULT 0',
        'confirm_token' => "ALTER TABLE users ADD COLUMN confirm_token TEXT NOT NULL DEFAULT ''",
        'confirm_expires' => 'ALTER TABLE users ADD COLUMN confirm_expires INTEGER NOT NULL DEFAULT 0',
        'confirm_sent_at' => 'ALTER TABLE users ADD COLUMN confirm_sent_at INTEGER NOT NULL DEFAULT 0',
    ];
    foreach ($uMigrations as $col => $sql) {
        if (!in_array($col, $uCols, true)) $pdo->exec($sql);
    }
    // Миграция уже существующих баз: добавляем метки времени и владельца записи
    foreach (['places', 'shelves', 'polki', 'containers', 'items'] as $t) {
        $cols = array_column($pdo->query("PRAGMA table_info($t)")->fetchAll(PDO::FETCH_ASSOC), 'name');
        if (!in_array('createdAt', $cols, true)) {
            $pdo->exec("ALTER TABLE $t ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
        }
        if (!in_array('updatedAt', $cols, true)) {
            $pdo->exec("ALTER TABLE $t ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
        }
        // Совместный учёт: владелец записи (0 = админ, >0 = id пользователя)
        if (!in_array('ownerId', $cols, true)) {
            $pdo->exec("ALTER TABLE $t ADD COLUMN ownerId INTEGER NOT NULL DEFAULT 0");
        }
        // Закреплённые вещи (⭐ pinned)
        if ($t === 'items' && !in_array('pinned', $cols, true)) {
            $pdo->exec('ALTER TABLE items ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0');
        }
    }
    // Совместный учёт: кому пользователь открыл доступ к своим записям
    $pdo->exec("CREATE TABLE IF NOT EXISTS shares (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        owner_user_id INTEGER NOT NULL,
        shared_with_user_id INTEGER NOT NULL,
        UNIQUE(owner_user_id, shared_with_user_id)
    )");
    // Совместный учёт: соответствие локальных id телефона и id на сервере
    // (чтобы два пользователя не «затирали» записи друг друга одинаковыми id)
    $pdo->exec("CREATE TABLE IF NOT EXISTS id_map (
        owner_id INTEGER NOT NULL,
        type TEXT NOT NULL,
        local_id INTEGER NOT NULL,
        server_id INTEGER NOT NULL,
        PRIMARY KEY (owner_id, type, local_id)
    )");
    // Отозванные входы: пользователь вышел из аккаунта, и его токен больше не
    // должен работать. Токен статистичный, поэтому отзываем вход целиком, а при
    // новом входе запись снимается.
    $pdo->exec("CREATE TABLE IF NOT EXISTS revoked_users (
        user_id INTEGER PRIMARY KEY,
        at INTEGER NOT NULL DEFAULT 0
    )");
    // Счётчики частоты тяжёлых запросов. В SQLite, а не в json-файле как у
    // ограничителя входа: несколько php-fpm воркеров должны видеть общий счёт,
    // иначе лимит обходится параллельными запросами.
    $pdo->exec("CREATE TABLE IF NOT EXISTS rate_hits (
        k TEXT NOT NULL,
        ts INTEGER NOT NULL
    )");
    $pdo->exec("CREATE INDEX IF NOT EXISTS idx_rate_hits ON rate_hits(k, ts)");
}

function respond(int $code, $payload): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    if ($code === 429) header('Retry-After: 600');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    exit;
}
function respondError(int $code, string $msg): void { respond($code, ['error' => $msg]); }
function requireAuth(array $cfg, string $token): void {
    $uid = userIdForToken($cfg, $token);
    if ($uid === null) respondError(401, 'Не авторизовано');
    if ($uid > 0) {
        // Пользователь с неподтверждённой почтой не видит данные
        $pdo = db($cfg);
        $stmt = $pdo->prepare('SELECT email_verified FROM users WHERE id = :id');
        $stmt->execute([':id' => $uid]);
        if ((int)$stmt->fetchColumn() !== 1) respondError(403, 'Подтвердите почту, чтобы пользоваться сайтом');
    }
}
function readInput(): array {
    $raw = file_get_contents('php://input');
    if ($raw === '' || $raw === false) return [];
    $j = json_decode($raw, true);
    return is_array($j) ? $j : [];
}

// ---------- Совместный учёт ----------
/** id пользователей, чьи записи видны пользователю $uid (0 = админ: видит всё). */
function visibleOwnerIds(PDO $pdo, int $uid): array {
    if ($uid === 0) return []; // пусто = без фильтра (видно всё)
    $ids = [$uid];
    $st = $pdo->prepare('SELECT owner_user_id FROM shares WHERE shared_with_user_id = :me');
    $st->execute([':me' => $uid]);
    foreach ($st->fetchAll(PDO::FETCH_COLUMN) as $o) $ids[] = (int)$o;
    return array_values(array_unique($ids));
}

/** Может ли пользователь (или админ) менять/удалять запись из таблицы. */
function canEditRecord(PDO $pdo, int $uid, string $table, int $id): bool {
    if ($uid === 0) return true;
    $st = $pdo->prepare("SELECT ownerId FROM $table WHERE id = :id");
    $st->execute([':id' => $id]);
    $row = $st->fetch(PDO::FETCH_ASSOC);
    if ($row === false) return false;
    $owner = (int)$row['ownerId'];
    if ($owner === $uid) return true;
    // Расшаренные записи можно редактировать (совместные)
    $sh = $pdo->prepare('SELECT 1 FROM shares WHERE owner_user_id = :o AND shared_with_user_id = :me');
    $sh->execute([':o' => $owner, ':me' => $uid]);
    return $sh->fetchColumn() !== false;
}

/**
 * Импорт записи пользователя с сохранением его владельца и без затирания чужих.
 * localId<=0: вставка с авто-id. Иначе: через id_map/поиск по id — либо обновляем
 * существующую запись пользователя, либо создаём с новым серверным id (если id занят чужим).
 */
function upsertOwned(PDO $pdo, string $table, array $rec, array $fields, int $uid, int $localId): int {
    $params = normalize($table, $rec, $fields);
    $now = (int)(microtime(true) * 1000);
    if ($table !== 'items') {
        // Места/стеллажи/полки/контейнеры: метки ведёт сервер.
        // ВАЖНО: createdAt/updatedAt должны попасть и в список колонок INSERT,
        // иначе PDO SQLite ругается "column index out of range" (лишние параметры).
        $fields = array_merge($fields, ['createdAt', 'updatedAt']);
        $params['createdAt'] = $now;
        $params['updatedAt'] = $now;
    } else {
        // Вещи: метки приходят с устройства, недостающие проставляем сами.
        if (empty($params['createdAt']) || $params['createdAt'] === 0) $params['createdAt'] = $now;
        $params['updatedAt'] = $now;
    }

    if ($localId <= 0) {
        // Новая запись — сервер сам выдаст id
        $allFields = array_merge($fields, ['ownerId']);
        $params['ownerId'] = $uid;
        $stmt = $pdo->prepare(
            "INSERT INTO $table (" . implode(',', $allFields) . ")
             VALUES (:" . implode(', :', $allFields) . ")"
        );
        $stmt->execute($params);
        return (int)$pdo->lastInsertId();
    }

    // 1. Ищем в id_map
    $m = $pdo->prepare('SELECT server_id FROM id_map WHERE owner_id=:o AND type=:t AND local_id=:l');
    $m->execute([':o' => $uid, ':t' => $table, ':l' => $localId]);
    $mapped = $m->fetchColumn();

    $id = 0;
    if ($mapped !== false && (int)$mapped > 0) {
        $id = (int)$mapped;
    } else {
        // 2. Проверяем, кому принадлежит id на сервере
        $chk = $pdo->prepare("SELECT ownerId FROM $table WHERE id = :id");
        $chk->execute([':id' => $localId]);
        $row = $chk->fetch(PDO::FETCH_ASSOC);
        if ($row === false || (int)$row['ownerId'] === $uid) {
            $id = $localId; // свободен или уже наш
        } else {
            // Коллизия с чужими данными — создаём с новым id
            $id = (int)$pdo->query("SELECT COALESCE(MAX(id),0)+1 FROM $table")->fetchColumn();
        }
        $m2 = $pdo->prepare('INSERT INTO id_map (owner_id, type, local_id, server_id) VALUES (?,?,?,?)');
        $m2->execute([$uid, $table, $localId, $id]);
    }

    $allFields = array_merge($fields, ['ownerId']);
    $params['id'] = $id;
    $params['ownerId'] = $uid;
    $upd = array_filter($allFields, fn($f) => $f !== 'createdAt');
    $updates = implode(', ', array_map(fn($f) => "$f = :$f", $upd));
    $stmt = $pdo->prepare(
        "INSERT INTO $table (id, " . implode(',', $allFields) . ")
         VALUES (:id, :" . implode(', :', $allFields) . ")
         ON CONFLICT(id) DO UPDATE SET $updates"
    );
    $stmt->execute($params);
    return $id;
}

// ---------- Защита от перебора пароля ----------
function failStore(array $cfg): string { return dirname($cfg['db']) . '/login-attempts.json'; }
function loadFails(array $cfg): array {
    $f = failStore($cfg);
    if (!is_file($f)) return [];
    $j = json_decode((string)file_get_contents($f), true);
    return is_array($j) ? $j : [];
}
function saveFails(array $cfg, array $data): void {
    file_put_contents(failStore($cfg), json_encode($data), LOCK_EX);
}
function blockedSeconds(array $cfg, string $ip): int {
    $now = time();
    $list = array_values(array_filter(loadFails($cfg)[$ip] ?? [], fn($t) => $now - (int)$t < 600));
    if (count($list) < 6) return 0;
    return max(1, 600 - ($now - (int)min($list)));
}
function recordFail(array $cfg, string $ip): void {
    $data = loadFails($cfg);
    $now = time();
    $data[$ip][] = $now;
    $data[$ip] = array_values(array_filter($data[$ip], fn($t) => $now - (int)$t < 600));
    saveFails($cfg, $data);
}
function clearFails(array $cfg, string $ip): void {
    $data = loadFails($cfg);
    if (isset($data[$ip])) { unset($data[$ip]); saveFails($cfg, $data); }
}

// ---------- Защита от спама регистрацией ----------
// Больше 3 аккаунтов с одного IP за сутки -> 429.
function regBlockedSeconds(array $cfg, string $ip): int {
    $f = dirname($cfg['db']) . '/reg-attempts.json';
    $j = is_file($f) ? json_decode((string)file_get_contents($f), true) : null;
    $list = is_array($j) && isset($j[$ip]) && is_array($j[$ip]) ? $j[$ip] : [];
    $now = time();
    $recent = array_values(array_filter($list, fn($t) => $now - (int)$t < 86400));
    if (count($recent) < 3) return 0;
    return max(1, 86400 - ($now - (int)min($recent)));
}
function recordRegistration(array $cfg, string $ip): void {
    $f = dirname($cfg['db']) . '/reg-attempts.json';
    $j = is_file($f) ? json_decode((string)file_get_contents($f), true) : [];
    if (!is_array($j)) $j = [];
    $now = time();
    $j[$ip][] = $now;
    $j[$ip] = array_values(array_filter($j[$ip] ?? [], fn($t) => $now - (int)$t < 86400));
    file_put_contents($f, json_encode($j), LOCK_EX);
}

// ---------- Ограничение частоты тяжёлых запросов ----------
// Считаем по id пользователя, а не по IP. По IP телефон и кабинет одного
// человека наказывали бы друг друга, а один и тот же пользователь, сменив сеть,
// лимит бы обошёл. Счётчики общие для всех воркеров (см. rate_hits в схеме).

/** Сколько запросов ещё можно: >0 — можно, <=0 — пора отвечать 429. */
function rateLeft(PDO $pdo, string $key, int $max, int $window): int {
    $since = time() - $window;
    // Подрезаем протухшее, иначе таблица растёт без ограничений
    $pdo->prepare('DELETE FROM rate_hits WHERE ts < :since')->execute([':since' => $since]);
    $stmt = $pdo->prepare('SELECT COUNT(*) FROM rate_hits WHERE k = :k AND ts >= :since');
    $stmt->execute([':k' => $key, ':since' => $since]);
    return $max - (int)$stmt->fetchColumn();
}

/**
 * Проверяет лимит и сразу записывает попытку, если он не исчерпан.
 * Проверка и запись идут вместе: иначе N параллельных запросов успевают
 * прочитать «есть запас» и все пройти.
 */
function rateGuard(PDO $pdo, string $key, int $max, int $window, string $what): void {
    if (rateLeft($pdo, $key, $max, $window) <= 0) {
        respondError(429, 'Слишком часто: ' . $what . '. Повторите через минуту.');
    }
    $pdo->prepare('INSERT INTO rate_hits (k, ts) VALUES (:k, :ts)')->execute([':k' => $key, ':ts' => time()]);
}

/** Лимит из конфига, с запасным значением — чтобы сервер можно было настроить без правки кода. */
function rateLimit(array $cfg, string $name, int $default): int {
    $v = (int)($cfg[$name] ?? $default);
    return $v > 0 ? $v : $default;
}

// ---------- Нормализация и upsert ----------
function normalize(string $table, array $data, array $fields): array {
    $out = [];
    foreach ($fields as $f) {
        $v = $data[$f] ?? null;
        if ($v === null || $v === '') {
            if (in_array($f, ['name','notes','location','unit','category'])) $v = '';
            elseif ($f === 'quantity') $v = 1;
            elseif ($f === 'pinned') $v = 0;
            elseif (in_array($f, ['createdAt','updatedAt'])) $v = 0;
            else $v = null;
        }
        if (in_array($f, ['quantity','pinned','createdAt','updatedAt','placeId','shelfId','containerId','ownerId'])) {
            $v = ($v === null || $v === '') ? null : (int)$v;
        }
        if (in_array($f, ['latitude','longitude'])) {
            $v = ($v === null || $v === '') ? null : (float)$v;
        }
        // photoPath — только безопасные схемы/относительные пути (блокируем javascript: и т.п.)
        if ($f === 'photoPath' && $v !== null) {
            $v = trim((string)$v);
            if ($v === '') $v = null;
            elseif (preg_match('/^\s*(javascript|vbscript|data|file):/i', $v) || strpbrk($v, "<>\x00\x0d\x0a") !== false) $v = null;
        }
        $out[$f] = $v;
    }
    return $out;
}

function upsert(PDO $pdo, string $table, array $data, array $fields): void {
    $id = (int)($data['id'] ?? 0);
    $params = normalize($table, $data, $fields);
    $now = (int)(microtime(true) * 1000);

    if ($table !== 'items') {
        // Места/стеллажи/контейнеры: метки ведёт сервер. createdAt выставляется
        // только при создании записи; повторная отправка (например, с телефона)
        // дату создания не затирает.
        $fields = array_merge($fields, ['createdAt', 'updatedAt']);
        $params['createdAt'] = $now;
        $params['updatedAt'] = $now;
    } else {
        // Вещи: метки приходят с устройства, недостающие проставляем сами.
        if ($id <= 0 && (empty($params['createdAt']) || $params['createdAt'] === 0)) $params['createdAt'] = $now;
        $params['updatedAt'] = $now;
    }

    if ($id <= 0) {
        $stmt = $pdo->prepare(
            "INSERT INTO $table (" . implode(',', $fields) . ")
             VALUES (:" . implode(', :', $fields) . ")"
        );
        $stmt->execute($params);
    } else {
        $upd = array_filter($fields, fn($f) => $f !== 'createdAt');
        $updates = implode(', ', array_map(fn($f) => "$f = :$f", $upd));
        $all = ['id' => $id] + $params;
        $stmt = $pdo->prepare(
            "INSERT INTO $table (id, " . implode(',', $fields) . ")
             VALUES (:id, :" . implode(', :', $fields) . ")
             ON CONFLICT(id) DO UPDATE SET $updates"
        );
        $stmt->execute($all);
    }
}

$TABLE_MAP = [
    'save_place' => 'places', 'save_shelf' => 'shelves',
    'save_polka' => 'polki', 'save_container' => 'containers', 'save_item' => 'items',
    'delete_place' => 'places', 'delete_shelf' => 'shelves',
    'delete_polka' => 'polki', 'delete_container' => 'containers', 'delete_item' => 'items',
];
$FIELDS = [
    'places' => ['name','latitude','longitude','notes'],
    'shelves' => ['name','notes','placeId','location'],
    'polki' => ['name','notes','shelfId','placeId'],
    'containers' => ['name','shelfId','placeId','location'],
    'items' => ['name','quantity','unit','category','notes','containerId','shelfId','placeId','photoPath','pinned','createdAt','updatedAt'],
];

$action = $_GET['action'] ?? '';
// Токен принимается только заголовком — Authorization: Bearer, либо X-Api-Key
// для ключа из конфига. Приём из query-строки (?token=, ?api_key=) и из тела POST
// убран: токен в URL попадает в логи веб-сервера, в Referer при загрузке
// ресурсов и в историю браузера, то есть утекает дальше, чем нужно.
// Все клиенты — телефон, десктоп, кабинет и скрипты sync-test — уже шлют Bearer.
$token = (function () {
    $h = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (str_starts_with($h, 'Bearer ')) return substr($h, 7);
    return $_SERVER['HTTP_X_API_KEY'] ?? '';
})();

$pdo = db($CFG);

// latestApk — ПУБЛИЧНАЯ проба ДО логина (без токена). Обрабатывается ДО switch:
if ($action === 'latestApk') latestApk($CFG);

switch ($action) {
    case 'login': {
        $ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
        $wait = blockedSeconds($CFG, $ip);
        if ($wait > 0) respondError(429, 'Слишком много попыток входа. Попробуйте через ' . ceil($wait / 60) . ' мин.');
        $in = readInput();
        $pw = (string)($in['password'] ?? '');
        $username = trim((string)($in['username'] ?? ''));

        // Админ: вход по паролю (приложение и старые клиенты шлют только password)
        if ($username === '' && password_verify($pw, $CFG['password_hash'])) {
            clearFails($CFG, $ip);
            respond(200, ['token' => issueToken($CFG), 'role' => 'admin']);
        }

        // Пользователь: вход по username + password
        if ($username !== '') {
            $stmt = $pdo->prepare('SELECT id, password_hash, email, email_verified FROM users WHERE username = :u');
            $stmt->execute([':u' => $username]);
            $user = $stmt->fetch(PDO::FETCH_ASSOC);
            if ($user !== false && password_verify($pw, $user['password_hash'])) {
                clearFails($CFG, $ip);
                // Новый вход снимает отзыв: иначе после выхода вернуться
                // было бы невозможно.
                unrevoke_user(db($CFG), (int)$user['id']);
                respond(200, [
                    'token' => issueUserToken($CFG, (int)$user['id']),
                    'role' => 'user',
                    'username' => $username,
                    'email' => (string)$user['email'],
                    'email_verified' => (int)$user['email_verified'] === 1,
                ]);
            }
        }

        recordFail($CFG, $ip);
        respondError(401, 'Неверный логин или пароль');
    }

    case 'register': {
        $ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
        $wait = regBlockedSeconds($CFG, $ip);
        if ($wait > 0) respondError(429, 'Слишком много регистраций с этого адреса. Попробуйте через ' . ceil($wait / 3600) . ' ч.');
        $in = readInput();
        $username = trim((string)($in['username'] ?? ''));
        $password = (string)($in['password'] ?? '');
        $email = strtolower(trim((string)($in['email'] ?? '')));

        // Валидация имени
        if (strlen($username) < 3 || strlen($username) > 30) respondError(400, 'Имя: от 3 до 30 символов');
        if (!preg_match('/^[a-zA-Z0-9_]+$/', $username)) respondError(400, 'Имя: только латиница, цифры и _');
        if (strtolower($username) === 'admin') respondError(400, 'Это имя зарезервировано');
        // Валидация пароля
        if (strlen($password) < 6) respondError(400, 'Пароль: минимум 6 символов');
        if (strlen($password) > 128) respondError(400, 'Пароль: слишком длинный');
        // Валидация почты
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) respondError(400, 'Некорректный email');
        if (strlen($email) > 190) respondError(400, 'Email: слишком длинный');
        $stmt = $pdo->prepare('SELECT id FROM users WHERE email = :e AND email <> ""');
        $stmt->execute([':e' => $email]);
        if ($stmt->fetch()) respondError(409, 'Этот email уже зарегистрирован');

        $confirmToken = bin2hex(random_bytes(32));
        try {
            $stmt = $pdo->prepare(
                'INSERT INTO users (username, email, password_hash, email_verified, confirm_token, confirm_expires, confirm_sent_at, created_at, ip)
                 VALUES (:u, :e, :h, 0, :ct, :ce, :cs, :t, :ip)'
            );
            $now = time();
            $stmt->execute([
                ':u' => $username,
                ':e' => $email,
                ':h' => password_hash($password, PASSWORD_DEFAULT),
                ':ct' => $confirmToken,
                ':ce' => $now + 86400,      // ссылка живёт 24 часа
                ':cs' => $now,
                ':t' => (int)(microtime(true) * 1000),
                ':ip' => $ip,
            ]);
            recordRegistration($CFG, $ip);
            sendConfirmMail($CFG, $email, $username, $confirmToken);
            respond(200, ['ok' => true, 'role' => 'user', 'username' => $username, 'email_sent' => true]);
        } catch (Throwable $e) {
            respondError(409, 'Это имя уже занято');
        }
    }

    case 'verify_email': {
        // Для уже существующих аккаунтов: задать почту (или прислать ссылку заново)
        $uid = userIdForToken($CFG, $token);
        if ($uid === null) respondError(401, 'Не авторизовано');
        if ($uid === 0) respondError(400, 'Для админа подтверждение не требуется');
        $in = readInput();
        $email = strtolower(trim((string)($in['email'] ?? '')));
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) respondError(400, 'Некорректный email');
        $stmt = $pdo->prepare('SELECT id FROM users WHERE email = :e AND id <> :id AND email <> ""');
        $stmt->execute([':e' => $email, ':id' => $uid]);
        if ($stmt->fetch()) respondError(409, 'Этот email уже используется другим аккаунтом');

        // Повторная отправка не чаще раза в минуту
        $stmt = $pdo->prepare('SELECT confirm_sent_at FROM users WHERE id = :id');
        $stmt->execute([':id' => $uid]);
        $sentAt = (int)$stmt->fetchColumn();
        if (time() - $sentAt < 60) respondError(429, 'Ссылка уже отправлена. Подождите минуту.');

        $confirmToken = bin2hex(random_bytes(32));
        $stmt = $pdo->prepare('UPDATE users SET email = :e, confirm_token = :ct, confirm_expires = :ce, confirm_sent_at = :cs WHERE id = :id');
        $stmt->execute([
            ':e' => $email, ':ct' => $confirmToken, ':ce' => time() + 86400, ':cs' => time(), ':id' => $uid,
        ]);
        $stmt = $pdo->prepare('SELECT username FROM users WHERE id = :id');
        $stmt->execute([':id' => $uid]);
        $username = (string)$stmt->fetchColumn();
        sendConfirmMail($CFG, $email, $username, $confirmToken);
        respond(200, ['ok' => true, 'email_sent' => true]);
    }

    case 'confirm': {
        // Переход по ссылке из письма. Подтверждает и АВТОЛОГИНИТ в этом же окне:
        // пишем ту же PHP-сессию, которую читает index.php.
        $u = (string)($_GET['u'] ?? '');
        $t = (string)($_GET['t'] ?? '');
        $ok = false;
        if ($u !== '' && $t !== '') {
            $stmt = $pdo->prepare('SELECT id, username, confirm_token, confirm_expires FROM users WHERE username = :u');
            $stmt->execute([':u' => $u]);
            $user = $stmt->fetch(PDO::FETCH_ASSOC);
            if ($user !== false && hash_equals((string)$user['confirm_token'], $t) && (int)$user['confirm_expires'] > time()) {
                $pdo->prepare('UPDATE users SET email_verified = 1, confirm_token = "", confirm_expires = 0, confirm_sent_at = 0 WHERE id = :id')
                    ->execute([':id' => $user['id']]);
                if (session_status() !== PHP_SESSION_ACTIVE) {
                    session_set_cookie_params(['httponly' => true, 'samesite' => 'Lax']);
                    session_start();
                }
                session_regenerate_id(true);
                unrevoke_user(db($CFG), (int)$user['id']);
                $_SESSION['kl_auth'] = [
                    'token' => issueUserToken($CFG, (int)$user['id']),
                    'role' => 'user',
                    'username' => (string)$user['username'],
                    'email' => '',
                    'email_verified' => true,
                ];
                $ok = true;
            }
        }
        header('Location: /' . ($ok ? '?confirm=ok' : '?confirm=fail'));
        exit;
    }

    case 'export':
    case 'list': {
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        // list дёшев и вызывается на каждом открытии экрана — ограничиваем
        // только выгрузку, она выгружает всю базу целиком.
        if ($action === 'export') {
            rateGuard($pdo, 'export:' . $uid, rateLimit($CFG, 'rate_export', 60), 60, 'выгрузка данных');
        }
        $visible = visibleOwnerIds($pdo, $uid);
        $out = [];
        foreach (['places','shelves','polki','containers','items'] as $t) {
            $where = ($visible === []) ? '' : ' WHERE ownerId IN (' . implode(',', $visible) . ')';
            $out[$t] = $pdo->query("SELECT * FROM $t$where ORDER BY id")->fetchAll(PDO::FETCH_ASSOC);
        }
        respond(200, $out);
    }

    case 'import': {
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        rateGuard($pdo, 'import:' . $uid, rateLimit($CFG, 'rate_import', 30), 60, 'загрузка данных');
        $in = readInput();
        $data = $in['json'] ?? null;
        if (is_string($data)) $data = json_decode($data, true);
        if (!is_array($data)) respondError(400, 'Неверный JSON');

        $pdo->beginTransaction();
        try {
            if ($uid === 0) {
                // Админ: полная замена всего склада (как раньше)
                foreach ($TABLE_MAP as $t) $pdo->exec("DELETE FROM $t");
                foreach (['places','shelves','polki','containers','items'] as $t) {
                    foreach (($data[$t] ?? []) as $r) {
                        if (!is_array($r)) continue;
                        upsert($pdo, $t, $r, $FIELDS[$t]);
                    }
                }
            } else {
                // Пользователь: обновляем ТОЛЬКО свои записи, чужие не трогаем
                foreach (['places','shelves','polki','containers','items'] as $t) {
                    $kept = []; // серверные id моих записей, которые остаются
                    foreach (($data[$t] ?? []) as $r) {
                        if (!is_array($r)) continue;
                        $localId = (int)($r['id'] ?? 0);
                        $serverId = upsertOwned($pdo, $t, $r, $FIELDS[$t], $uid, $localId);
                        $kept[] = $serverId;
                    }
                    // Удаляем мои записи, которых больше нет в дампе (заменены на телефоне)
                    $kept = array_values(array_unique(array_filter($kept, fn($k) => $k > 0)));
                    if ($kept === []) {
                        $pdo->prepare("DELETE FROM $t WHERE ownerId = ?")->execute([$uid]);
                    } else {
                        $ph = implode(',', array_fill(0, count($kept), '?'));
                        $st = $pdo->prepare("DELETE FROM $t WHERE ownerId = ? AND id NOT IN ($ph)");
                        $st->execute(array_merge([$uid], $kept));
                    }
                    // Чистим id_map для удалённых локальных id
                    $dead = $pdo->prepare("DELETE FROM id_map WHERE owner_id=:o AND type=:t AND server_id NOT IN (SELECT id FROM $t WHERE ownerId=:o2)");
                    $dead->execute([':o' => $uid, ':t' => $t, ':o2' => $uid]);
                }
            }
            $pdo->commit();
            respond(200, ['ok' => true, 'notes' => 'Импорт завершён']);
        } catch (Throwable $e) {
            $pdo->rollBack();
            error_log('[kladovka] import: ' . $e->getMessage());
            respondError(500, 'Ошибка импорта: не удалось обработать данные. Попробуйте ещё раз.');
        }
    }

    case 'share': {
        // Пользователь открывает доступ к своему складу другому пользователю
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        if ($uid === 0) respondError(400, 'Админ видит всё и не расшаривает');
        $with = trim((string)(readInput()['with_username'] ?? ''));
        if ($with === '') respondError(400, 'Укажите логин пользователя');
        $st = $pdo->prepare('SELECT id FROM users WHERE username = :u');
        $st->execute([':u' => $with]);
        $target = (int)$st->fetchColumn();
        if ($target <= 0) respondError(404, 'Пользователь не найден');
        if ($target === $uid) respondError(400, 'Нельзя расшарить самому себе');
        $pdo->prepare('INSERT OR IGNORE INTO shares (owner_user_id, shared_with_user_id) VALUES (?,?)')
            ->execute([$uid, $target]);
        respond(200, ['ok' => true, 'shared_with' => $with]);
    }

    case 'unshare': {
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        if ($uid === 0) respondError(400, 'Админ не расшаривает');
        $with = trim((string)(readInput()['with_username'] ?? ''));
        if ($with === '') respondError(400, 'Укажите логин пользователя');
        $st = $pdo->prepare('SELECT id FROM users WHERE username = :u');
        $st->execute([':u' => $with]);
        $target = (int)$st->fetchColumn();
        if ($target <= 0) respondError(404, 'Пользователь не найден');
        $pdo->prepare('DELETE FROM shares WHERE owner_user_id=? AND shared_with_user_id=?')
            ->execute([$uid, $target]);
        respond(200, ['ok' => true, 'unshared_with' => $with]);
    }

    case 'shares': {
        // Кому я дал доступ и кто дал доступ мне
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        if ($uid === 0) respond(200, ['given' => [], 'received' => []]);
        $given = $pdo->prepare('SELECT u.username FROM shares s JOIN users u ON u.id=s.shared_with_user_id WHERE s.owner_user_id=:o ORDER BY u.username');
        $given->execute([':o' => $uid]);
        $received = $pdo->prepare('SELECT u.username FROM shares s JOIN users u ON u.id=s.owner_user_id WHERE s.shared_with_user_id=:o ORDER BY u.username');
        $received->execute([':o' => $uid]);
        respond(200, [
            'given' => $given->fetchAll(PDO::FETCH_COLUMN),
            'received' => $received->fetchAll(PDO::FETCH_COLUMN),
        ]);
    }

    case 'status': {
        requireAuth($CFG, $token);
        $counts = [];
        foreach (['places','shelves','polki','containers','items'] as $t) {
            $counts[$t] = (int)$pdo->query("SELECT COUNT(*) FROM $t")->fetchColumn();
        }
        respond(200, ['ok' => true, 'counts' => $counts, 'time' => (int)(microtime(true) * 1000)]);
    }

    case 'profile': {
        // Данные пользователя для экрана «Настройки» в кабинете и в приложении
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        $stmt = $pdo->prepare('SELECT id, username, email, email_verified, created_at FROM users WHERE id = :id');
        $stmt->execute([':id' => $uid]);
        $u = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($u === false) respondError(404, 'Пользователь не найден');
        respond(200, [
            'ok' => true,
            'id' => (int)$u['id'],
            'username' => (string)$u['username'],
            'email' => (string)$u['email'],
            'email_verified' => (int)$u['email_verified'] === 1,
            'created_at' => (int)$u['created_at'],
        ]);
    }

    case 'updateProfile': {
        // Смена данных пользователя: имя / почта (с подтверждением) / пароль
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        if ($uid === 0) respondError(400, 'Для администратора настройка данных недоступна');
        $in = readInput();
        $wasVerifiedSt = $pdo->prepare('SELECT email_verified FROM users WHERE id = :id');
        $wasVerifiedSt->execute([':id' => $uid]);
        $wasVerified = (int)$wasVerifiedSt->fetchColumn() === 1;
        $now = time();
        $changes = [];
        $params = [':id' => $uid];

        if (isset($in['username'])) {
            $username = trim((string)$in['username']);
            if (strlen($username) < 3 || strlen($username) > 30) respondError(400, 'Имя: от 3 до 30 символов');
            if (!preg_match('/^[a-zA-Z0-9_]+$/', $username)) respondError(400, 'Имя: только латиница, цифры и _');
            if (strtolower($username) === 'admin') respondError(400, 'Это имя зарезервировано');
            $stmt = $pdo->prepare('SELECT id FROM users WHERE username = :u AND id <> :id');
            $stmt->execute([':u' => $username, ':id' => $uid]);
            if ($stmt->fetch()) respondError(409, 'Это имя уже занято');
            $changes[] = 'username = :username';
            $params[':username'] = $username;
        }

        $emailChanged = false;
        if (isset($in['email'])) {
            $email = strtolower(trim((string)$in['email']));
            if (!filter_var($email, FILTER_VALIDATE_EMAIL)) respondError(400, 'Некорректный email');
            if (strlen($email) > 190) respondError(400, 'Email: слишком длинный');
            $stmt = $pdo->prepare('SELECT id FROM users WHERE email = :e AND email <> "" AND id <> :id');
            $stmt->execute([':e' => $email, ':id' => $uid]);
            if ($stmt->fetch()) respondError(409, 'Этот email уже используется');
            $confirmToken = bin2hex(random_bytes(32));
            $changes[] = 'email = :email';
            $changes[] = 'email_verified = 0';
            $changes[] = 'confirm_token = :ct';
            $changes[] = 'confirm_expires = :ce';
            $changes[] = 'confirm_sent_at = :cs';
            $params[':email'] = $email;
            $params[':ct'] = $confirmToken;
            $params[':ce'] = $now + 86400;
            $params[':cs'] = $now;
            $emailChanged = true;
        }

        if (isset($in['new_password'])) {
            $nw = (string)$in['new_password'];
            if (strlen($nw) < 6) respondError(400, 'Пароль: минимум 6 символов');
            if (strlen($nw) > 128) respondError(400, 'Пароль: слишком длинный');
            $cur = (string)($in['current_password'] ?? '');
            $stmt = $pdo->prepare('SELECT password_hash FROM users WHERE id = :id');
            $stmt->execute([':id' => $uid]);
            $hash = (string)$stmt->fetchColumn();
            if ($hash === '' || !password_verify($cur, $hash)) respondError(403, 'Текущий пароль неверный');
            $changes[] = 'password_hash = :ph';
            $params[':ph'] = password_hash($nw, PASSWORD_DEFAULT);
        }

        if ($changes === []) respondError(400, 'Нет данных для обновления');
        $stmt = $pdo->prepare('UPDATE users SET ' . implode(', ', $changes) . ' WHERE id = :id');
        $stmt->execute($params);

        if ($emailChanged) {
            // Отправляем письмо с подтверждением нового адреса
            $stmt = $pdo->prepare('SELECT username FROM users WHERE id = :id');
            $stmt->execute([':id' => $uid]);
            $username = (string)$stmt->fetchColumn();
            sendConfirmMail($CFG, $email, $username, $confirmToken);
        }

        respond(200, ['ok' => true, 'email_verified' => (bool)$wasVerified && !$emailChanged, 'email_sent' => $emailChanged]);
    }

    case 'logout': {
        // Отзыв настоящий: показывать «вышли», ничего не отозвав — значит оставить
        // человеку действующий доступ к API навсегда. Токен статистичный, поэтому
        // отзываем вход пользователя целиком (у него он один).
        $uid = userIdForToken($CFG, $token);
        if ($uid !== null) {
            revoke_user(db($CFG), $uid);
        }
        respond(200, ['ok' => true]);
    }

    case 'upload_photo': {
        requireAuth($CFG, $token);
        $uid = userIdForToken($CFG, $token);
        rateGuard($pdo, 'photo:' . $uid, rateLimit($CFG, 'rate_photo', 30), 60, 'загрузка фото');

        if (empty($_FILES['photo']) || $_FILES['photo']['error'] !== UPLOAD_ERR_OK) {
            respondError(400, 'Файл не загружен');
        }
        $file = $_FILES['photo'];
        $maxSize = 5 * 1024 * 1024; // 5 МБ
        if ($file['size'] > $maxSize) {
            respondError(400, 'Файл слишком большой (максимум 5 МБ)');
        }

        // Определение типа: предпочитаем finfo (если расширение установлено),
        // иначе getimagesize — он же проверяет, что файл действительно изображение
        if (class_exists('finfo')) {
            $finfo = new finfo(FILEINFO_MIME_TYPE);
            $mime = (string)$finfo->file($file['tmp_name']);
        } else {
            $info = @getimagesize($file['tmp_name']);
            if ($info === false) respondError(400, 'Файл не является изображением');
            $mime = (string)($info['mime'] ?? '');
        }
        $allowed = [
            'image/jpeg' => 'jpg',
            'image/png'  => 'png',
            'image/webp' => 'webp',
        ];
        if (!isset($allowed[$mime])) {
            respondError(400, 'Допустимые форматы: JPEG, PNG, WebP');
        }

        $dir = dirname($CFG['db']) . '/../photos';
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        // De-duplication по MD5: приложение передаёт контрольную сумму файла.
        // Файл с тем же хешем уже загружен — возвращаем его адрес, не плодя копии
        // (повторная синхронизация не накапливает дубликаты фото).
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = $_SERVER['HTTP_HOST'] ?? 'kladovka.dr6ter.ru';
        $md5 = trim((string)($_POST['md5'] ?? ''));
        if (preg_match('/^[0-9a-f]{32}$/i', $md5)) {
            $fname = 'm-' . strtolower($md5) . '.' . $allowed[$mime];
            $fpath = $dir . '/' . $fname;
            if (is_file($fpath) && filesize($fpath) > 0) {
                respond(200, ['ok' => true, 'url' => $scheme . '://' . $host . '/photos/' . $fname]);
            }
            if (move_uploaded_file($file['tmp_name'], $fpath)) {
                $dest = $fpath;
                $url = $scheme . '://' . $host . '/photos/' . $fname;
                respond(200, ['ok' => true, 'url' => $url]);
            }
            respondError(500, 'Не удалось сохранить файл');
        }

        $name = 'photo-' . $uid . '-' . bin2hex(random_bytes(6)) . '.' . $allowed[$mime];
        $dest = $dir . '/' . $name;
        if (!move_uploaded_file($file['tmp_name'], $dest)) {
            respondError(500, 'Не удалось сохранить файл');
        }
        $url = $scheme . '://' . $host . '/photos/' . $name;
        respond(200, ['ok' => true, 'url' => $url]);
    }

    default: {
        if (isset($TABLE_MAP[$action]) && str_starts_with($action, 'save_')) {
            requireAuth($CFG, $token);
            $uid = userIdForToken($CFG, $token);
            $table = $TABLE_MAP[$action];
            $in = readInput();
            $rec = array_merge($_POST, $in);
            $rec['id'] = isset($rec['id']) ? (int)$rec['id'] : 0;
            try {
                $pdo->beginTransaction();
                $isNew = !((int)($rec['id'] ?? 0) > 0);
                if (!$isNew && !canEditRecord($pdo, $uid, $table, (int)$rec['id'])) {
                    respondError(403, 'Нет доступа к этой записи');
                }
                $useFields = $FIELDS[$table];
                if ($uid > 0) {
                    // Владельца проставляем только своим записям.
                    // При совместном редактировании чужой (расшаренной) записи
                    // владелец НЕ меняется — иначе запись «перетекает» гостю.
                    $isMine = true;
                    if (!$isNew) {
                        $st = $pdo->prepare("SELECT ownerId FROM $table WHERE id = ?");
                        $st->execute([(int)$rec['id']]);
                        $cur = $st->fetchColumn();
                        $isMine = ($cur === false || (int)$cur === $uid);
                    }
                    if ($isMine) {
                        $rec['ownerId'] = $uid;
                        $useFields = array_merge($useFields, ['ownerId']);
                    }
                }
                upsert($pdo, $table, $rec, $useFields);
                $newId = $isNew ? (int)$pdo->lastInsertId() : (int)$rec['id'];
                $pdo->commit();
                respond(200, ['ok' => true, 'table' => $table, 'id' => $newId]);
            } catch (Throwable $e) {
                $pdo->rollBack();
                error_log('[kladovka] save_' . $table . ': ' . $e->getMessage());
                respondError(500, 'Ошибка сохранения. Попробуйте ещё раз.');
            }
        }
        if (isset($TABLE_MAP[$action]) && str_starts_with($action, 'delete_')) {
            requireAuth($CFG, $token);
            $uid = userIdForToken($CFG, $token);
            $table = $TABLE_MAP[$action];
            $in = readInput();
            $id = (int)(array_merge($_POST, $in)['id'] ?? 0);
            if ($id <= 0) respondError(400, 'Нет id');
            if (!canEditRecord($pdo, $uid, $table, $id)) respondError(403, 'Нет доступа к этой записи');
            try {
                $pdo->beginTransaction();
                switch ($table) {
                    case 'places':
                        $pdo->prepare("UPDATE shelves SET placeId=NULL WHERE placeId=:id")->execute(['id' => $id]);
                        $pdo->prepare("UPDATE polki SET placeId=NULL WHERE placeId=:id")->execute(['id' => $id]);
                        $pdo->prepare("UPDATE containers SET placeId=NULL WHERE placeId=:id")->execute(['id' => $id]);
                        $pdo->prepare("UPDATE items SET placeId=NULL WHERE placeId=:id")->execute(['id' => $id]);
                        break;
                    case 'shelves':
                        $pdo->prepare("UPDATE polki SET shelfId=NULL WHERE shelfId=:id")->execute(['id' => $id]);
                        $pdo->prepare("UPDATE containers SET shelfId=NULL WHERE shelfId=:id")->execute(['id' => $id]);
                        $pdo->prepare("UPDATE items SET shelfId=NULL WHERE shelfId=:id")->execute(['id' => $id]);
                        break;
                    case 'polki':
                        // На полки никто не ссылается — связей обнулять не нужно
                        break;
                    case 'containers':
                        $pdo->prepare("UPDATE items SET containerId=NULL WHERE containerId=:id")->execute(['id' => $id]);
                        break;
                }
                $pdo->prepare("DELETE FROM $table WHERE id=:id")->execute(['id' => $id]);
                $pdo->commit();
                respond(200, ['ok' => true, 'table' => $table]);
            } catch (Throwable $e) {
                $pdo->rollBack();
                error_log('[kladovka] delete_' . $table . ': ' . $e->getMessage());
                respondError(500, 'Ошибка удаления. Попробуйте ещё раз.');
            }
        }
        respondError(400, 'Неизвестное действие: ' . $action);
    }
}


/* ================= latestApk: метаданные свежего APK (публично, без токена) ================= */

/**
 * Сборки в каталоге.
 *
 * Перебор + регулярка вместо glob: glob в PHP регистрозависим на всех
 * платформах, включая Windows. Шаблон Kladovka-*.apk не находит лежащий рядом
 * kladovka-v1.2.apk, и latestApk отвечает 404 при полностью рабочей раскладке.
 */
function kladovkaApksIn(string $dir): array
{
    // Подкаталога download/ может не быть: без проверки scandir() кидает
    // Warning прямо в вывод, и ответ уезжает с кодом 200 вместо своего.
    if (!is_dir($dir)) {
        return [];
    }
    $entries = scandir($dir);
    if ($entries === false) {
        return [];
    }
    $out = [];
    foreach ($entries as $entry) {
        if ($entry === '' || $entry[0] === '.') {
            continue;
        }
        if (!preg_match('/^kladovka[-_ ]v\d+(?:\.\d+)?.*\.apk$/i', $entry)) {
            continue;
        }
        $path = $dir . '/' . $entry;
        if (is_file($path)) {
            $out[] = $path;
        }
    }
    return $out;
}

function latestApk(array $CFG): void {
    $root = rtrim($CFG['root'] ?? dirname(__FILE__), '/');
    $candidates = [];
    foreach ([$root . '/download', $root] as $dir) {
        foreach (kladovkaApksIn($dir) as $f) {
            $m = [];
            // Сортируем по ВЕРСИИ из имени (v1.30 -> 130), а не по дате файла
            if (preg_match('/Kladovka[-_ ]v(\d+)\.(\d+)\.apk/i', basename($f), $m)) {
                $candidates[$f] = (int)$m[1] * 100 + (int)$m[2];
            } else {
                $candidates[$f] = filemtime($f);
            }
        }
    }
    if (!$candidates) { respondError(404, 'APK не найден'); }
    arsort($candidates);
    $apk = array_key_first($candidates);
    $name = basename($apk);
    $m = [];
    if (preg_match('/Kladovka[-_ ]v(\d+)\.(\d+)\.apk/i', $name, $m)) {
        $vn = 'v' . $m[1] . '.' . $m[2];
        $vc = (int)$m[1] * 100 + (int)$m[2];
    } else {
        $vn = $name;
        $vc = (int)filemtime($apk);
    }
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host   = $_SERVER['HTTP_HOST'] ?? 'kladovka.dr6ter.ru';
    $rel    = '/' . ltrim(str_replace($root, '', $apk), '/');
    $url    = $scheme . '://' . $host . $rel;
    respond(200, [
        'versionCode' => $vc,
        'versionName' => $vn,
        'url'         => $url,
        'md5'         => md5_file($apk),
        'size'        => filesize($apk),
    ]);
}
