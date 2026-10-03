<?php
// Кладовка — веб-интерфейс (вход/регистрация, редактирование, поиск).
declare(strict_types=1);

// Сжатие страницы (gzip), если клиент поддерживает — быстрее загрузка и меньше трафика
if (function_exists('ob_gzhandler') && !ob_start('ob_gzhandler')) ob_start();

$CFG = require __DIR__ . '/../../server-config.php';
session_set_cookie_params(['httponly' => true, 'samesite' => 'Lax', 'secure' => !empty($_SERVER['HTTPS'])]);
session_start();

// Актуальная сборка на сервере — для кнопки «Скачать приложение» в шапке.
// Каталог верхнего уровня: на сервере кабинет лежит в /cabinet/, а сборки —
// в корне сайта; рядом с ними может быть и подкаталог download/.
//
// Перебор каталога регуляркой, а не glob: glob в PHP регистрозависим на всех
// платформах, включая Windows, и шаблон Kladovka-*.apk не находит лежащий рядом
// kladovka-v1.2.apk — кнопка просто не появлялась ни в кабинете, ни в шапке.
// Версия выбирается из имени как (major, minor), а не по дате файла, и правило
// обязано совпадать с kladovkaNewestByExt() в index.php и kladovkaLatest()
// в download-handler.php, иначе ссылки разойдутся версиями.
$webRoot = dirname(__DIR__);
$apkHref = '';
$apkVersion = '';
$apkBest = null;
foreach ([$webRoot, $webRoot . '/download'] as $dir) {
    $prefix = $dir === $webRoot ? '' : 'download/';
    if (!is_dir($dir)) {
        continue;
    }
    $entries = scandir($dir);
    if ($entries === false) {
        continue;
    }
    foreach ($entries as $entry) {
        if ($entry === '' || $entry[0] === '.') {
            continue;
        }
        $m = [];
        if (!preg_match('/^kladovka[-_ ]v(\d+)\.(\d+)\.apk$/i', $entry, $m)) {
            continue;
        }
        if (!is_file($dir . '/' . $entry)) {
            continue;
        }
        $major = (int)$m[1];
        $minor = (int)($m[2] ?? 0);
        if ($apkBest === null
            || $major > $apkBest['major']
            || ($major === $apkBest['major'] && $minor > $apkBest['minor'])) {
            $apkBest = ['href' => $prefix . $entry, 'major' => $major, 'minor' => $minor];
        }
    }
}
if ($apkBest !== null) {
    $apkHref = $apkBest['href'];
    $apkVersion = $apkBest['major'] . '.' . $apkBest['minor'];
}

$scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
if (!empty($_SERVER['HTTP_X_FORWARDED_PROTO'])) $scheme = $_SERVER['HTTP_X_FORWARDED_PROTO'];
$apiUrl = $scheme . '://' . ($_SERVER['HTTP_HOST'] ?? '127.0.0.1') . '/api.php';

function klApi(string $url, array $payload, string $token = ''): array {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));
    if ($token !== '') curl_setopt($ch, CURLOPT_HTTPHEADER, ['Authorization: Bearer ' . $token]);
    if (!empty($_SERVER['HTTPS'])) { curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false); curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false); }
    $res = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    $j = json_decode((string)$res, true);
    return [$code, is_array($j) ? $j : []];
}
function klNewCaptcha(): void {
    $a = random_int(2, 12); $b = random_int(2, 12);
    $_SESSION['kl_cap_a'] = $a + $b;
    $_SESSION['kl_cap_q'] = "$a + $b";
}

$session = '';
$authRole = '';
$authUser = '';
$authEmailVerified = true;
$authEmail = '';
$error = '';
$regError = '';
$regOk = '';
$mailError = '';
$mailOk = '';
$view = in_array(($_GET['view'] ?? ''), ['register', 'settings'], true) ? (string)$_GET['view'] : 'login';
$confirmMsg = $_GET['confirm'] ?? '';

// Flash-сообщения (PRG): после обработки формы делаем редирект (GET),
// чтобы браузер не спрашивал «повторить отправку?» при обновлении страницы.
$flash = $_SESSION['kl_flash'] ?? null;
unset($_SESSION['kl_flash']);
if (is_array($flash)) {
    foreach (['error', 'regError', 'regOk', 'mailError', 'mailOk', 'setError', 'setOk'] as $fl) {
        if (!empty($flash[$fl])) $$fl = (string)$flash[$fl];
    }
}

// ---------- выход ----------
if (isset($_GET['logout'])) {
    // Отзываем API-токен на сервере. Раньше здесь стоял best-effort, потому что
    // api.php на logout просто отвечал ok и ничего не отзывал: выход из кабинета
    // стирал сессию в браузере, но доступ к API оставался выданным навсегда.
    if (!empty($_SESSION['kl_auth']['token'])) {
        klApi($apiUrl . '?action=logout', [], (string)$_SESSION['kl_auth']['token']);
    }
    unset($_SESSION['kl_auth'], $_SESSION['kl_cap_a'], $_SESSION['kl_cap_q']);
    header('Location: /');
    exit;
}

// Восстановление авторизации из сессии (в т.ч. после авто-входа из ссылки подтверждения)
if (!empty($_SESSION['kl_auth']) && is_array($_SESSION['kl_auth'])) {
    $session = (string)($_SESSION['kl_auth']['token'] ?? '');
    $authRole = (string)($_SESSION['kl_auth']['role'] ?? '');
    $authUser = (string)($_SESSION['kl_auth']['username'] ?? '');
    $authEmail = (string)($_SESSION['kl_auth']['email'] ?? '');
    $authEmailVerified = (bool)($_SESSION['kl_auth']['email_verified'] ?? false);
}

// ---------- вход ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['password']) && !isset($_POST['reg_username'])) {
    $token = $_SESSION['csrf_token'] ?? '';
    $submitted = $_POST['_csrf'] ?? '';
    if ($token === '' || !hash_equals($token, $submitted)) {
        http_response_code(403);
        die('CSRF token mismatch');
    }
    $payload = ['password' => (string)$_POST['password']];
    if (trim((string)($_POST['username'] ?? '')) !== '') $payload['username'] = trim((string)$_POST['username']);
    [$code, $j] = klApi($apiUrl . '?action=login', $payload);
    if ($code === 200) {
        $session = (string)($j['token'] ?? '');
        $authRole = (string)($j['role'] ?? '');
        $authUser = (string)($j['username'] ?? '');
        $authEmailVerified = (bool)($j['email_verified'] ?? true);
        $authEmail = (string)($j['email'] ?? '');
        session_regenerate_id(true);
        $_SESSION['kl_auth'] = [
            'token' => $session, 'role' => $authRole, 'username' => $authUser,
            'email' => $authEmail, 'email_verified' => $authEmailVerified,
        ];
        // PRG: успешный вход — редирект в кабинет (GET), сессия восстановит авторизацию
        header('Location: /cabinet/');
        exit;
    } else {
        // PRG: ошибка — редирект с flash-сообщением, форма остаётся пустой и обновляется без запроса
        $_SESSION['kl_flash'] = ['error' => (string)($j['error'] ?? ('Ошибка входа (HTTP ' . $code . ')'))];
        header('Location: /cabinet/' . (($_GET['view'] ?? '') === 'register' ? '?view=register' : ''));
        exit;
    }
}

// (автологин после подтверждения уже сделан api.php -> сессия kl_auth; сбрасывать её нельзя)

// ---------- отправка ссылки подтверждения (для старых/неподтверждённых аккаунтов) ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['mail_action']) && $session !== '') {
    $email = strtolower(trim((string)($_POST['mail_email'] ?? '')));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $_SESSION['kl_flash'] = ['mailError' => 'Некорректный email'];
    } else {
        [$code, $j] = klApi($apiUrl . '?action=verify_email', ['email' => $email], $session);
        if ($code === 200) {
            $_SESSION['kl_flash'] = ['mailOk' => 'Ссылка отправлена на ' . htmlspecialchars($email) . '. Проверьте почту (и спам).'];
            $authEmail = $email;
        } else {
            $_SESSION['kl_flash'] = ['mailError' => (string)($j['error'] ?? ('Ошибка (HTTP ' . $code . ')'))];
        }
    }
    header('Location: /cabinet/');
    exit;
}

