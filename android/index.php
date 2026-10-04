<?php
// Кладовка — главная (лендинг). PHP-обёртка: знает статус входа из сессии кабинета.
//
// Файл обслуживается сервером как index.php и кладётся в корень сайта.
// Раньше в репозитории лежал landing-index.php — копия, которую никто не
// открывал: сервер отдавал index.php, и правки лендинга до прода не доходили.
// Теперь источник истины здесь.
declare(strict_types=1);

// Сжатие страницы (gzip), если клиент поддерживает — быстрее загрузка
if (function_exists('ob_gzhandler') && !ob_start('ob_gzhandler')) ob_start();

// Параметры ровно такие же, как в cabinet/index.php. Раньше тут не было
// 'secure', а в кабинете было: одна и та же сессия ставилась двумя разными
// cookie, и при выходе из аккаунта лендинг мог показать старое состояние.
session_set_cookie_params(['httponly' => true, 'samesite' => 'Lax', 'secure' => !empty($_SERVER['HTTPS'])]);
session_start();
if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}
$cabAuthed = !empty($_SESSION['kl_auth']);
$cabUser = (string)($_SESSION['kl_auth']['username'] ?? '');

// Актуальные сборки на сервере, чтобы кнопки «Скачать» не устаревали.
// Основная ссылка ведёт на download-handler.php — он сам выбирает файл по ОС
// посетителя. Прямые ссылки тоже держим: телефон не всегда присылает внятный
// User-Agent, и человек должен иметь возможность взять APK, ничем не
// распознаваясь.

/**
 * Самая свежая сборка нужного расширения.
 *
 * Перебор каталога регуляркой, а не glob: glob в PHP регистрозависим на всех
 * платформах, включая Windows. Шаблон Kladovka-*.apk не находит лежащий рядом
 * kladovka-v1.2.apk — кнопка просто исчезает, и непонятно почему.
 *
 * Версия выбирается из имени как (major, minor), а не по дате файла: перезалитый
 * старый APK не должен снова стать «актуальным». Правило обязано совпадать с
 * kladovkaLatest() из download-handler.php — иначе прямая ссылка и кнопка
 * «Скачать приложение» разойдутся версиями.
 *
 * Каталоги — только под публичной частью (здесь и download/): отсюда строится
 * ссылка, а ссылка на файл снаружи DOCUMENT_ROOT всё равно не откроется.
 * В rel кладётся путь вместе с подкаталогом: сборка может лежать в download/,
 * и голое имя в ссылке даст 404.
 */
function kladovkaNewestByExt(string $ext): array
{
    $best = null;
    foreach ([__DIR__, __DIR__ . '/download'] as $dir) {
        // Подкаталога download/ может не быть: без проверки scandir() кидает
        // Warning прямо в страницу.
        if (!is_dir($dir)) {
            continue;
        }
        $entries = scandir($dir);
        if ($entries === false) {
            continue;
        }
        $prefix = $dir === __DIR__ ? '' : 'download/';
        foreach ($entries as $entry) {
            if ($entry === '' || $entry[0] === '.') {
                continue;
            }
            $m = [];
            if (!preg_match('/^kladovka[-_ ]v(\d+)\.(\d+)\.([A-Za-z0-9]+)$/i', $entry, $m)) {
                continue;
            }
            if (strtolower($m[3]) !== $ext || !is_file($dir . '/' . $entry)) {
                continue;
            }
            $major = (int)$m[1];
            $minor = (int)($m[2] ?? 0);
            if ($best === null
                || $major > $best['major']
                || ($major === $best['major'] && $minor > $best['minor'])) {
                $best = ['rel' => $prefix . $entry, 'major' => $major, 'minor' => $minor];
            }
        }
    }
    return $best ?? ['rel' => '', 'major' => 0, 'minor' => 0];
}

$apkNewest = kladovkaNewestByExt('apk');
$apkLatest = $apkNewest['rel'];
$apkVersion = $apkLatest !== '' ? $apkNewest['major'] . '.' . $apkNewest['minor'] : '';

// Предупреждение о смене подписи.
//
// Раньше оно показывалось, только если в каталоге лежал APK старее свежего:
// «старые файлы уберут с сервера — и предупреждение исчезнет само». Это была
// ошибка рассуждения, причём с самой дорогой стороной. APK на сервере и версия,
// которая стоит у человека на телефоне, — разные вещи: 1.46 убрали с сервера по
// решению владельца, и предупреждение пропало ровно у тех, кому оно нужно.
// Человек скачивает 1.50, Android отказывает с «подписи не совпадают», и что
// делать дальше — не сказано нигде.
//
// Поэтому предупреждение постоянное. Убрать его можно будет только когда
// найдётся ключ от 1.46 и версии снова станут ставиться поверх старых.
const SIGNATURE_CHANGED_FROM = '1.47';

// Настольная сборка необязательна: на сервере её может не быть. Тогда
// download-handler.php отдаст посетителю APK и честно сообщит об этом
// заголовком X-Kladovka-Served.
$exeNewest = kladovkaNewestByExt('exe');
$exeLatest = $exeNewest['rel'];
$exeVersion = $exeLatest !== '' ? $exeNewest['major'] . '.' . $exeNewest['minor'] : '';
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Кладовка — складской учёт на телефоне и в веб-кабинете. Места, стеллажи, контейнеры, вещи, фото и синхронизация на вашем собственном сервере. Android APK, без облаков и подписок.">
<title>Кладовка — Складской учёт на телефоне</title>
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="preconnect" href="https://fonts.googleapis.com" crossorigin>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&display=swap" rel="stylesheet">
<style>
/* ===== CORE ===== */
:root {
  --primary: #00f0ff; --accent: #a855f7; --bg: #06090f;
  --card-bg: rgba(255,255,255,.04); --ink: #e0e6ed; --muted: #5a6a7a;
  --ok: #22c55e; --amber: #fbbf24;
  --r-xl: 24px; --r-lg: 16px; --r-md: 10px;
}
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { scroll-behavior: smooth; -webkit-text-size-adjust: 100%; }
body {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  background: var(--bg); color: var(--ink); font-size: 16px; line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  overflow-x: hidden;
}
a { color: var(--primary); text-decoration: none; }
a:hover { text-decoration: underline; }
img { max-width: 100%; height: auto; }

