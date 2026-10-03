#!/bin/bash
R=/www/wwwroot/kladovka.dr6ter.ru
DB=$R/data/kladovka.db
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== lint+deploy =="
php -l /tmp/api.php && php -l /tmp/index.php
cp /tmp/api.php $R/api.php && cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php
echo "== create legacy user =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
$db->exec("DELETE FROM users WHERE username IN (\"old_account\",\"emai_l_user\")");
$db->prepare("INSERT INTO users (username,email,password_hash,email_verified,created_at,ip) VALUES (?,?,?,0,?,?)")
   ->execute(["old_account","",password_hash("legacy123",PASSWORD_DEFAULT),1788983000000,"127.0.0.1"]);
echo "ok\n";
'
echo "== 1. web login legacy -> panel with mail form =="
curl "${RES[@]}" --compressed -c /tmp/leg.txt -b /tmp/leg.txt --data-urlencode "username=old_account" --data-urlencode "password=legacy123" https://kladovka.dr6ter.ru/ -o /tmp/p1.html -w '[%{http_code}] %{size_download}b\n'
grep -o 'Подтвердите почту\|Укажите почту' /tmp/p1.html | sort | uniq -c
echo "== 2. submit email (token now from session) =="
curl "${RES[@]}" --compressed -c /tmp/leg.txt -b /tmp/leg.txt --data-urlencode "mail_action=send" --data-urlencode "mail_email=old-account@dr6ter.ru" https://kladovka.dr6ter.ru/ -o /tmp/p2.html -w '[%{http_code}] %{size_download}b\n'
grep -o 'Ссылка отправлена на old-account@dr6ter.ru\|Подтвердите почту' /tmp/p2.html | sort | uniq -c
CT=$(python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
print(c.execute(\"SELECT confirm_token FROM users WHERE username='old_account'\").fetchone()[0])
")
echo "confirm token set: $([ ${#CT} -ge 20 ] && echo YES || echo NO)"
echo "== 3. confirm link =="
curl "${RES[@]}" -s -o /dev/null -w 'confirm: [%{http_code}] -> %{redirect_url}\n' "https://kladovka.dr6ter.ru/api.php?action=confirm&u=old_account&t=$CT"
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
print('verified:', c.execute(\"SELECT email,email_verified FROM users WHERE username='old_account'\").fetchone())
"
echo "== 4. login after confirm + data =="
UT=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"old_account","password":"legacy123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=status -H "Authorization: Bearer $UT" -o /dev/null -w 'status: [%{http_code}]\n'
echo "== 5. mail delivery check =="
sleep 2
grep -E 'to=<old-account@dr6ter.ru>' /var/log/mail.log | tail -3 | sed 's/^.*postfix/  postfix/' || echo "(нет записи)"
echo "== 6. resend throttle (60s) =="
RESEND=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=verify_email -H 'Content-Type: application/json' -H "Authorization: Bearer $UT" -d '{"email":"old-account@dr6ter.ru"}' -o /tmp/rr.json -w '%{http_code}')
echo "verify_email right after: [$RESEND] $(cat /tmp/rr.json)"
echo "== cleanup =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->exec("DELETE FROM users WHERE username IN (\"old_account\",\"emai_l_user\")");
echo "users left: ", $db->query("SELECT COUNT(*) FROM users")->fetchColumn(), "\n";
'