// ---------- регистрация ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['reg_username'])) {
    $view = 'register';
    $u = trim((string)$_POST['reg_username']);
    $email = strtolower(trim((string)($_POST['reg_email'] ?? '')));
    $p1 = (string)($_POST['reg_password'] ?? '');
    $p2 = (string)($_POST['reg_password2'] ?? '');
    $cap = (string)($_POST['reg_captcha'] ?? '');
    $capOk = isset($_SESSION['kl_cap_a']) && (string)(int)$cap === (string)(int)$_SESSION['kl_cap_a'];
    if (!$capOk) {
        $regError = 'Неверный ответ на контрольный вопрос';
    } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $regError = 'Некорректный email';
    } elseif ($p1 !== $p2) {
        $regError = 'Пароли не совпадают';
    } elseif (strlen($p1) < 6) {
        $regError = 'Пароль: минимум 6 символов';
    } elseif (strlen($u) < 3 || strlen($u) > 30 || !preg_match('/^[a-zA-Z0-9_]+$/', $u)) {
        $regError = 'Имя: 3-30 символов, только латиница, цифры и _';
    } else {
        [$code, $j] = klApi($apiUrl . '?action=register', ['username' => $u, 'email' => $email, 'password' => $p1]);
        if ($code === 200 && !empty($j['email_sent'])) {
            $regOk = 'Аккаунт создан. На ' . htmlspecialchars($email) . ' отправлена ссылка для подтверждения — проверьте почту (и спам).';
        } else {
            $regError = (string)($j['error'] ?? ('Ошибка регистрации (HTTP ' . $code . ')'));
        }
    }
    if ($regOk === '') klNewCaptcha();
    // PRG: редирект на страницу регистрации (GET) с flash-сообщением
    $_SESSION['kl_flash'] = [
        'regError' => $regError,
        'regOk' => $regOk,
    ];
    header('Location: /cabinet/?view=register');
    exit;
}
// ---------- настройки данных пользователя (смена логина / почты / пароля) ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['set_action'])) {
    while (in_array(($_POST['set_action'] ?? ''), ['profile', 'password'], true)) {
        $setError = '';
        $setOk = '';
        $payload = [];
        if (isset($_POST['set_username'])) {
            $su = trim((string)$_POST['set_username']);
            if ($su !== '') $payload['username'] = $su;
        }
        if (isset($_POST['set_email'])) {
            $se = strtolower(trim((string)$_POST['set_email']));
            if ($se !== '') $payload['email'] = $se;
        }
        if (isset($_POST['set_current']) || isset($_POST['set_new']) || isset($_POST['set_new2'])) {
            $cur = (string)$_POST['set_current'];
            $n1 = (string)$_POST['set_new'];
            $n2 = (string)$_POST['set_new2'];
            if ($n1 !== $n2) {
                $setError = 'Новые пароли не совпадают';
                break;
            }
            if (strlen($n1) < 6) {
                $setError = 'Пароль: минимум 6 символов';
                break;
            }
            $payload = ['current_password' => $cur, 'new_password' => $n1];
        }
        if ($payload === []) {
            $setError = 'Нет данных для изменения';
            break;
        }
        if ($session === '') {
            $setError = 'Сессия истекла — войдите ещё раз';
            break;
        }
        [$code, $j] = klApi($apiUrl . '?action=updateProfile', $payload, $session);
        if ($code === 200) {
            $setOk = 'Данные обновлены';
            if (!empty($j['email_changed'])) $setOk .= ' На новую почту отправлена ссылка для подтверждения.';
        } else {
            $setError = (string)($j['error'] ?? ('Ошибка (HTTP ' . $code . ')'));
        }
        break;
    }
    $_SESSION['kl_flash'] = ['setError' => $setError, 'setOk' => $setOk];
    header('Location: /cabinet/?view=settings');
    exit;
}

// ---------- настройки данных пользователя (смена логина / почты / пароля) ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['set_action']) && $session !== '') {
    $setError = '';
    $setOk = '';
    $payload = [];
    if (($_POST['set_action'] ?? '') === 'profile') {
        if (isset($_POST['set_username'])) {
            $su = trim((string)$_POST['set_username']);
            if ($su !== '' && $su !== $authUser) $payload['username'] = $su;
        }
        if (isset($_POST['set_email'])) {
            $se = strtolower(trim((string)$_POST['set_email']));
            if ($se !== '' && $se !== $authEmail) $payload['email'] = $se;
        }
    } elseif (($_POST['set_action'] ?? '') === 'password') {
        $cur = (string)($_POST['set_current_password'] ?? '');
        $p1 = (string)($_POST['set_new_password'] ?? '');
        $p2 = (string)($_POST['set_new_password2'] ?? '');
        if ($p1 !== $p2) {
            $setError = 'Новые пароли не совпадают';
        } elseif (strlen($p1) < 6) {
            $setError = 'Пароль: минимум 6 символов';
        } else {
            $payload = ['current_password' => $cur, 'new_password' => $p1];
        }
    }
    if ($payload === []) $setError = 'Нет данных для изменения';
    if ($setError === '' && $payload !== []) {
        [$setCode, $setJ] = klApi($apiUrl . '?action=updateProfile', $payload, $session);
        if ($setCode === 200) {
            $setOk = 'Данные обновлены';
            if (!empty($setJ['email_changed'])) $setOk .= '. На новую почту отправлена ссылка для подтверждения.';
        } else {
            $setError = (string)($setJ['error'] ?? ('Ошибка (HTTP ' . $setCode . ')'));
        }
    }
    $_SESSION['kl_flash'] = [
        'setError' => $setError,
        'setOk' => $setOk,
    ];
    header('Location: /cabinet/?view=settings');
    exit;
}
$setOk = $setOk ?? '';
$setError = $setError ?? '';


// неподтверждённая почта: показываем экран подтверждения, а не приложение
$needConfirm = $session !== '' && $authRole === 'user' && !$authEmailVerified;
if (!$needConfirm) {
    $authed = $session !== '';
} else {
    $authed = false;
}
if ($authed || $view === 'register') klNewCaptcha();
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Кладовка — база данных</title>
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="icon" href="/favicon.png" type="image/png" sizes="32x32">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap" rel="stylesheet">
<style>
/* ===== Кладовка — Futuristic Dark Theme ===== */
@keyframes kl-fadeUp{from{opacity:0;transform:translateY(24px)}to{opacity:1;transform:translateY(0)}}
/* box-shadow-анимации заменены на transform/opacity: визуально то же «дыхание», но без
   пересчёта теней каждый кадр (главный источник тормозов на слабых устройствах) */
@keyframes kl-pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.06)}}
@keyframes kl-glow{0%,100%{transform:scale(1)}50%{transform:scale(1.04)}}
@keyframes kl-badgePulse{0%,100%{transform:scale(1)}50%{transform:scale(1.08)}}
@keyframes newpulse{0%,100%{opacity:1}50%{opacity:.45}}
:root{
  --primary:#00f0ff; --accent:#a855f7; --bg:#06090f;
  --card-bg:rgba(255,255,255,.04); --line:rgba(255,255,255,.07); --line-strong:rgba(255,255,255,.12);
  --ink:#e0e6ed; --muted:#5a6a7a; --faint:#7b8a99;
  --amber:#fbbf24; --amber-soft:rgba(251,191,36,.08);
  --danger:#f43f5e; --danger-soft:rgba(244,63,94,.08);
  --ok:#22c55e;
  --r-lg:16px; --r-md:10px; --r-sm:7px;
  --sh:0 2px 16px rgba(0,0,0,.35);
}
*{box-sizing:border-box}
html,body{margin:0;padding:0}
html{scroll-behavior:smooth}
@media (prefers-reduced-motion: reduce){ html{scroll-behavior:auto} }
body{
  font-family:'Inter',system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
  background:var(--bg);color:var(--ink);font-size:14px;line-height:1.45;-webkit-font-smoothing:antialiased;
  min-height:100vh;
}
body::before{
  content:'';position:fixed;inset:0;pointer-events:none;z-index:0;
  background:
    radial-gradient(600px 300px at 12% -8%, rgba(0,240,255,.10), transparent 60%),
    radial-gradient(700px 350px at 88% 105%, rgba(168,85,247,.09), transparent 60%);
}

/* --- шапка (hero) --- */
.top{
  position:relative;z-index:1;
  background:linear-gradient(135deg, rgba(0,240,255,.10) 0%, rgba(168,85,247,.10) 50%, rgba(0,240,255,.06) 100%);
  border:1px solid rgba(0,240,255,.18);border-radius:20px;
  margin:18px auto;max-width:1200px;width:calc(100% - 32px);
  color:#fff;padding:22px 26px;display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;
  box-shadow:0 0 24px rgba(0,240,255,.08), 0 4px 24px rgba(0,0,0,.4);
  transition:border-color .4s,box-shadow .4s;
}
.top:hover{border-color:rgba(0,240,255,.32);box-shadow:0 0 36px rgba(0,240,255,.14),0 4px 24px rgba(0,0,0,.5)}
.brand{display:flex;align-items:center;gap:14px;min-width:0}
a.brand{color:inherit;text-decoration:none}
a.brand:hover{text-decoration:none;opacity:.92}
.brand:hover .logo{animation-duration:.9s}
.brand .logo{
  width:44px;height:44px;border-radius:14px;background:rgba(0,240,255,.06);
  border:1px solid rgba(0,240,255,.25);display:grid;place-items:center;font-size:24px;flex:none;
  box-shadow:0 0 8px rgba(0,240,255,.18);
  animation:kl-pulse 3s ease-in-out infinite;
}
.brand h1{margin:0;font-size:20px;font-weight:800;letter-spacing:.4px;
  background:linear-gradient(90deg,var(--primary),var(--accent));
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}
.brand .sub{font-size:12px;color:#8fb4b8;margin-top:2px;letter-spacing:.3px}
.stats{display:flex;gap:8px;flex-wrap:wrap}
.stat{
  background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);border-radius:999px;
  padding:6px 14px;font-size:13px;font-weight:700;display:flex;gap:6px;align-items:center;
  transition:border-color .25s,box-shadow .25s,transform .25s;
}
.stat:hover{border-color:rgba(0,240,255,.3);box-shadow:0 0 14px rgba(0,240,255,.12);transform:translateY(-2px)}

/* --- контейнер --- */
.wrap{position:relative;z-index:1;max-width:1200px;margin:0 auto;padding:4px 16px 48px}
.card{
  background:var(--card-bg);border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--sh);
  padding:18px;margin-bottom:16px;
  transition:border-color .3s,box-shadow .3s;
}
.card:hover{border-color:rgba(255,255,255,.1);box-shadow:0 4px 24px rgba(0,0,0,.45)}
.card h3{margin:0 0 12px;font-size:15px;font-weight:700;
  background:linear-gradient(90deg,var(--ink),var(--muted));
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}
/* анимация появления — ТОЛЬКО при первой загрузке страницы,
   чтобы фоновые обновления (каждые 7 с) не «прыгали» */
html.kl-first .top,html.kl-first .tabs,html.kl-first .card{animation:kl-fadeUp .5s ease-out both}
html.kl-first #totals .stat{animation:kl-fadeUp .5s ease-out both}
html.kl-first #totals .stat:nth-child(1){animation-delay:.05s}
html.kl-first #totals .stat:nth-child(2){animation-delay:.1s}
html.kl-first #totals .stat:nth-child(3){animation-delay:.15s}
html.kl-first #totals .stat:nth-child(4){animation-delay:.2s}