/* ===== BACKGROUND — оптимизировано: мягкие градиенты вместо blur,
       анимация только opacity (GPU-композит), без пересчёта градиентов ===== */
.bg-fx {
  position: fixed; inset: 0; z-index: -1;
  overflow: hidden; pointer-events: none;
  background:
    radial-gradient(ellipse 50% 50% at 15% 15%, rgba(0,240,255,.12), transparent 70%),
    radial-gradient(ellipse 40% 40% at 85% 35%, rgba(168,85,247,.10), transparent 65%),
    radial-gradient(ellipse 35% 35% at 50% 85%, rgba(34,197,94,.06), transparent 60%);
  animation: bgPulse 14s ease-in-out infinite alternate;
}
@keyframes bgPulse {
  from { opacity: .75; }
  to { opacity: 1; }
}
/* Лёгкая сетка — без perspective, без анимации на GPU */
.grid-overlay {
  position: fixed; inset: 0; pointer-events: none; z-index: -1;
  background:
    linear-gradient(rgba(0,240,255,.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,240,255,.03) 1px, transparent 1px);
  background-size: 72px 72px;
  opacity: .5;
}

/* ===== REVEAL (IntersectionObserver) — без will-change:
   не держим постоянные слои GPU для всех секций, анимации и так на transform/opacity ===== */
.reveal { opacity: 0; transform: translateY(24px); transition: opacity .6s ease, transform .6s ease; }
.reveal.visible { opacity: 1; transform: none; }
@media (prefers-reduced-motion: reduce) {
  .reveal { opacity: 1; transform: none; transition: none; }
}

/* ===== TOPBAR ===== */
.topbar {
  position: fixed; top: 0; left: 0; right: 0; z-index: 90;
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px clamp(16px, 3vw, 28px);
  background: rgba(6,9,15,.88);
  border-bottom: 1px solid rgba(0,240,255,.1);
  /* Нет backdrop-filter — спасает FPS */
}
.topbar .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 1.05rem; color: var(--ink); }
.topbar .brand .logo { font-size: 20px; }
.topbar .brand:hover { text-decoration: none; }
.topbar nav { display: flex; align-items: center; gap: 20px; }
.topbar nav a { color: var(--muted); font-size: .88rem; font-weight: 600; transition: color .15s; }
.topbar nav a:hover { color: var(--primary); text-decoration: none; }
.topbar .actions { display: flex; align-items: center; gap: 10px; }
.btn-sm { padding: 7px 14px; font-size: .82rem; border-radius: 10px; }
.btn-outline { background: transparent; color: var(--primary); border: 1px solid rgba(0,240,255,.3); }
.btn-outline:hover { background: rgba(0,240,255,.1); text-decoration: none; }
.topbar .user-tag { color: var(--muted); font-size: .82rem; white-space: nowrap; }

/* ===== BUTTONS ===== */
.btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 12px 22px; border-radius: 12px; font-size: .95rem; font-weight: 700;
  transition: transform .2s, box-shadow .2s, background .2s; cursor: pointer; border: none;
  /* Нет backdrop-filter — быстрее FPS */
}
.btn-primary {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: #06090f; box-shadow: 0 4px 20px rgba(0,240,255,.2);
  position: relative;
}
.btn-primary::after { /* мягкое пульсирующее кольцо */
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  border: 2px solid rgba(0,240,255,.6); pointer-events: none;
  animation: pulseRing 2.6s ease-out infinite;
}
.btn-primary:hover { transform: translateY(-2px); box-shadow: 0 6px 28px rgba(0,240,255,.35); text-decoration: none; }
.btn-ghost {
  background: rgba(0,240,255,.06); color: var(--primary);
  border: 1px solid rgba(0,240,255,.25);
}
.btn-ghost:hover { background: rgba(0,240,255,.14); transform: translateY(-2px); text-decoration: none; }

/* ===== АНИМАЦИИ (только transform/opacity — плавно и быстро) ===== */
@keyframes appearUp { from { opacity: 0; transform: translateY(24px); } to { opacity: 1; transform: none; } }
@keyframes floaty { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-14px); } }
@keyframes pulseRing { 0% { opacity: 0; transform: scale(1); } 40% { opacity: .5; } 100% { opacity: 0; transform: scale(1.35); } }
@keyframes arrowSlide { 0%,100% { transform: translateX(0); opacity: .6; } 50% { transform: translateX(5px); opacity: 1; } }
@keyframes iconBounce { 0%,100% { transform: translateY(0); } 40% { transform: translateY(-8px); } 60% { transform: translateY(-3px); } }
@keyframes swapGlow { 0%,100% { transform: scale(1); opacity: .7; } 50% { transform: scale(1.15); opacity: 1; } }
@keyframes glowPulse { 0%,100% { opacity: .3; } 50% { opacity: .9; } }

