#!/bin/bash
R=/www/wwwroot/kladovka.dr6ter.ru
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
php -l /tmp/api.php && php -l /tmp/index.php
cp /tmp/api.php $R/api.php && cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php
echo "== create test user + confirm + auto-login =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
$db->exec("DELETE FROM users WHERE username=\"autologin_test\"");
$db->prepare("INSERT INTO users (username,email,password_hash,email_verified,confirm_token,confirm_expires,created_at,ip) VALUES (?,?,?,0,?,?,?,?)")
   ->execute(["autologin_test","al@dr6ter.ru",password_hash("secret123",PASSWORD_DEFAULT),"tok123",time()+86400,1788983000000,"127.0.0.1"]);
echo "user ready\n";
'
echo "== клик по ссылке (с кукой сессии как в браузере) =="
curl "${RES[@]}" -s -c /tmp/auto.txt -o /dev/null -w 'confirm: [%{http_code}] -> %{redirect_url}\n' "https://kladovka.dr6ter.ru/api.php?action=confirm&u=autologin_test&t=tok123"
echo "== переход на главную с той же кукой -> авто-вход =="
curl "${RES[@]}" -s -b /tmp/auto.txt -c /tmp/auto.txt https://kladovka.dr6ter.ru/ -o /tmp/auto.html -w '[%{http_code}] %{size_download}b\n'
grep -o 'Синхронизировано\|Выйти\|Кладовка' /tmp/auto.html | sort | uniq -c | head
echo "== API с токеном из сессии =="
echo "(implicit: главная сама вызывает api с токеном; проверяем что страница = приложение, не логин)"
grep -c 'action=list' /tmp/auto.html || true
echo "== cleanup =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->exec("DELETE FROM users WHERE username=\"autologin_test\"");
echo "users left: ", $db->query("SELECT COUNT(*) FROM users")->fetchColumn(), "\n";
'