/* --- кнопки --- */
.btn{display:inline-flex;align-items:center;gap:7px;padding:8px 14px;border-radius:var(--r-md);
  border:1px solid rgba(255,255,255,.1);background:rgba(255,255,255,.04);color:var(--ink);
  font:inherit;font-weight:600;cursor:pointer;white-space:nowrap;
  transition:all .2s}
.btn:hover{background:rgba(255,255,255,.08);border-color:rgba(255,255,255,.18);transform:translateY(-1px)}
.btn:active{transform:translateY(1px)}
.btn:disabled{opacity:.55;cursor:default}
.btn-primary{
  background:linear-gradient(135deg,var(--primary),var(--accent));border-color:transparent;color:#fff;
  box-shadow:0 0 20px rgba(0,240,255,.2);
}
.btn-primary:hover{box-shadow:0 0 30px rgba(0,240,255,.35),0 4px 16px rgba(168,85,247,.2);filter:brightness(1.1)}
.btn-danger{color:var(--danger);border-color:rgba(244,63,94,.25)}
.btn-danger:hover{background:var(--danger-soft)}
.btn-ghost{border-color:transparent;background:transparent;color:var(--muted);font-weight:600}
.btn-ghost:hover{background:rgba(255,255,255,.05);color:var(--ink)}
.btn-sm{padding:4px 10px;font-size:12.5px;border-radius:var(--r-sm)}
#btnRefresh{box-shadow:0 0 10px rgba(0,240,255,.2);animation:kl-glow 2.5s ease-in-out infinite}

/* --- вкладки --- */
.tabs{display:flex;gap:6px;margin-bottom:16px;flex-wrap:wrap}
.tab-btn{
  flex:1;min-width:120px;display:flex;align-items:center;justify-content:center;gap:8px;
  padding:10px 10px;border:1px solid rgba(255,255,255,.06);border-radius:var(--r-lg);
  background:var(--card-bg);color:var(--muted);font:inherit;font-weight:600;cursor:pointer;
  transition:all .25s;
}
.tab-btn:hover{background:rgba(255,255,255,.06);color:var(--ink);border-color:rgba(255,255,255,.12)}
.tab-btn.active{
  background:linear-gradient(135deg,rgba(0,240,255,.12),rgba(168,85,247,.08));
  border-color:rgba(0,240,255,.32);color:var(--primary);
  box-shadow:0 0 14px rgba(0,240,255,.12);
}
.tab-btn .cnt{background:rgba(255,255,255,.06);border-radius:999px;font-size:12px;padding:1px 8px;font-weight:700}
.tab-btn.active .cnt{background:rgba(0,240,255,.14);color:var(--primary)}
.tab-btn .cnt-new{background:var(--ok);color:#fff;border-radius:999px;font-size:11.5px;padding:1px 8px;font-weight:700;animation:kl-badgePulse 1.5s ease-in-out infinite}
.tab-btn.active .cnt-new{background:var(--ok);color:#fff}
.badge-new{display:inline-block;background:var(--ok);color:#fff;border-radius:999px;padding:0 8px;
  font-size:10.5px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;vertical-align:1.5px;margin-left:8px;line-height:1.7;
  animation:kl-badgePulse 2s ease-in-out infinite}
.badge-new.pulse{animation:newpulse 1.4s ease-in-out 3}
.tab{display:none}
.tab.active{display:block}

/* --- панель инструментов --- */
.toolbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:16px}
.status{margin-left:auto;font-size:12.5px;color:var(--muted);min-height:18px}
.status.ok{color:var(--ok);font-weight:600;text-shadow:0 0 8px rgba(34,197,94,.4)}
.status.err{color:var(--danger);font-weight:600}

/* --- формы --- */
.field{display:flex;flex-direction:column;gap:5px}
.field label{font-size:12px;font-weight:600;color:var(--muted)}
.field label .req{color:var(--danger)}
.field input,.field select,.field textarea{
  padding:9px 12px;border:1px solid var(--line-strong);border-radius:var(--r-md);
  background:rgba(0,0,0,.3);color:var(--ink);font:inherit;outline:none;
  transition:border-color .2s,box-shadow .3s;
}
.field input:focus,.field select:focus,.field textarea:focus{
  border-color:var(--primary);box-shadow:0 0 0 3px rgba(0,240,255,.1),0 0 16px rgba(0,240,255,.08);
}
.field textarea{resize:vertical;min-height:44px}
.form-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:12px}
.form-actions{margin-top:14px;display:flex;gap:8px;align-items:center}
.edit-note{display:inline-flex;align-items:center;gap:6px;font-size:12.5px;color:var(--amber);background:var(--amber-soft);border-radius:999px;padding:3px 11px;font-weight:600}

/* --- поиск и таблицы --- */
.search-row{display:flex;align-items:center;gap:8px;margin-bottom:10px}
.search-row input{flex:1;padding:10px 14px;border:1px solid var(--line-strong);border-radius:var(--r-md);background:rgba(0,0,0,.25);color:var(--ink);font:inherit;outline:none;transition:border-color .2s,box-shadow .3s}
.search-row input:focus{border-color:var(--primary);box-shadow:0 0 0 3px rgba(0,240,255,.1)}
.search-row .hint-result{font-size:12px;color:var(--faint);white-space:nowrap}
.tablewrap{overflow-x:auto;border:1px solid var(--line);border-radius:var(--r-md)}
table{width:100%;border-collapse:collapse;font-size:13.5px;min-width:560px}
thead th{background:rgba(0,240,255,.03);color:var(--muted);font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;padding:10px 12px;text-align:left;border-bottom:1px solid var(--line);white-space:nowrap}
tbody td{padding:10px 12px;border-bottom:1px solid rgba(255,255,255,.04);vertical-align:top}
tbody tr:last-child td{border-bottom:none}
tbody tr:nth-child(even){background:rgba(255,255,255,.015)}
tbody tr{transition:background .2s}
tbody tr:hover{background:rgba(0,240,255,.03)}
td.num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
td.nowrap{white-space:nowrap}
td.wide{min-width:190px;white-space:normal}
td.muted{color:var(--faint)}
.actions-cell{text-align:right;white-space:nowrap;width:1%}
.actions-cell .btn{margin-left:6px}
.cat{display:inline-block;
  background:linear-gradient(135deg,rgba(168,85,247,.15),rgba(0,240,255,.1));
  border:1px solid rgba(168,85,247,.15);color:var(--accent);
  border-radius:999px;padding:2px 10px;font-size:11.5px;font-weight:600}
.empty{padding:26px;text-align:center;color:var(--faint);font-size:13.5px}
.chip{display:inline-block;border:1px solid var(--line);background:rgba(255,255,255,.04);color:var(--muted);
  border-radius:999px;padding:5px 12px;font-size:12.5px;font-weight:600;cursor:pointer;transition:all .2s}