/* ===== HERO ===== */
.hero {
  position: relative; overflow: hidden;
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  padding: 100px clamp(16px, 4vw, 24px) 60px;
}
.hero-inner {
  max-width: 1100px; width: 100%;
  display: grid; grid-template-columns: 1fr 1fr; gap: 48px; align-items: center;
}
.hero-text h1 {
  font-size: clamp(2rem, 5vw, 3.4rem); font-weight: 900; line-height: 1.15;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
  margin-bottom: 18px;
  animation: appearUp .7s ease both;
}
.hero-text p { font-size: clamp(.95rem, 1.8vw, 1.15rem); color: var(--muted); max-width: 480px; margin-bottom: 28px; animation: appearUp .7s .12s ease both; }
.hero-buttons { display: flex; gap: 12px; flex-wrap: wrap; animation: appearUp .7s .24s ease both; }

/* ===== PHONE MOCKUP — упрощён, без blur/ring ===== */
.phone-mockup {
  position: relative; width: 260px; height: 520px; margin: 0 auto;
  border-radius: 32px;
  background: linear-gradient(135deg, rgba(0,240,255,.06), rgba(168,85,247,.06));
  border: 1.5px solid rgba(0,240,255,.15);
  box-shadow: 0 16px 60px rgba(0,0,0,.4), 0 0 30px rgba(0,240,255,.06);
  display: flex; align-items: center; justify-content: center;
  animation: floaty 7s ease-in-out infinite;
}
.phone-mockup::before {
  content: ''; position: absolute; top: 10px; left: 50%; transform: translateX(-50%);
  width: 70px; height: 5px; background: rgba(0,240,255,.15); border-radius: 3px;
}
.phone-screen {
  width: 232px; height: 484px; border-radius: 22px; overflow: hidden;
  background: var(--bg); border: 1px solid rgba(0,240,255,.12);
  display: flex; flex-direction: column;
}
.phone-status { padding: 8px 14px; font-size: 11px; color: var(--muted); text-align: right; }
.phone-header { padding: 6px 14px 10px; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid rgba(0,240,255,.1); }
.phone-header .logo { font-size: 16px; }
.phone-header .title { font-weight: 700; font-size: 14px; }
.phone-tabs { display: flex; border-bottom: 1px solid rgba(0,240,255,.1); }
.phone-tab { flex: 1; text-align: center; padding: 8px 0; font-size: 10px; color: var(--muted); border-bottom: 2px solid transparent; }
.phone-tab.active { color: var(--primary); border-bottom-color: var(--primary); }
.phone-list { flex: 1; padding: 10px; overflow: hidden; }
.phone-item {
  display: flex; align-items: center; gap: 8px; padding: 9px;
  border-radius: 8px; margin-bottom: 5px;
  background: rgba(0,240,255,.04); border: 1px solid rgba(0,240,255,.07);
  animation: itemIn .5s ease both;
}
.phone-item:nth-child(1) { animation-delay: .5s; }
.phone-item:nth-child(2) { animation-delay: .7s; }
.phone-item:nth-child(3) { animation-delay: .9s; }
.phone-item:nth-child(4) { animation-delay: 1.1s; }
@keyframes itemIn { from { opacity: 0; transform: translateX(14px); } to { opacity: 1; transform: none; } }
.phone-item .icon { font-size: 18px; }
.phone-item .name { font-size: 12px; font-weight: 600; }
.phone-item .sub { font-size: 10px; color: var(--muted); }
.phone-fab {
  position: absolute; bottom: 50px; right: 16px;
  width: 42px; height: 42px; border-radius: 12px;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; color: #06090f; font-weight: 900;
  box-shadow: 0 4px 16px rgba(0,240,255,.25);
}

/* ===== SECTIONS ===== */
.section { padding: clamp(60px, 10vw, 100px) clamp(16px, 4vw, 24px); max-width: 1100px; margin: 0 auto; content-visibility: auto; contain-intrinsic-size: auto 600px; }
.section-title {
  font-size: clamp(1.6rem, 3.5vw, 2.2rem); font-weight: 800; text-align: center; margin-bottom: 14px;
  background: linear-gradient(135deg, var(--ink), var(--primary));
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
}
.section-subtitle { text-align: center; color: var(--muted); max-width: 560px; margin: 0 auto 40px; font-size: 1rem; }

/* ===== FEATURES GRID ===== */
.features-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr)); gap: 20px; }
.feature-card {
  position: relative; overflow: hidden;
  padding: 24px; border-radius: var(--r-lg);
  background: var(--card-bg); border: 1px solid rgba(0,240,255,.08);
  transition: transform .2s, border-color .2s, box-shadow .2s;
}
.feature-card:hover { border-color: rgba(0,240,255,.25); box-shadow: 0 6px 24px rgba(0,240,255,.08); transform: translateY(-4px); }
.feature-icon { font-size: 28px; margin-bottom: 12px; display: inline-block; transition: transform .2s; }
.feature-card:hover .feature-icon { animation: iconBounce .5s ease; }
.feature-card h3 { font-size: 1rem; font-weight: 700; margin-bottom: 6px; }
.feature-card p { color: var(--muted); font-size: .9rem; }

/* ===== HIERARCHY ===== */
.hierarchy { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; padding: 30px 0; align-items: center; }
.h-level {
  text-align: center; padding: 18px 20px; border-radius: var(--r-md);
  background: var(--card-bg); border: 1px solid rgba(0,240,255,.08);
  min-width: 120px; transition: transform .2s;
}
.h-level:hover { transform: translateY(-3px); }
.h-level .icon { font-size: 32px; margin-bottom: 6px; }
.h-level .label { font-weight: 700; font-size: .9rem; }
.h-level .sub { font-size: .75rem; color: var(--muted); margin-top: 3px; }
.h-arrow { font-size: 20px; color: var(--primary); animation: arrowSlide 2.4s ease-in-out infinite; }

/* ===== SHARE ===== */
.share-demo { display: grid; grid-template-columns: 1fr auto 1fr; gap: 20px; align-items: center; max-width: 640px; margin: 0 auto; }
.share-user {
  padding: 22px; border-radius: var(--r-lg); text-align: center;
  background: var(--card-bg); border: 1px solid rgba(0,240,255,.08);
  transition: transform .2s;
}
.share-user:hover { transform: translateY(-3px); }
.share-user .avatar { font-size: 36px; margin-bottom: 6px; }
.share-user .name { font-weight: 700; font-size: .95rem; }
.share-user .desc { font-size: .8rem; color: var(--muted); margin-top: 3px; }
.share-arrow { font-size: 24px; color: var(--accent); animation: swapGlow 3.2s ease-in-out infinite; }

/* ===== MODAL ===== */
.modal-overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(3,6,12,.8);
  display: flex; align-items: center; justify-content: center; padding: 20px;
  opacity: 0; visibility: hidden; transition: opacity .25s, visibility .25s;
}
.modal-overlay.open { opacity: 1; visibility: visible; }
.modal {
  position: relative; width: 100%; max-width: 400px;
  background: #0d1422; border: 1px solid rgba(0,240,255,.2); border-radius: var(--r-xl);
  padding: 32px 28px 26px;
  box-shadow: 0 20px 60px rgba(0,0,0,.5);
  transform: translateY(16px) scale(.98); transition: transform .25s;
}
.modal-overlay.open .modal { transform: none; }
.modal-close {
  position: absolute; top: 12px; right: 14px;
  background: none; border: none; color: var(--muted); font-size: 20px; cursor: pointer;
  transition: color .15s;
}
.modal-close:hover { color: var(--primary); }
.modal h2 { font-size: 1.4rem; font-weight: 800; margin-bottom: 4px; }
.modal .modal-sub { color: var(--muted); font-size: .88rem; margin-bottom: 20px; }
.modal .field { margin-bottom: 12px; }
.modal .field label { display: block; font-size: .8rem; font-weight: 600; color: var(--muted); margin-bottom: 5px; }
.modal .field input {
  width: 100%; padding: 11px 13px; border-radius: var(--r-md);
  background: rgba(255,255,255,.05); border: 1px solid rgba(0,240,255,.15); color: var(--ink);
  font-size: .95rem; outline: none; transition: border-color .2s;
}
.modal .field input:focus { border-color: var(--primary); }
.modal .btn { width: 100%; justify-content: center; margin-top: 4px; }
.modal .modal-links { display: flex; justify-content: space-between; margin-top: 14px; font-size: .82rem; }
.modal .hint { color: var(--muted); font-size: .75rem; margin-top: 12px; text-align: center; }

/* ===== PRIVACY ===== */
.privacy { padding: 60px clamp(16px, 4vw, 24px); max-width: 780px; margin: 0 auto; content-visibility: auto; contain-intrinsic-size: auto 800px; }
.privacy h2 { font-size: clamp(1.4rem, 3vw, 1.7rem); font-weight: 800; margin-bottom: 28px; }
.privacy h3 { font-size: 1rem; font-weight: 700; margin: 20px 0 10px; color: var(--primary); }
.privacy p, .privacy li { color: var(--muted); line-height: 1.7; margin-bottom: 10px; font-size: .92rem; }
.privacy ul { padding-left: 18px; }
.privacy li { margin-bottom: 6px; }

/* ===== FOOTER ===== */
.footer { padding: 32px 16px; text-align: center; color: var(--muted); font-size: .82rem; border-top: 1px solid rgba(0,240,255,.06); }
.footer a { color: var(--primary); }

/* ===== SCREENSHOTS hover ===== */
.screenshots-wrap img { transition: transform .25s ease, box-shadow .25s ease; }
.screenshots-wrap img:hover { transform: translateY(-6px); box-shadow: 0 12px 34px rgba(0,240,255,.16); }

/* ===== TOPBAR brand glow ===== */
.topbar .brand .logo { display: inline-block; animation: glowPulse 4s ease-in-out infinite; }

/* ===== SCROLLBAR ===== */
::-webkit-scrollbar { width: 8px; }
::-webkit-scrollbar-track { background: var(--bg); }
::-webkit-scrollbar-thumb { background: rgba(0,240,255,.15); border-radius: 4px; }

/* ===== RESPONSIVE: TABLET (≤ 900px) ===== */
@media (max-width: 900px) {
  .hero-inner { grid-template-columns: 1fr; text-align: center; }
  .hero-text p { margin-left: auto; margin-right: auto; }
  .hero-buttons { justify-content: center; }
  .topbar nav { gap: 14px; }
  .topbar nav a { font-size: .82rem; }
  .phone-mockup { width: 220px; height: 440px; margin-top: 24px; }
  .phone-screen { width: 192px; height: 404px; }
  .hierarchy { gap: 10px; }
  .h-arrow { display: none; }
}

/* ===== RESPONSIVE: MOBILE (≤ 600px) ===== */
@media (max-width: 600px) {
  .topbar nav { display: none; }
  .topbar { padding: 10px 14px; }
  .topbar .user-tag { display: none; }
  .hero { min-height: auto; padding: 90px 16px 40px; }
  .phone-mockup { width: 200px; height: 400px; }
  .phone-screen { width: 172px; height: 364px; }
  .phone-tabs { overflow-x: auto; }
  .phone-tab { font-size: 9px; white-space: nowrap; padding: 6px 0; }
  .features-grid { grid-template-columns: 1fr; }
  .share-demo { grid-template-columns: 1fr; gap: 12px; }
  .share-arrow { transform: rotate(90deg); }
  .hierarchy { flex-direction: column; align-items: center; }
  .section { padding: 48px 16px; }
  .btn { padding: 11px 18px; font-size: .9rem; }
  /* Скриншоты: горизонтальный скролл на мобильном */
  .screenshots-wrap { flex-wrap: nowrap !important; overflow-x: auto; -webkit-overflow-scrolling: touch; scroll-snap-type: x mandatory; padding-bottom: 8px; }
  .screenshots-wrap > div { flex-shrink: 0; scroll-snap-align: center; }
  .screenshots-wrap img { width: 160px !important; height: 356px !important; }
}