.chip:hover{border-color:var(--primary);color:var(--ink)}
.chip.on{background:linear-gradient(135deg,rgba(168,85,247,.3),rgba(0,240,255,.2));
  border-color:rgba(168,85,247,.5);color:#fff}

/* --- ключ API --- */
.key-row{display:flex;gap:8px;align-items:stretch}
.key-row input{flex:1;padding:9px 12px;border:1px dashed var(--line-strong);border-radius:var(--r-md);background:rgba(0,0,0,.3);font-family:ui-monospace,Consolas,monospace;font-size:13px;color:var(--ink)}
.hint{font-size:12.5px;color:var(--muted);margin:2px 0 0}
code{background:rgba(0,240,255,.05);border:1px solid var(--line);padding:1px 6px;border-radius:5px;font-size:12px;color:var(--primary)}

/* --- экран входа --- */
.login-page{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;position:relative;z-index:1}
.login-card{max-width:400px;width:100%;background:var(--card-bg);border:1px solid rgba(0,240,255,.14);
  border-radius:var(--r-lg);box-shadow:0 0 40px rgba(0,240,255,.08),0 8px 32px rgba(0,0,0,.5);
  animation:kl-fadeUp .5s ease-out both}
.login-logo{display:flex;align-items:center;gap:12px;margin-bottom:18px}
.login-logo .logo{width:52px;height:52px;border-radius:14px;
  background:linear-gradient(135deg,var(--primary),var(--accent));color:#fff;display:grid;place-items:center;font-size:28px;
  box-shadow:0 0 24px rgba(0,240,255,.35);animation:kl-pulse 3s ease-in-out infinite}
.login-logo h1{margin:0;font-size:22px;
  background:linear-gradient(90deg,var(--primary),var(--accent));
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}
.login-logo p{margin:2px 0 0;color:var(--muted);font-size:13px}

/* --- подвал --- */
.foot{position:relative;z-index:1;text-align:center;color:var(--muted);font-size:11.5px;padding:20px 16px 32px;letter-spacing:.3px;border-top:1px solid rgba(255,255,255,.04);max-width:1200px;margin:0 auto 40px}

@media (max-width:720px){
  .wrap{padding:4px 12px 40px}
  .top{padding:16px;margin:12px}
  .tab-btn{min-width:0;font-size:12.5px}
  .status{margin-left:0;width:100%}
  .key-row{flex-wrap:wrap}
  .key-row .btn{flex:1}
  .search-row{flex-wrap:wrap}
}
</style>
<!-- PWA -->
<link rel="manifest" href="/manifest.json">
<meta name="theme-color" content="#00f0ff">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<meta name="apple-mobile-web-app-title" content="Кладовка">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<!-- /PWA -->
</head>
<body>
<?php if ($needConfirm): ?>
<div class="login-page">
  <div class="card login-card">
    <div class="login-logo">
      <div class="logo">📮</div>
      <div>
        <h1>Подтвердите почту</h1>
        <p><?= htmlspecialchars($authUser) ?>, аккаунт ждёт подтверждения</p>
      </div>
    </div>
    <?php if ($mailOk): ?><p style="color:var(--ok);font-weight:600;margin:0 0 12px">✅ <?= htmlspecialchars($mailOk) ?></p><?php endif; ?>
    <?php if ($mailError): ?><p style="color:var(--danger);font-weight:600;margin:0 0 12px">⚠ <?= htmlspecialchars($mailError) ?></p><?php endif; ?>
    <?php if ($authEmail !== '' && $mailOk === ''): ?>
      <p style="margin:0 0 12px;color:var(--muted)">Мы отправили ссылку на <b style="color:var(--ink)"><?= htmlspecialchars($authEmail) ?></b>. Перейдите по ней, чтобы подтвердить аккаунт.</p>
      <p class="hint" style="margin:0 0 14px">Не пришло? Проверьте папку «Спам» или отправьте ещё раз:</p>
      <form method="post" autocomplete="off">
        <div class="field">
          <label>Email</label>
          <input type="email" name="mail_email" required value="<?= htmlspecialchars($authEmail) ?>">
        </div>
        <div class="form-actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary" name="mail_action" value="resend">Отправить ещё раз</button>
          <a href="/cabinet/" class="btn btn-ghost">Назад</a>
        </div>
      </form>
    <?php else: ?>
      <p style="margin:0 0 12px;color:var(--muted)">Укажите почту — на неё придёт письмо со ссылкой для подтверждения аккаунта:</p>
      <form method="post" autocomplete="off">
        <div class="field">
          <label>Email</label>
          <input type="email" name="mail_email" required placeholder="you@example.com" autofocus>
        </div>
        <div class="form-actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary" name="mail_action" value="send">Отправить ссылку</button>
          <a href="/cabinet/" class="btn btn-ghost">Назад</a>
        </div>
      </form>
    <?php endif; ?>
  </div>
</div>
<?php elseif (!$authed): ?>
<div class="login-page">
  <div class="card login-card">
    <div class="login-logo">
      <div class="logo">📦</div>
      <div>
        <h1>Кладовка</h1>
        <p>база данных хранилища</p>
      </div>
    </div>
    <div style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin:0 0 16px">
      <a class="btn btn-sm" href="/" title="На главную — сайт Кладовки">🌐 Сайт</a>
      <?php if ($apkHref !== ''): ?>
        <a class="btn btn-sm" href="/<?= $apkHref ?>" title="Скачать приложение для Android<?= $apkVersion !== '' ? ' (версия ' . $apkVersion . ')' : '' ?>">📱 Приложение<?= $apkVersion !== '' ? ' v' . $apkVersion : '' ?></a>
      <?php endif; ?>
    </div>

    <?php if ($confirmMsg === 'ok'): ?><p style="color:var(--ok);font-weight:600;margin:0 0 12px">📧 Почта подтверждена! Теперь войдите.</p><?php endif; ?>
    <?php if ($confirmMsg === 'fail'): ?><p style="color:var(--danger);font-weight:600;margin:0 0 12px">⚠ Ссылка недействительна или истекла. Войдите и запросите новую.</p><?php endif; ?>

    <?php if ($view === 'register'): ?>
    <?php if ($regError): ?><p style="color:var(--danger);font-weight:600;margin:0 0 12px">⚠ <?= htmlspecialchars($regError) ?></p><?php endif; ?>
    <?php if ($regOk): ?><p style="color:var(--ok);font-weight:600;margin:0 0 12px">✅ <?= htmlspecialchars($regOk) ?></p><?php endif; ?>
    <form method="post" autocomplete="off">
      <input type="hidden" name="reg_username_marker" value="1">
      <div class="field">
        <label>Имя пользователя</label>
        <input type="text" name="reg_username" required minlength="3" maxlength="30" pattern="[a-zA-Z0-9_]+" placeholder="латиница, цифры, _"
               value="<?= htmlspecialchars(trim((string)($_POST['reg_username'] ?? ''))) ?>" autofocus>
      </div>
      <div class="field" style="margin-top:10px">
        <label>Email</label>
        <input type="email" name="reg_email" required placeholder="you@example.com"
               value="<?= htmlspecialchars(trim((string)($_POST['reg_email'] ?? ''))) ?>">
      </div>
      <div class="field" style="margin-top:10px">
        <label>Пароль</label>
        <input type="password" name="reg_password" required minlength="6" placeholder="минимум 6 символов">
      </div>
      <div class="field" style="margin-top:10px">
        <label>Повторите пароль</label>
        <input type="password" name="reg_password2" required>
      </div>
      <div class="field" style="margin-top:10px">
        <label>Сколько будет <?= htmlspecialchars((string)($_SESSION['kl_cap_q'] ?? '')) ?>? <span class="hint" style="color:var(--muted);font-weight:400">(защита от ботов)</span></label>
        <input type="text" name="reg_captcha" required inputmode="numeric">
      </div>
      <div class="form-actions" style="margin-top:16px">
        <button type="submit" class="btn btn-primary">Создать аккаунт</button>
        <a href="/cabinet/" class="btn btn-ghost">Войти</a>
      </div>
    </form>
    <p class="hint" style="margin-top:14px">Создайте аккаунт, чтобы смотреть и редактировать базу хранилища. После регистрации подтвердите почту по ссылке из письма.</p>
    <?php else: ?>
    <?php if ($error): ?><p style="color:var(--danger);font-weight:600;margin:0 0 12px">⚠ <?= htmlspecialchars($error) ?></p><?php endif; ?>
    <form method="post" autocomplete="off">
      <div class="field">
        <label>Логин</label>
        <input type="text" name="username" placeholder="Имя пользователя" autocomplete="username"
               value="<?= htmlspecialchars(trim((string)($_POST['username'] ?? ''))) ?>">
      </div>
      <div class="field" style="margin-top:10px">
        <label>Пароль</label>
        <input type="password" name="password" required autofocus autocomplete="current-password">
      </div>
      <div class="form-actions" style="margin-top:16px">
        <button type="submit" class="btn btn-primary">Войти</button>
        <a href="/cabinet/?view=register" class="btn btn-ghost">Создать аккаунт</a>
      </div>
    </form>
    <p class="hint" style="margin-top:14px">Клиенты регистрируют аккаунт себе и подтверждают почту.</p>
    <?php endif; ?>
  </div>
</div>
<?php elseif ($view === 'settings' && $session !== ''): ?>

<div class="login-page">
  <div class="card login-card" style="max-width:460px">
    <div class="login-logo">
      <div class="logo">⚙️</div>
      <div>
        <h1>Настройки</h1>
        <p>Данные вашего аккаунта</p>
      </div>
    </div>

    <?php if ($setOk !== ''): ?><p style="color:var(--ok);font-weight:600;margin:0 0 12px">✅ <?= htmlspecialchars($setOk) ?></p><?php endif; ?>
    <?php if ($setError !== ''): ?><p style="color:var(--danger);font-weight:600;margin:0 0 12px">⚠ <?= htmlspecialchars($setError) ?></p><?php endif; ?>

    <h3 style="margin:0 0 4px;font-size:14px;color:var(--ink)">👤 Данные аккаунта</h3>
    <p class="hint" style="margin:0 0 14px">Логин — <?= htmlspecialchars($authUser) ?> · почта — <?= htmlspecialchars($authEmail !== '' ? $authEmail : 'не указана') ?> <?= $authEmailVerified ? '· подтверждена ✅' : '· не подтверждена' ?></p>
    <form method="post" autocomplete="off">
      <input type="hidden" name="set_action" value="profile">
      <div class="field">
        <label>Имя пользователя</label>
        <input type="text" name="set_username" minlength="3" maxlength="30" pattern="[a-zA-Z0-9_]+"
               value="<?= htmlspecialchars($authUser) ?>" autofocus>
      </div>
      <div class="field" style="margin-top:10px">
        <label>Email</label>
        <input type="email" name="set_email" value="<?= htmlspecialchars($authEmail) ?>">
      </div>
      <div class="form-actions" style="margin-top:14px">
        <button type="submit" class="btn btn-primary">Сохранить данные</button>
      </div>
    </form>

    <form method="post" autocomplete="off" style="margin-top:22px">
      <input type="hidden" name="set_action" value="password">
      <h3 style="margin:0 0 10px;font-size:14px;color:var(--ink)">🔒 Смена пароля</h3>
      <div class="field">
        <label>Текущий пароль</label>
        <input type="password" name="set_current_password" required minlength="6">
      </div>
      <div class="field" style="margin-top:10px">
        <label>Новый пароль</label>
        <input type="password" name="set_new_password" required minlength="6">
      </div>
      <div class="field" style="margin-top:10px">
        <label>Повторите новый пароль</label>
        <input type="password" name="set_new_password2" required minlength="6">
      </div>
      <div class="form-actions" style="margin-top:14px">
        <button type="submit" class="btn btn-primary">Сменить пароль</button>
      </div>
    </form>

    <div class="form-actions" style="margin-top:18px">
      <a href="/cabinet/" class="btn btn-ghost">← Вернуться в кабинет</a>
    </div>
  </div>
</div>

<?php else: ?>

<header class="top">
  <a class="brand" href="/" title="На главную — сайт Кладовки">
    <div class="logo">📦</div>
    <div>
      <h1>Кладовка</h1>
      <div class="sub"><?= htmlspecialchars($authUser !== '' ? ('👤 ' . $authUser . ($authRole === 'admin' ? ' (админ)' : '')) : 'Места · Стеллажи · Полки · Контейнеры · Вещи') ?></div>
    </div>
  </a>
  <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
    <div class="stats" id="totals"></div>
    <?php if ($apkHref !== ''): ?>
      <a class="btn btn-sm" href="/<?= $apkHref ?>" title="Скачать приложение для Android<?= $apkVersion !== '' ? ' (версия ' . $apkVersion . ')' : '' ?>">📱 Приложение<?= $apkVersion !== '' ? ' v' . $apkVersion : '' ?></a>
    <?php endif; ?>
    <a class="btn btn-sm" href="/cabinet/?logout" title="Выйти и вернуться на сайт">⏻ Выйти</a>
  </div>
</header>

<div class="wrap">

  <nav class="tabs" id="tabnav"></nav>

  <div class="toolbar">
    <button class="btn btn-primary" id="btnRefresh">⟳ Обновить</button>
    <button class="btn" id="btnExport">⬇ Скачать бэкап</button>
    <button class="btn" id="btnImport">⬆ Импорт из файла</button>
    <a class="btn" href="/cabinet/?view=settings">⚙ Настройки</a>
    <span class="status" id="status"></span>
  </div>

  <div id="tab-places" class="tab active"></div>
  <div id="tab-shelves" class="tab"></div>
  <div id="tab-polki" class="tab"></div>
  <div id="tab-containers" class="tab"></div>
  <div id="tab-items" class="tab"></div>
  <div id="tab-stats" class="tab"></div>
  <div id="tab-shares" class="tab"></div>

  <?php if ($authRole === 'admin'): ?>
  <div class="card">
    <h3>🔑 API-ключ</h3>
    <p class="hint" style="margin-bottom:12px">Для внешних скриптов и автоматизации (права администратора). Ключ передаётся только заголовком <code>Authorization: Bearer &lt;ключ&gt;</code>. В URL он больше не принимается: query-строка попадает в логи веб-сервера, в Referer и в историю браузера.</p>
    <div class="key-row">
      <input type="text" id="apiKey" value="<?= htmlspecialchars($CFG['api_key']) ?>" readonly>
      <button class="btn" id="btnCopyKey">Копировать</button>
    </div>
  </div>
  <?php endif; ?>

</div>

<div class="foot">📦 Кладовка · База данных хранилища · Синхронизация с приложением · Автообновление каждые 7&nbsp;с</div>

<script>
const TOKEN = <?= json_encode($session) ?>;
const IS_ADMIN = <?= json_encode($authRole === 'admin') ?>;
const API = location.origin + '/api.php';
let DB = {places:[],shelves:[],polki:[],containers:[],items:[]};
let editing = {places:0, shelves:0, polki:0, containers:0, items:0};
let query =   {places:'', shelves:'', polki:'', containers:'', items:''};
// Фильтры вкладки «Вещи»: только «последние» (1 шт), выбранные категории, сортировка.
let itemsUi = {last:false, cats:[], sort:'name'};
let refBusy = false;
// Черновик незавершённой формы добавления: table -> {field: value}.
// Автообновление раз в 7 с перерисовывает активную вкладку — черновик
// не даёт потерять уже введённые данные, пока пользователь не сохранил запись.
let formDraft = {};

const META = {
  places: {
    title:'Места', icon:'🏠', add:'Добавить место',
    searchPh:'Место, заметки…',
    cols:[
      {f:'name',      label:'Название', req:true},
      {f:'latitude',  label:'Широта',   num:true, align:'right'},
      {f:'longitude', label:'Долгота',  num:true, align:'right'},
      {f:'notes',     label:'Заметки',  multi:true, wide:true}
    ]
  },
  shelves: {
    title:'Стеллажи', icon:'🗄', add:'Добавить стеллаж',
    searchPh:'Стеллаж, заметки…',
    cols:[
      {f:'name',    label:'Название', req:true},
      {f:'placeId', label:'Место',    ref:'places'},
      {f:'notes',   label:'Заметки',  multi:true, wide:true}
    ]
  },
  polki: {
    title:'Полки', icon:'📚', add:'Добавить полку',
    searchPh:'Полка, заметки…',
    cols:[
      {f:'name',    label:'Название', req:true},
      {f:'shelfId', label:'Стеллаж',  ref:'shelves'},
      {f:'placeId', label:'Место',    ref:'places'},
      {f:'notes',   label:'Заметки',  multi:true, wide:true}
    ]
  },
  containers: {
    title:'Контейнеры', icon:'📦', add:'Добавить контейнер',
    searchPh:'Контейнер, заметки…',
    cols:[
      {f:'name',    label:'Название', req:true},
      {f:'shelfId', label:'Стеллаж',  ref:'shelves'},
      {f:'placeId', label:'Место',    ref:'places'},
      {f:'notes',   label:'Заметки',  multi:true, wide:true}
    ]
  },
  items: {
    title:'Вещи', icon:'🧺', add:'Добавить вещь',
    searchPh:'Название, категория, заметки…',
    cols:[
      {f:'name',      label:'Название',  req:true},
      {f:'quantity',  label:'Кол-во',    num:true, qty:true, align:'right'},
      {f:'unit',      label:'Ед.'},
      {f:'category',  label:'Категория', chip:true},
      {f:'placeId',   label:'Место',     ref:'places'},
      {f:'shelfId',   label:'Стеллаж',   ref:'shelves'},
      {f:'containerId',label:'Контейнер',ref:'containers'},
      {f:'photoUrl',  label:'Фото',      photo:true},
      {f:'pinned',    label:'⭐',        pin:true, align:'center', title:'Закреплённые'},
      {f:'notes',     label:'Заметки',   multi:true, wide:true},
      {f:'updatedAt', label:'Обновлено', date:true, nowrap:true}
    ]
  },
  stats: { title:'Статистика', icon:'📊' },
  shares:{ title:'Доступ',     icon:'🤝' },
};
const TABS = ['places','shelves','polki','containers','items','stats','shares'];
const HAS_DATA = ['places','shelves','polki','containers','items'];

function esc(s){ const d=document.createElement('div'); d.textContent=(s==null?'':String(s)); return d.innerHTML.replace(/"/g,'&quot;').replace(/'/g,'&#39;'); }
function safeUrl(v){ const s=String(v||'').trim(); if(!s) return ''; if(/^[a-zA-Z][a-zA-Z0-9+.-]*:/i.test(s) && !/^https?:\/\//i.test(s)) return ''; return s.replace(/[<>"'\x00-\x1f]/g,''); }
function fmtDate(v){ if(!v) return '—'; const d=new Date(Number(v)); return isNaN(d)?'—':d.toLocaleString('ru-RU',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}); }
function nameOf(table,id){ if(id==null||id==='') return ''; const r=DB[table].find(x=>x.id===Number(id)); return r?r.name:''; }
function setStatus(msg,isErr){ const el=document.getElementById('status'); el.textContent=msg||''; el.className='status'+(isErr?' err':msg?' ok':''); }

// «Новое»: запись создана после последнего визита этой страницы.
const SEEN = Number(localStorage.getItem('kladovka-seen')) || Date.now();
localStorage.setItem('kladovka-seen', String(Date.now()));
function isNew(r){ return !!r && Number(r.createdAt||0) > SEEN; }
function newCount(t){ return DB[t].filter(isNew).length; }
const NEW_TOTAL=HAS_DATA.reduce((a,t)=>a+newCount(t),0);

async function api(action,body){
  const opt={method:'GET'};
  if(body!==undefined){opt.method='POST';opt.body=JSON.stringify(body);}
  opt.headers={'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'};
  const r=await fetch(API+'?action='+action,opt);
  const j=await r.json().catch(()=>({error:'Ошибка разбора ответа сервера'}));
  if(r.status!==200) throw new Error(j.error||('HTTP '+r.status));
  return j;
}

function dependents(table,id){
  if(table==='places') return DB.shelves.filter(x=>x.placeId==id).length+DB.polki.filter(x=>x.placeId==id).length+DB.containers.filter(x=>x.placeId==id).length+DB.items.filter(x=>x.placeId==id).length;
  if(table==='shelves') return DB.polki.filter(x=>x.shelfId==id).length+DB.containers.filter(x=>x.shelfId==id).length+DB.items.filter(x=>x.shelfId==id).length;
  if(table==='polki') return 0;
  if(table==='containers') return DB.items.filter(x=>x.containerId==id).length;
  return 0;
}

// ---------- вкладки ----------
// Активная вкладка сохраняется в URL (#polki) — обновление страницы её не сбрасывает.
let ACTIVE_TAB = TABS.includes((location.hash||'').slice(1)) ? location.hash.slice(1) : 'places';
function renderTabs(active){
  ACTIVE_TAB=active;
  const nav=document.getElementById('tabnav');
  nav.innerHTML='';
  TABS.forEach(t=>{
    const b=document.createElement('button');
    b.className='tab-btn'+(t===active?' active':'');
    b.dataset.tab=t;
    const isData = HAS_DATA.includes(t);
    const nc = isData ? newCount(t) : 0;
    const cnt = isData ? (' <span class="cnt">'+DB[t].length+'</span>') : '';
    b.innerHTML='<span>'+META[t].icon+' '+META[t].title+'</span>'+cnt+(nc>0?' <span class="cnt-new">+'+nc+'</span>':'');
    b.onclick=()=>{ ACTIVE_TAB=t; try{ history.replaceState(null,'','#'+t); }catch(e){} document.querySelectorAll('.tab-btn').forEach(x=>x.classList.remove('active')); b.classList.add('active'); document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active')); const tabEl=document.getElementById('tab-'+t); if(!tabEl) return; tabEl.classList.add('active'); if(!isData) renderPane(t); else render(t); };
    nav.appendChild(b);
  });
}

// ---------- доп. вкладки (Статистика / Доступ) ----------
function renderPane(t){
  if(t==='stats') renderStats();
  else if(t==='shares') renderShares();
}

function renderStats(){
  const wrap=document.getElementById('tab-stats');
  wrap.innerHTML='';
  const card=document.createElement('div'); card.className='card';
  const L=n=>DB[n].length;
  const totals=[['🏠 Места',L('places')],['🗄 Стеллажи',L('shelves')],['📚 Полки',L('polki')],['📦 Контейнеры',L('containers')],['🧺 Вещи',L('items')]];
  let h='<div style="display:flex;gap:20px;flex-wrap:wrap;margin-bottom:16px">';
  totals.forEach(x=>{ h+='<div style="flex:1;min-width:120px;text-align:center;padding:16px;border-radius:var(--r-md);background:rgba(255,255,255,.04);border:1px solid var(--line)"><div style="font-size:28px;font-weight:800;color:var(--primary)">'+x[1]+'</div><div style="color:var(--muted);font-size:13px">'+x[0]+'</div></div>'; });
  h+='</div>';
  // категории
  const cats={};
  DB.items.forEach(it=>{ const c=(it.category||'').trim(); if(c) cats[c]=(cats[c]||0)+1; });
  const catArr=Object.entries(cats).sort((a,b)=>b[1]-a[1]);
  if(catArr.length){
    h+='<h3 style="margin:12px 0 8px">Категории вещей</h3>';
    h+='<div style="display:flex;flex-wrap:wrap;gap:8px">';
    catArr.slice(0,15).forEach(c=>{ h+='<span class="cat">'+esc(c[0])+' · '+c[1]+'</span>'; });
    h+='</div>';
  }
  // по местам
  const byPlace={};
  DB.items.forEach(it=>{ const p=nameOf('places',it.placeId); const k=p||'— без места —'; byPlace[k]=(byPlace[k]||0)+1; });
  const placeArr=Object.entries(byPlace).sort((a,b)=>b[1]-a[1]);
  if(placeArr.length){
    h+='<h3 style="margin:16px 0 8px">Вещи по местам</h3>';
    h+='<table style="width:100%;border-collapse:collapse;font-size:13px"><tbody>';
    placeArr.slice(0,15).forEach(p=>{
      h+='<tr><td style="padding:6px 8px;border-bottom:1px solid var(--line)">'+esc(p[0])+'</td><td style="padding:6px 8px;text-align:right;border-bottom:1px solid var(--line);font-weight:700">'+p[1]+'</td></tr>';
    });
    h+='</tbody></table>';
  }
  const orphan = DB.items.filter(it=>!it.placeId&&!it.shelfId&&!it.containerId).length;
  if(orphan>0){ h+='<p class="hint" style="margin-top:14px;color:var(--amber)">⚠ '+orphan+' вещь(и) без места — их сложнее найти.</p>'; }
  card.innerHTML=h; wrap.appendChild(card);
}

async function renderShares(){
  const wrap=document.getElementById('tab-shares');
  wrap.innerHTML='';
  const card=document.createElement('div'); card.className='card';
  let h='<h3>🤝 Совместный доступ</h3>';
  if(IS_ADMIN){
    h+='<p class="hint" style="margin:8px 0 14px">Вы вошли как администратор — видите все записи всех пользователей. Совместный доступ нужен обычным аккаунтам.</p>';
    card.innerHTML=h; wrap.appendChild(card);
    return;
  }
  h+='<p class="hint" style="margin:8px 0 14px">Откройте свои записи другому пользователю — он будет видеть и редактировать ваш склад. Он также может открыть доступ вам.</p>';
  h+='<div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap"><input type="text" id="shareName" placeholder="Логин пользователя" style="flex:1;min-width:180px;padding:11px 13px;border-radius:var(--r-md);background:rgba(255,255,255,.05);border:1px solid var(--line);color:var(--ink);font-size:14px">'
    +'<button class="btn btn-primary" id="btnShare">Поделиться</button></div>';
  card.innerHTML=h;
  wrap.appendChild(card);
  const list=document.createElement('div');
  list.innerHTML='<div style="color:var(--muted)">Загрузка…</div>';
  card.appendChild(list);
  const btnShare=document.getElementById('btnShare');
  btnShare.onclick=async()=>{
    const nm=document.getElementById('shareName').value.trim();
    if(!nm){ setStatus('Введите логин пользователя',true); return; }
    btnShare.disabled=true;
    try{ await api('share',{with_username:nm}); setStatus('Доступ открыт для «'+nm+'»'); document.getElementById('shareName').value=''; await renderShares(); }
    catch(e){ setStatus('Ошибка: '+e.message,true); }
    finally{ btnShare.disabled=false; }
  };
  try{
    const d=await api('shares');
    const makeRow=(who,isGiven)=>{
      const tr=document.createElement('tr');
      tr.innerHTML='<td style="padding:8px;border-bottom:1px solid var(--line)">'+esc(who)+'</td>'
        +'<td style="padding:8px;border-bottom:1px solid var(--line);text-align:right">'
        +(isGiven?'<button class="btn btn-sm btn-danger" data-who="'+esc(who)+'">Отозвать</button>':'<span class="hint">—</span>')
        +'</td>';
      if(isGiven) tr.querySelector('[data-who]').onclick=async()=>{
        if(!confirm('Отозвать доступ для «'+who+'»?')) return;
        try{ await api('unshare',{with_username:who}); setStatus('Доступ отозван: «'+who+'»'); await renderShares(); }
        catch(e){ setStatus('Ошибка: '+e.message,true); }
      };
      return tr;
    };
    let b='<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;flex-wrap:wrap">';
    b+='<div><h4 style="margin:0 0 8px;font-size:14px;color:var(--muted)">Я открыл доступ</h4>';
    b+=(d.given&&d.given.length)?'<table style="width:100%;border-collapse:collapse;font-size:13px">'+d.given.map(w=>makeRow(w,true).outerHTML).join('')+'</table>':'<div class="empty" style="padding:12px">Вы никому не открывали доступ</div>';
    b+='</div>';
    b+='<div><h4 style="margin:0 0 8px;font-size:14px;color:var(--muted)">Кто открыл мне</h4>';
    b+=(d.received&&d.received.length)?'<table style="width:100%;border-collapse:collapse;font-size:13px">'+d.received.map(w=>makeRow(w,false).outerHTML).join('')+'</table>':'<div class="empty" style="padding:12px">Никто не делился с вами</div>';
    b+='</div></div>';
    list.innerHTML=b;
  }catch(e){ list.innerHTML='<div style="color:var(--danger)">Ошибка: '+esc(e.message)+'</div>'; }
}

// ---------- рендер вкладки ----------
// Оптимизация: render(t) рисует только одну вкладку; render() — все data-вкладки.
const renderOne = (table)=>render(table);
function render(only){
  (only?[only]:HAS_DATA).forEach(table=>{
    const wrap=document.getElementById('tab-'+table);
    const meta=META[table];
    wrap.innerHTML='';

    // --- форма добавления/редактирования ---
    const editRow = editing[table] ? DB[table].find(x=>x.id===editing[table]) : null;
    const f=document.createElement('div'); f.className='card';
    f.innerHTML='<h3>'+(editRow?'✏️ Редактирование':'➕ '+meta.add)+'</h3>';
    if(editRow) f.innerHTML+='<div class="edit-note" style="margin-bottom:10px">Изменяете: «'+esc(editRow.name)+'»</div>';

    const grid=document.createElement('div'); grid.className='form-grid';
    meta.cols.forEach(c=>{
      if(c.date) return; // время проставляет сервер
      const fd=document.createElement('div'); fd.className='field';
      const lb=document.createElement('label');
      lb.innerHTML=esc(c.label)+(c.req?' <span class="req">*</span>':'');
      let inp;
      if(c.ref){
        inp=document.createElement('select');
        inp.innerHTML='<option value="">— не выбрано —</option>'+DB[c.ref].map(x=>'<option value="'+x.id+'">'+esc(x.name)+'</option>').join('');
        if(editRow && editRow[c.f]!=null) inp.value=String(editRow[c.f]);
      }else if(c.pin){
        // --- закреплённая вещь (⭐) ---
        inp=document.createElement('label');
        inp.style.cssText='display:flex;align-items:center;gap:8px;cursor:pointer;color:var(--text)';
        const cb=document.createElement('input'); cb.type='checkbox'; cb.dataset.field=c.f;
        cb.checked=!(!editRow || !editRow[c.f]);
        cb.style.width='18px'; cb.style.height='18px'; cb.style.accentColor='#FFC107';
        inp.appendChild(cb); inp.appendChild(document.createTextNode('Закрепить в начале'));
      }else if(c.photo){
        // --- загрузка фото (multipart -> upload_photo) ---
        inp=document.createElement('div');
        inp.style.display='flex'; inp.style.alignItems='center'; inp.style.gap='10px'; inp.style.flexWrap='wrap';
        const hid=document.createElement('input'); hid.type='hidden'; hid.dataset.field=c.f;
        const cur=editRow? (editRow[c.f]||'') : '';
        hid.value=cur;
        const prev=document.createElement('img');
        const curSafe=safeUrl(cur);
        if(curSafe){ prev.src=curSafe; prev.style.cssText='width:56px;height:56px;object-fit:cover;border-radius:8px;border:1px solid var(--line)'; }
        else if(cur){ prev.style.display='none'; }
        const lab=document.createElement('label'); lab.className='btn btn-sm'; lab.textContent='📷 Загрузить';
        lab.style.cursor='pointer';
        const fi=document.createElement('input'); fi.type='file'; fi.accept='image/jpeg,image/png,image/webp'; fi.hidden=true;
        fi.onchange=async()=>{
          const file=fi.files[0]; if(!file) return;
          lab.textContent='⏳ Загрузка…';
          try{
            const fd=new FormData(); fd.append('photo',file);
            const r=await fetch(API+'?action=upload_photo',{method:'POST',headers:{'Authorization':'Bearer '+TOKEN},body:fd});
            const j=await r.json();
            if(!r.ok||!j.url) throw new Error(j.error||('HTTP '+r.status));
            hid.value=j.url; prev.src=safeUrl(j.url); prev.style.display=''; prev.style.cssText='width:56px;height:56px;object-fit:cover;border-radius:8px;border:1px solid var(--line)';
            setStatus('Фото загружено. Сохраните вещь, чтобы применить.');
          }catch(e){ setStatus('Ошибка загрузки фото: '+e.message,true); }
          finally{ lab.textContent='📷 Загрузить'; fi.value=''; }
        };
        const delBtn=document.createElement('button'); delBtn.type='button'; delBtn.className='btn btn-sm btn-danger'; delBtn.textContent='✕';
        delBtn.onclick=()=>{ hid.value=''; prev.removeAttribute('src'); prev.style.display='none'; };
        lab.appendChild(fi);
        inp.appendChild(hid); inp.appendChild(prev); inp.appendChild(lab); inp.appendChild(delBtn);
      }else{
        inp=document.createElement(c.multi?'textarea':'input');
        if(inp.tagName==='TEXTAREA'){ inp.rows=2; }
        else if(c.num){ inp.type='number'; if(c.qty){ inp.min=1; } }
        if(editRow){
          const v=editRow[c.f];
          inp.value=(v==null?'':v);
          if(c.num&&c.qty&&!v) inp.value=1;
        }else if(c.num&&c.qty){ inp.value=1; }
      }
      if(!c.photo) inp.dataset.field=c.f; // у фото data-field висит на hidden-инпуте
      fd.appendChild(lb); fd.appendChild(inp); grid.appendChild(fd);
    });
    // --- черновик формы: запоминаем ввод в форму добавления ---
    if(!editRow){
      grid.addEventListener('input',()=>{
        const d=formDraft[table]||(formDraft[table]={});
        grid.querySelectorAll('[data-field]').forEach(i=>{ d[i.dataset.field]= i.type==='checkbox' ? (i.checked?'1':'') : i.value; });
      });
    }
    f.appendChild(grid);

    const fa=document.createElement('div'); fa.className='form-actions';
    const saveBtn=document.createElement('button'); saveBtn.className='btn btn-primary';
    saveBtn.textContent=editRow?'Сохранить изменения':'Сохранить';
    saveBtn.onclick=()=>{
      const row={}; let ok=true;
      if(editRow) row.id=editRow.id;
      grid.querySelectorAll('[data-field]').forEach(i=>{
        let v;
        if(i.type==='checkbox'){ v=i.checked?1:0; }
        else { v=i.value==null?'':String(i.value).trim(); }
        const c=meta.cols.find(x=>x.f===i.dataset.field);
        if(c&&c.req&&v===''){ ok=false; i.style.borderColor='var(--danger)'; }
        if(c&&c.num){ const n=parseFloat(v); v=isNaN(n)?null:((c.qty&&n<1)?1:n); }
        row[i.dataset.field]=(v===''?null:v);
      });
      if(!ok){ setStatus('Заполните обязательные поля (со звёздочкой).',true); return; }
      saveBtn.disabled=true; saveBtn.textContent='Сохранение…';
      saveRow(table,row).finally(()=>{ saveBtn.disabled=false; saveBtn.textContent=editRow?'Сохранить изменения':'Сохранить'; });
    };
    fa.appendChild(saveBtn);
    if(editRow){
      const cancelBtn=document.createElement('button'); cancelBtn.className='btn btn-ghost'; cancelBtn.textContent='Отмена';
      cancelBtn.onclick=()=>{ editing[table]=0; render(); };
      fa.appendChild(cancelBtn);
    }
    f.appendChild(fa);
    // --- восстановить черновик, если форма добавления уже заполнялась ---
    if(!editRow && formDraft[table]){
      grid.querySelectorAll('[data-field]').forEach(i=>{
        const k=i.dataset.field;
        if(k in formDraft[table] && formDraft[table][k]!==null){
          if(i.type==='checkbox') i.checked=formDraft[table][k]==='1';
          else i.value=formDraft[table][k];
        }
      });
    }
    wrap.appendChild(f);

    // --- поиск + таблица ---
    const c=document.createElement('div'); c.className='card';
    const rows=DB[table];
    const q=query[table].trim().toLowerCase();
    const filtered=q?rows.filter(r=>{
      const hay=[r.name,r.category,r.notes,r.unit].concat(meta.cols.filter(x=>x.ref).map(x=>nameOf(x.ref,r[x.f])||'')).join(' ').toLowerCase();
      return hay.includes(q);
    }):rows;

    // --- фильтры и сортировка вещей (как в приложении) ---
    let vis=filtered;
    if(table==='items'){
      if(itemsUi.last) vis=vis.filter(r=>r.quantity<=1);
      if(itemsUi.cats.length>0) vis=vis.filter(r=>itemsUi.cats.includes(r.category));
      const key=itemsUi.sort;
      const pinFirst=(a,b)=>(Number(b.pinned||0)-Number(a.pinned||0));
      if(key==='qty') vis=[...vis].sort((a,b)=>pinFirst(a,b)||(b.quantity-a.quantity)||a.name.localeCompare(b.name,'ru'));
      else if(key==='cat') vis=[...vis].sort((a,b)=>pinFirst(a,b)||a.category.localeCompare(b.category,'ru')||a.name.localeCompare(b.name,'ru'));
      else if(key==='date') vis=[...vis].sort((a,b)=>pinFirst(a,b)||(Number(b.updatedAt||0)-Number(a.updatedAt||0))||a.name.localeCompare(b.name,'ru'));
      else vis=[...vis].sort((a,b)=>pinFirst(a,b)||a.name.localeCompare(b.name,'ru'));
    }

    if(rows.length===0){
      c.innerHTML='<div class="empty">Пока пусто — добавьте первую запись выше ⬆</div>';
    }else{
      let filterRow=null;
      if(table==='items'){
        const catsArray=[...new Set(DB.items.map(x=>x.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'ru'));
        filterRow=document.createElement('div'); filterRow.className='item-filters';
        filterRow.style.cssText='display:flex;flex-wrap:wrap;gap:6px;margin-bottom:10px;align-items:center';
        const mkChip=(label,on,onClick)=>{
          const b=document.createElement('button'); b.type='button'; b.className='chip'+(on?' on':'');
          b.textContent=label; b.onclick=onClick; return b;
        };
        filterRow.appendChild(mkChip('❗ Последние',itemsUi.last,()=>{ itemsUi.last=!itemsUi.last; render('items'); }));
        catsArray.forEach(c=>{
          filterRow.appendChild(mkChip(esc(c),itemsUi.cats.includes(c),()=>{
            itemsUi.cats=itemsUi.cats.includes(c)?itemsUi.cats.filter(x=>x!==c):[...itemsUi.cats,c];
            render('items');
          }));
        });
        const sel=document.createElement('select'); sel.className='sort-sel';
        sel.style.cssText='margin-left:auto;padding:7px 10px;border-radius:var(--r-md);border:1px solid var(--line);background:rgba(255,255,255,.05);color:var(--ink);font:inherit;font-size:13px';
        sel.innerHTML='<option value="name">По имени</option><option value="qty">По количеству</option><option value="cat">По категории</option><option value="date">Сначала изменённые</option>';
        sel.value=itemsUi.sort;
        sel.onchange=()=>{ itemsUi.sort=sel.value; render('items'); };
        filterRow.appendChild(sel);
        const csvBtn=document.createElement('button'); csvBtn.type='button'; csvBtn.className='chip';
        csvBtn.textContent='📥 CSV';
        csvBtn.title='Скачать все вещи в CSV (открывается в Excel/таблицах)';
        csvBtn.onclick=exportCsv;
        filterRow.appendChild(csvBtn);
      }
      if(filterRow) c.appendChild(filterRow);
      const sr=document.createElement('div'); sr.className='search-row';
      const si=document.createElement('input'); si.placeholder=meta.searchPh; si.value=query[table];
      // debounce поиска — не перерисовывать DOM на каждое нажатие
      si.oninput=()=>{ query[table]=si.value; clearTimeout(si._t); si._t=setTimeout(()=>render(table),200); };
      const hint=document.createElement('span'); hint.className='hint-result';
      const filtActive=table==='items'&&(itemsUi.last||itemsUi.cats.length>0||itemsUi.sort!=='name');
      hint.textContent=(q||filtActive)?('найдено: '+vis.length+' из '+rows.length):(rows.length+' записей');
      sr.appendChild(si); sr.appendChild(hint); c.appendChild(sr);

      if(vis.length===0){
        const e=document.createElement('div'); e.className='empty';
        e.textContent=(q||filtActive)?'Ничего не найдено по фильтрам.':'Ничего не найдено по запросу.';
        c.appendChild(e);
      }else{
        const tw=document.createElement('div'); tw.className='tablewrap';
        let h='<table><thead><tr>';
        meta.cols.forEach(col=>h+='<th>'+esc(col.label)+'</th>');
        h+='<th class="actions-cell"></th></tr></thead><tbody>';
        vis.forEach(r=>{
          h+='<tr>';
          meta.cols.forEach(col=>{
            let v=r[col.f];
            const cls=[];
            if(col.align) cls.push('num');
            if(col.nowrap) cls.push('nowrap');
            if(col.wide) cls.push('wide');
            if(v==null||v===''){ h+='<td class="muted'+(cls.length?' '+cls.join(' '):'')+'">—</td>'; return; }
            if(col.photo){ const u=safeUrl(v); h+='<td class="nowrap">'+(u?'<a href="'+esc(u)+'" target="_blank" rel="noopener"><img src="'+esc(u)+'" loading="lazy" style="width:44px;height:44px;object-fit:cover;border-radius:8px;border:1px solid var(--line);vertical-align:middle" alt="фото"></a>':'<span style="color:var(--muted)">—</span>')+'</td>'; return; }
            if(col.ref){ h+='<td class="nowrap">'+esc(nameOf(col.ref,v))+'</td>'; return; }
            if(col.date){ h+='<td class="nowrap">'+esc(fmtDate(v))+'</td>'; return; }
            if(col.num&&col.qty){ h+='<td class="num"><button class="btn-mini" style="min-width:22px;height:24px;margin:0 4px;border-radius:6px;border:1px solid var(--line);background:rgba(255,255,255,.05);color:var(--text);cursor:pointer;font-size:14px;line-height:1" onclick="event.stopPropagation();changeQty('+r.id+',-1)" title="Уменьшить">−</button><b>'+esc(v)+'</b>'+(r.unit?' <span style="color:var(--faint)">'+esc(r.unit)+'</span>':'')+(v==1?' <span class="cat" title="Осталась одна штука">❗ Последний</span>':'')+'<button class="btn-mini" style="min-width:22px;height:24px;margin:0 4px;border-radius:6px;border:1px solid var(--line);background:rgba(255,255,255,.05);color:var(--text);cursor:pointer;font-size:14px;line-height:1" onclick="event.stopPropagation();changeQty('+r.id+',1)" title="Увеличить">+</button></td>'; return; }
            if(col.pin){ h+='<td class="num">'+(v?'<span title="Закреплённая вещь">⭐</span>':'<span style="color:var(--faint)">·</span>')+'</td>'; return; }
            if(col.chip&&v){ h+='<td><span class="cat">'+esc(v)+'</span></td>'; return; }
            h+='<td'+(cls.length?' class="'+cls.join(' ')+'"':'')+'>'+esc(v);
            if(col.f==='name'&&isNew(r)) h+='<span class="badge-new">новое</span>';
            h+='</td>';
          });
          h+='<td class="actions-cell">'
            +'<button class="btn btn-sm" onclick="startEdit(\''+table+'\','+r.id+')">✏️ Изменить</button>'
            +'<button class="btn btn-sm btn-danger" onclick="del(\''+table+'\','+r.id+')">Удалить</button>'
            +'</td></tr>';
        });
        h+='</tbody></table>';
        tw.innerHTML=h; c.appendChild(tw);
      }
    }
    wrap.appendChild(c);
  });
}

function startEdit(table,id){
  editing[table]=id;
  render();
  document.getElementById('tab-'+table).scrollIntoView({behavior:'smooth',block:'start'});
}

let lastSig='';

async function refresh(silent){
  if(refBusy) return; refBusy=true;
  try{
    const d=await api('list');
    // не перерисовывать, если данные не изменились (устраняет «мерцание»)
    const sig=JSON.stringify(d);
    if(silent && sig===lastSig) return;
    lastSig=sig;
    DB={places:[],shelves:[],polki:[],containers:[],items:[],...(d||{})};
    const n=t=>DB[t].length;
    const nn=t=>newCount(t);
    document.getElementById('totals').innerHTML=
      '<span class="stat">🏠 '+n('places')+'</span><span class="stat">🗄 '+n('shelves')+'</span><span class="stat">📚 '+n('polki')+'</span><span class="stat">📦 '+n('containers')+'</span><span class="stat">🧺 '+n('items')+'</span>';
    renderTabs(ACTIVE_TAB);
    if(HAS_DATA.includes(ACTIVE_TAB)) render(ACTIVE_TAB); else renderPane(ACTIVE_TAB);
    if(!silent) setStatus('Обновлено '+new Date().toLocaleTimeString('ru-RU'));
  }catch(e){ if(!silent || !document.getElementById('status').textContent) setStatus('Ошибка: '+e.message,true); }
  finally{ refBusy=false; }
}
// Единственное число таблицы для имён action API (места→place, стеллажи→shelf,
// полки→polka, контейнеры→container, вещи→item)
const SINGULAR = {places:'place', shelves:'shelf', polki:'polka', containers:'container', items:'item'};
async function saveRow(table,row){
  try{
    await api('save_'+SINGULAR[table],row);
    editing[table]=0;
    delete formDraft[table];
    await refresh();
  }catch(e){ setStatus('Ошибка сохранения: '+e.message,true); throw e; }
}
async function del(table,id){
  const rec=(DB[table]||[]).find(x=>x.id===id);
  const nm=rec?rec.name:('#'+id);
  const dep=dependents(table,id);
  const msg='Удалить «'+esc(nm)+'»?'+(dep>0?'\n\nСвязанных записей: '+dep+' — они останутся, но ссылка на удалённое будет убрана.':'');
  if(!confirm(msg)) return;
  try{ await api('delete_'+SINGULAR[table],{id:id}); if(editing[table]===id) editing[table]=0; await refresh(); }
  catch(e){ setStatus('Ошибка: '+e.message,true); }
}

// Быстрое изменение количества прямо из списка (кнопки − / +)
async function changeQty(id,delta){
  const r=(DB.items||[]).find(x=>x.id===id); if(!r) return;
  const n=(Number(r.quantity)||1);
  const q=Math.max(1,n+delta);
  if(q===n) return;
  try{
    await api('save_item',{...r, quantity:q});
    await refresh(true);
    setStatus('«'+esc(r.name)+'»: '+n+' → '+q);
  }catch(e){ setStatus('Ошибка: '+e.message,true); }
}

// Экспорт всех вещей в CSV (Excel/таблицы): BOM для корректной кодировки, ';' как разделитель.
function exportCsv(){
  const csvE=s=>String(s==null?'':s).replace(/"/g,'""');
  const nameOfT=(t,id)=>id?(DB[t].find(x=>x.id===id)?.name||''):'';
  // «Место» = цепочка: контейнер → стеллаж → место (как в приложении)
  const locOf=it=>{
    const parts=[];
    if(it.containerId){ const c=DB.containers.find(x=>x.id===it.containerId); if(c){ parts.push(c.name); if(c.shelfId) parts.push(nameOfT('shelves',c.shelfId)); if(c.placeId) parts.push(nameOfT('places',c.placeId)); return parts.join(' · '); } }
    if(it.shelfId){ parts.push(nameOfT('shelves',it.shelfId)); }
    if(it.placeId && it.shelfId) parts.push(nameOfT('places',it.placeId));
    if(it.placeId && !it.shelfId) parts.push(nameOfT('places',it.placeId));
    return parts.join(' · ') || 'Без места';
  };
  const rows=[['Название','Кол-во','Ед.','Категория','Место','Заметки']];
  DB.items.slice().sort((a,b)=>a.name.localeCompare(b.name,'ru')).forEach(it=>{
    rows.push([it.name, String(it.quantity), it.unit, it.category, locOf(it), it.notes]);
  });
  const text='\uFEFF'+rows.map(r=>r.map(csvE).join(';')).join('\r\n');
  const blob=new Blob([text],{type:'text/csv;charset=utf-8'});
  const a=document.createElement('a');
  a.href=URL.createObjectURL(blob);
  a.download='kladovka-veshi-'+new Date().toISOString().slice(0,10)+'.csv';
  document.body.appendChild(a); a.click();
  setTimeout(()=>{ URL.revokeObjectURL(a.href); a.remove(); },1000);
  setStatus('CSV скачан: '+rows.length+' строк');
}

document.getElementById('btnRefresh').onclick=refresh;
document.getElementById('btnExport').onclick=async()=>{
  try{
    const d=await api('export');
    const blob=new Blob([JSON.stringify(d,null,2)],{type:'application/json'});
    const a=document.createElement('a'); a.href=URL.createObjectURL(blob);
    a.download='kladovka-backup-'+new Date().toISOString().slice(0,10)+'.json'; a.click();
    setStatus('Бэкап скачан');
  }catch(e){ setStatus('Ошибка: '+e.message,true); }
};
document.getElementById('btnImport').onclick=()=>{
  const inp=document.createElement('input'); inp.type='file'; inp.accept='.json,application/json';
  inp.onchange=()=>{ const f=inp.files[0]; if(!f) return; const rd=new FileReader();
    rd.onload=async()=>{
      if(!confirm('Заменить ВСЕ данные на сервере данными из выбранного файла?')) return;
      try{ await api('import',{json:rd.result}); editing={places:0,shelves:0,polki:0,containers:0,items:0}; await refresh(); setStatus('Данные восстановлены из файла'); }
      catch(e){ setStatus('Ошибка: '+e.message,true); }
    };
    rd.readAsText(f);
  };
  inp.click();
};
const copyBtn=document.getElementById('btnCopyKey');
if(copyBtn) copyBtn.onclick=()=>{
  const k=document.getElementById('apiKey'); k.select();
  navigator.clipboard.writeText(k.value).then(()=>setStatus('Ключ скопирован')).catch(()=>setStatus('Не удалось скопировать'));
};

refresh();
// анимация появления — только при первой загрузке
document.documentElement.classList.add('kl-first');
setTimeout(()=>document.documentElement.classList.remove('kl-first'),1500);

// ---------- автообновление в реальном времени ----------
// Каждые 7 секунд аккуратно подтягиваем данные: не мешаем, пока пользователь
// редактирует запись или печатает в поле.
function formFocused(){
  const t=document.activeElement;
  return t && (t.tagName==='INPUT'||t.tagName==='TEXTAREA'||t.tagName==='SELECT');
}
setInterval(()=>{
  if(document.hidden) return;
  if(HAS_DATA.some(t=>editing[t]!==0)) return;
  if(formFocused()) return;
  refresh(true);
},7000);
document.addEventListener('visibilitychange',()=>{ if(!document.hidden) refresh(true); });
</script>
<?php endif; ?>
<!-- PWA -->
<script>
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js')
      .then(reg => console.log('SW registered:', reg.scope))
      .catch(err => console.log('SW registration failed:', err));
  });
}
</script>
<!-- /PWA -->
</body>
</html>