/* ===== SMALL SCREEN (≤ 380px) ===== */
@media (max-width: 380px) {
  .hero-text h1 { font-size: 1.7rem; }
  .hero-buttons { flex-direction: column; align-items: center; }
  .btn { width: 100%; justify-content: center; }
  .modal { padding: 24px 18px 20px; }
}

/* ===== REDUCED MOTION =====
   Анимации остаются, но отключаются только самые активные (парение, свечение).
   Появления (appearUp / reveal) работают всегда — они короткие и экономные. */
@media (prefers-reduced-motion: reduce) {
  .phone-mockup { animation: none; }
  .btn-primary::after { animation: none; }
  .topbar .brand .logo { animation: none; }
  .h-arrow, .share-arrow, .feature-card:hover .feature-icon { animation: none; }
  .bg-fx { animation: none; }
}

/* ===== Skip-link ===== */
.skip-link {
  position: absolute;
  left: -9999px;
  top: -9999px;
  width: 1px;
  height: 1px;
  overflow: hidden;
  z-index: 1000;
  padding: 8px 16px;
  background: var(--primary);
  color: #06090f;
  font-weight: 700;
  text-decoration: none;
  border-radius: 4px;
}
.skip-link:focus {
  left: 8px;
  top: 8px;
  width: auto;
  height: auto;
  overflow: visible;
}
</style>
</head>
<body>
<!-- Skip-link для accessibility -->
<a class="skip-link" href="#main-content">Перейти к содержимому</a>

<!-- ===== ШАПКА ===== -->
<header class="topbar">
  <a class="brand" href="/"><span class="logo">📦</span>Кладовка</a>
  <nav aria-label="Навигация по сайту">
    <a href="#features">Возможности</a>
    <a href="#install">Установка</a>
    <a href="#screenshots">Скриншоты</a>
    <a href="#privacy">Политика</a>
  </nav>
  <div class="actions">
    <?php if ($cabAuthed): ?>
      <span class="user-tag">👤 <?= htmlspecialchars($cabUser !== '' ? $cabUser : 'Администратор') ?></span>
      <a class="btn btn-primary btn-sm" href="/cabinet/">Кабинет</a>
      <a class="btn btn-outline btn-sm" href="/cabinet/?logout=1">Выйти</a>
    <?php else: ?>
      <button class="btn btn-primary btn-sm" onclick="openModal()">Войти</button>
    <?php endif; ?>
  </div>
</header>

<!-- ===== Фон ===== -->
<div class="bg-fx" aria-hidden="true"></div>
<div class="grid-overlay" aria-hidden="true"></div>

<!-- ===== HERO ===== -->
<section class="hero" id="main-content">
  <div class="hero-inner">
    <div class="hero-text">
      <h1>Кладовка</h1>
      <p>Удобный складской учёт на телефоне. Располагайте вещи по местам, стеллажам и контейнерам. Ведите совместный учёт с семьёй или коллегами.</p>
      <div class="hero-buttons">
        <a href="/download-handler.php" class="btn btn-primary">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          Скачать приложение
        </a>
        <?php if ($cabAuthed): ?>
        <a href="/cabinet/" class="btn btn-ghost">Открыть кабинет</a>
        <?php else: ?>
        <button class="btn btn-ghost" onclick="openModal()">Личный кабинет</button>
        <?php endif; ?>
      </div>
      <p style="margin-top:14px; font-size:.85rem; color:var(--muted);">
        <?php
        $direct = [];
        if ($apkLatest !== '') {
            $direct[] = '<a href="/' . htmlspecialchars($apkLatest, ENT_QUOTES, 'UTF-8') . '">APK для Android'
                . ($apkVersion !== '' ? ' v' . htmlspecialchars($apkVersion, ENT_QUOTES, 'UTF-8') : '') . '</a>';
        }
        if ($exeLatest !== '') {
            $direct[] = '<a href="/' . htmlspecialchars($exeLatest, ENT_QUOTES, 'UTF-8') . '">EXE для Windows'
                . ($exeVersion !== '' ? ' v' . htmlspecialchars($exeVersion, ENT_QUOTES, 'UTF-8') : '') . '</a>';
        }
        echo $direct
            ? 'Ссылка выше сама подберёт файл под вашу систему. Можно и напрямую: ' . implode(' · ', $direct)
            : 'Сборка скоро появится.';
        ?>
      </p>
    </div>
    <div class="phone-mockup">
      <div class="phone-screen">
        <div class="phone-status">09:41</div>
        <div class="phone-header">
          <span class="logo">📦</span>
          <span class="title">Кладовка</span>
        </div>
        <div class="phone-tabs">
          <div class="phone-tab">📍 Места</div>
          <div class="phone-tab">🗄 Стеллажи</div>
          <div class="phone-tab">📚 Полки</div>
          <div class="phone-tab active">📦 Контейнеры</div>
          <div class="phone-tab">🏷 Вещи</div>
        </div>
        <div class="phone-list">
          <div class="phone-item"><span class="icon">📦</span><div><div class="name">К01</div><div class="sub">Полка П1 · Стеллаж С1 · Кладовая</div></div></div>
          <div class="phone-item"><span class="icon">📦</span><div><div class="name">К02</div><div class="sub">Полка П2 · Стеллаж С2 · Балкон</div></div></div>
          <div class="phone-item"><span class="icon">📦</span><div><div class="name">К03</div><div class="sub">Полка П1 · Стеллаж С1 · Кладовая</div></div></div>
          <div class="phone-item"><span class="icon">📦</span><div><div class="name">К04</div><div class="sub">Полка П3 · Стеллаж С3 · Гараж</div></div></div>
        </div>
        <div class="phone-fab">+</div>
      </div>
    </div>
  </div>
</section>

<!-- ===== FEATURES ===== -->
<section class="section reveal" id="features">
  <h2 class="section-title">Всё под контролем</h2>
  <p class="section-subtitle">Простая структура, понятная любому. Откройте приложение — и сразу видите, где что лежит.</p>
  <div class="features-grid">
    <div class="feature-card"><div class="feature-icon">📍</div><h3>Места</h3><p>Отмечайте территории: кладовая, балкон, гараж, подвал. Добавляйте координаты для навигации.</p></div>
    <div class="feature-card"><div class="feature-icon">🗄</div><h3>Стеллажи</h3><p>Нумеруйте стеллажи внутри мест. Размещайте на них полки и контейнеры.</p></div>
    <div class="feature-card"><div class="feature-icon">📚</div><h3>Полки</h3><p>Полки внутри стеллажей или отдельно. Каждая полка со своими вещами и заметками.</p></div>
    <div class="feature-card"><div class="feature-icon">📦</div><h3>Контейнеры</h3><p>Коробки, ящики, папки — всё, что помогает группировать вещи на полках и стеллажах.</p></div>
    <div class="feature-card"><div class="feature-icon">🏷</div><h3>Вещи</h3><p>Каждая вещь с названием, количеством, категорией и заметкой. Фото прямо с камеры.</p></div>
    <div class="feature-card"><div class="feature-icon">📷</div><h3>Фото</h3><p>Фотографируйте вещи или загружайте из галереи. Всегда видите, что внутри коробки.</p></div>
    <div class="feature-card"><div class="feature-icon">☁️</div><h3>Синхронизация</h3><p>Отправляйте данные на свой сервер и загружайте с него. Полная замена — вы контролируете.</p></div>
    <div class="feature-card"><div class="feature-icon">👥</div><h3>Совместный учёт</h3><p>Откройте доступ другому пользователю — и ведите учёт вместе. Каждый видит общие данные.</p></div>
    <div class="feature-card"><div class="feature-icon">🔒</div><h3>Приватность</h3><p>Данные хранятся на вашем сервере. Никаких облаков, никаких третьих лиц. Полный контроль.</p></div>
    <div class="feature-card"><div class="feature-icon">🔍</div><h3>Поиск</h3><p>Находите любую вещь за секунды: поиск работает по названию, категории и месту — и в приложении, и в кабинете.</p></div>
    <div class="feature-card"><div class="feature-icon">📊</div><h3>Статистика</h3><p>Сколько вещей и где: категории, места и «последние штуки» под контролем в один взгляд.</p></div>
    <div class="feature-card"><div class="feature-icon">💾</div><h3>Бэкап и CSV</h3><p>Резервная копия в один клик, выгрузка вещей в CSV для Excel. Данные переживут смену телефона.</p></div>
  </div>
</section>

<!-- ===== HIERARCHY ===== -->
<section class="section reveal">
  <h2 class="section-title">Простая структура</h2>
  <p class="section-subtitle">Пять уровней иерархии — от места до вещи. Ничего лишнего.</p>
  <div class="hierarchy">
    <div class="h-level"><div class="icon">📍</div><div class="label">Место</div><div class="sub">Кладовая, Балкон</div></div>
    <div class="h-arrow">→</div>
    <div class="h-level"><div class="icon">🗄</div><div class="label">Стеллаж</div><div class="sub">С1, С2, С3</div></div>
    <div class="h-arrow">→</div>
    <div class="h-level"><div class="icon">📚</div><div class="label">Полка</div><div class="sub">П1, П2, П3</div></div>
    <div class="h-arrow">→</div>
    <div class="h-level"><div class="icon">📦</div><div class="label">Контейнер</div><div class="sub">К01, К02</div></div>
    <div class="h-arrow">→</div>
    <div class="h-level"><div class="icon">🏷</div><div class="label">Вещь</div><div class="sub">Болты, Книги</div></div>
  </div>
</section>

<!-- ===== SHARING ===== -->
<section class="section reveal">
  <h2 class="section-title">Вместе удобнее</h2>
  <p class="section-subtitle">Совместный учёт: расшаривайте свой склад другим пользователям и ведите учёт вместе.</p>
  <div class="share-demo">
    <div class="share-user"><div class="avatar">👨</div><div class="name">Вы</div><div class="desc">Создаёте записи, расшариваете доступ</div></div>
    <div class="share-arrow">⇄</div>
    <div class="share-user"><div class="avatar">👩</div><div class="name">Партнёр</div><div class="desc">Видит ваш склад, ведёт учёт вместе</div></div>
  </div>
  <p style="text-align:center; color:var(--muted); margin-top:20px; font-size:.85rem;">Владелец сервера видит и управляет всеми данными.</p>
</section>

<!-- ===== SELF-HOSTED ===== -->
<section class="section reveal">
  <h2 class="section-title">Свой сервер — свой контроль</h2>
  <p class="section-subtitle">Кладовка работает с вашим собственным сервером. Никаких подписок, никаких ограничений.</p>
  <div class="features-grid" style="max-width:800px; margin:0 auto;">
    <div class="feature-card"><div class="feature-icon">🖥</div><h3>Простая установка</h3><p>PHP + SQLite — минимум зависимостей. Один файл <code>api.php</code> и база данных.</p></div>
    <div class="feature-card"><div class="feature-icon">🛡</div><h3>Безопасность</h3><p>HMAC-токены, подтверждение почты, ограничение регистраций. Ваш сервер — ваши правила.</p></div>
    <div class="feature-card"><div class="feature-icon">📱</div><h3>Телефон + веб</h3><p>Приложение для Android и веб-интерфейс для ПК. Одна база — доступ отовсюду.</p></div>
  </div>
</section>

<!-- ===== INSTALL ===== -->
<section class="section reveal" id="install">
  <h2 class="section-title">Быстрый старт</h2>
  <p class="section-subtitle">Три шага до полноценного складского учёта.</p>
  <div class="features-grid" style="max-width:800px; margin:0 auto;">
    <div class="feature-card" style="text-align:center;"><div class="feature-icon" style="font-size:42px; color:var(--primary); font-weight:900;">1</div><h3>Установите сервер</h3><p>Скачайте <code>api.php</code>, положите на хостинг с PHP 8+ и SQLite. Откройте в браузере — приложение готово.</p></div>
    <div class="feature-card" style="text-align:center;"><div class="feature-icon" style="font-size:42px; color:var(--accent); font-weight:900;">2</div><h3>Установите приложение</h3><p>Скачайте APK и поставьте на Android, либо EXE на компьютер. Введите логин и пароль — войдите.</p></div>
    <div class="feature-card" style="text-align:center;"><div class="feature-icon" style="font-size:42px; color:var(--ok); font-weight:900;">3</div><h3>Начните учёт</h3><p>Добавляйте места, стеллажи, полки, контейнеры и вещи. Фотографируйте, синхронизируйте, делитесь.</p></div>
  </div>
  <p style="text-align:center; margin-top:24px;">
    <a href="/download-handler.php" class="btn btn-primary btn-sm">Скачать под мою систему</a>
  </p>

  <!-- Предупреждение о смене подписи. Показывается всегда, а не «пока на
       сервере лежит старый APK»: версия, которая стоит у человека на телефоне,
       с наличием файлов на сервере ничего общего не имеет. -->
  <div style="max-width:760px; margin:24px auto 0; padding:18px 20px; border-radius:14px;
              border:1px solid rgba(251,191,36,.35); background:rgba(251,191,36,.07);">
    <div style="color:var(--amber); font-weight:700; margin-bottom:8px;">
      ⚠️ Если у вас стоит 1.46 или более старая версия
    </div>
    <p style="margin:0 0 10px; color:var(--muted); font-size:.92rem; line-height:1.55;">
      Начиная с версии 1.47 приложение подписано новым ключом: прежний ключ утрачен
      и восстановить его нельзя. Android не поставит новую версию поверх старой —
      подписи разные, и система откажет установку. Это не ошибка телефона и не
      проблема скачанного файла.
    </p>
    <p style="margin:0 0 10px; color:var(--muted); font-size:.92rem; line-height:1.55;">
      Если Android ответит «Приложение не установлено» или упомянет «подписи не
      совпадают» — это оно самое.
    </p>
    <p style="margin:0 0 8px; color:var(--muted); font-size:.92rem; line-height:1.55;">
      Данные лежат <strong style="color:var(--text);">на сервере</strong>, если вы
      пользовались синхронизацией, — тогда перенос выглядит так:
    </p>
    <ol style="margin:0 0 10px; padding-left:20px; color:var(--muted); font-size:.92rem; line-height:1.7;">
      <li>Удалите старую версию приложения.</li>
      <li>Поставьте новую.</li>
      <li>Войдите на сервер — данные подтянутся сами, либо «Синхронизация с сервером» → «Загрузить с сервера».</li>
    </ol>
    <p style="margin:0; color:var(--muted); font-size:.92rem; line-height:1.55;">
      Если синхронизации не было, данные остались только на телефоне, и тогда
      <strong style="color:var(--text);">сначала выгрузите их</strong> («Экспорт (бэкап)»),
      иначе они пропадут при удалении приложения.
    </p>
  </div>
  <p style="text-align:center; color:var(--muted); margin-top:14px; font-size:.9rem;">
    Работаете за компьютером? <?php if ($cabAuthed): ?>
    <a href="/cabinet/" class="btn btn-ghost btn-sm" style="margin-left:4px;">Открыть кабинет</a>
    <?php else: ?>
    <button class="btn btn-ghost btn-sm" onclick="openModal()" style="margin-left:4px;">Веб-кабинет</button>
    <?php endif; ?>
  </p>
</section>

<!-- ===== SCREENSHOTS ===== -->
<section class="section reveal" id="screenshots">
  <h2 class="section-title">Скриншоты</h2>
  <p class="section-subtitle">Минималистичный интерфейс, понятная навигация, минимум лишнего.</p>
  <div class="screenshots-wrap" style="display:flex; gap:20px; justify-content:center; flex-wrap:wrap; padding:16px 0;">
    <div style="text-align:center;">
      <!-- Скриншоты лежат в /screens/, а не рядом с исходниками Android в /app/:
     тот закрыт правами 700 и отдаёт 403, из-за чего картинки на сайте были битыми. -->
    <img src="/screens/01-main.png" alt="Главный экран" loading="lazy" width="200" height="444" style="width:200px; height:444px; border-radius:18px; border:1px solid rgba(0,240,255,.12); object-fit:cover; object-position:top; ">
      <p style="color:var(--muted); font-size:12px; margin:8px 0 0;">Главный экран</p>
    </div>
    <div style="text-align:center;">
      <img src="/screens/03-item-edit.png" alt="Новая вещь" loading="lazy" width="200" height="444" style="width:200px; height:444px; border-radius:18px; border:1px solid rgba(0,240,255,.12); object-fit:cover; object-position:top; ">
      <p style="color:var(--muted); font-size:12px; margin:8px 0 0;">Новая вещь</p>
    </div>
    <div style="text-align:center;">
      <img src="/screens/04-place-edit.png" alt="Новое место" loading="lazy" width="200" height="444" style="width:200px; height:444px; border-radius:18px; border:1px solid rgba(0,240,255,.12); object-fit:cover; object-position:top; ">
      <p style="color:var(--muted); font-size:12px; margin:8px 0 0;">Новое место</p>
    </div>
    <div style="text-align:center;">
      <img src="/screens/02-sync.png" alt="Синхронизация" loading="lazy" width="200" height="444" style="width:200px; height:444px; border-radius:18px; border:1px solid rgba(0,240,255,.12); object-fit:cover; object-position:top; ">
      <p style="color:var(--muted); font-size:12px; margin:8px 0 0;">Синхронизация</p>
    </div>
  </div>
  <p style="text-align:center; color:var(--muted); font-size:12px; margin-top:4px;">Скриншоты <?= $apkVersion !== '' ? ('v' . $apkVersion) : '' ?> · <a href="/app/icon-512.png" style="color:var(--primary)">icon-512.png</a></p>
</section>

<!-- ===== PRIVACY ===== -->
<section class="privacy reveal" id="privacy">
  <h2>🔒 Политика конфиденциальности</h2>
  <p><em>Последнее обновление: 13 сентября 2026 г.</em></p>
  <h3>1. Какие данные собираются</h3>
  <p><strong>Кладовка</strong> собирает и хранит <strong>только те данные, которые вы вводите самостоятельно</strong>:</p>
  <ul>
    <li>Названия мест, стеллажей, полок, контейнеров и вещей</li>
    <li>Количество и единицы измерения</li>
    <li>Заметки и категории</li>
    <li>Координаты мест (опционально)</li>
    <li>Фото вещей (хранятся на устройстве и на вашем сервере)</li>
    <li>Адрес электронной почты (для входа в аккаунт)</li>
  </ul>
  <h3>2. Где хранятся данные</h3>
  <p>Все данные хранятся <strong>исключительно на вашем собственном сервере</strong> (SQLite). Приложение не отправляет данные на сторонние серверы, не использует аналитику, не передаёт информацию третьим лицам.</p>
  <h3>3. Синхронизация</h3>
  <p>Данные передаются между вашим телефоном и вашим сервером по HTTPS. Не проходят через промежуточные серверы.</p>
  <h3>4. Фото</h3>
  <p>Фотографии сохраняются на устройстве и загружаются на ваш сервер при синхронизации. Не передаются в сторонние сервисы.</p>
  <h3>5. Аутентификация</h3>
  <p>Для входа используется логин и пароль. Пароли хранятся в необратимо захешированном виде (bcrypt).</p>
  <h3>6. Безопасность</h3>
  <ul>
    <li>HMAC-токены для всех запросов</li>
    <li>Ограничение регистраций (3 в день на IP)</li>
    <li>Математическая CAPTCHA</li>
    <li>Сессии истекают при выходе</li>
  </ul>
  <h3>7. Права пользователя</h3>
  <ul>
    <li>Удалить аккаунт и все данные в любой момент</li>
    <li>Экспортировать данные в JSON</li>
    <li>Отозвать доступ совместного пользования</li>
  </ul>
  <h3>8. Контакты</h3>
  <p><a href="mailto:support@dr6ter.ru">support@dr6ter.ru</a></p>
</section>

<!-- ===== FOOTER ===== -->
<footer class="footer">
  <p>📦 Кладовка — складской учёт на телефоне</p>
  <p style="margin-top:6px;">Сделано на <a href="https://kladovka.dr6ter.ru">kladovka.dr6ter.ru</a></p>
</footer>

<!-- ===== MODAL ===== -->
<div class="modal-overlay" id="loginModal" role="dialog" aria-modal="true" aria-labelledby="loginTitle">
  <div class="modal">
    <button class="modal-close" onclick="closeModal()" aria-label="Закрыть">✕</button>
    <h2 id="loginTitle">Вход в кабинет</h2>
    <p class="modal-sub">Просмотр и редактирование базы с ПК.</p>
    <form method="post" action="/cabinet/" autocomplete="off">
      <input type="hidden" name="_csrf" value="<?= htmlspecialchars($_SESSION['csrf_token'] ?? '') ?>">
      <div class="field">
        <label for="cab-username">Имя пользователя</label>
        <input type="text" id="cab-username" name="username" placeholder="Имя пользователя" autocomplete="username">
      </div>
      <div class="field">
        <label for="cab-password">Пароль</label>
        <input type="password" id="cab-password" name="password" required placeholder="••••••••" autocomplete="current-password">
      </div>
      <button type="submit" class="btn btn-primary">Войти</button>
    </form>
    <div class="modal-links">
      <a href="/cabinet/?view=register">Создать аккаунт</a>
    </div>
    <p class="hint">Данные синхронизируются с приложением.</p>
  </div>
</div>

<script>
/* Модалка — минимальный JS */
var modal = document.getElementById('loginModal');
function openModal() {
  modal.classList.add('open');
  document.getElementById('cab-password').focus();
  document.body.style.overflow = 'hidden';
}
function closeModal() {
  modal.classList.remove('open');
  document.body.style.overflow = '';
}
modal.addEventListener('click', function(e) { if (e.target === this) closeModal(); });
document.addEventListener('keydown', function(e) { if (e.key === 'Escape') closeModal(); });

/* Reveal — IntersectionObserver */
(function() {
  var els = document.querySelectorAll('.reveal');
  if (!('IntersectionObserver' in window)) { for (var i = 0; i < els.length; i++) els[i].classList.add('visible'); return; }
  var io = new IntersectionObserver(function(entries) {
    for (var i = 0; i < entries.length; i++) {
      if (entries[i].isIntersecting) { entries[i].target.classList.add('visible'); io.unobserve(entries[i].target); }
    }
  }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });
  for (var j = 0; j < els.length; j++) io.observe(els[j]);
})();
</script>
</body>
</html>
