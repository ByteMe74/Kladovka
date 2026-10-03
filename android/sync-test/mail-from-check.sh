#!/bin/bash
R=/www/wwwroot/kladovka.dr6ter.ru
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
php -l /tmp/api.php
cp /tmp/api.php $R/api.php && chown www:www $R/api.php
echo "== register with real noreply from =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=register -H 'Content-Type: application/json' -d '{"username":"mail_from_test","email":"noreply@dr6ter.ru","password":"secret123"}' -w '\n[%{http_code}]\n'
sleep 4
echo "== delivery =="
grep -E 'to=<noreply@dr6ter.ru>' /var/log/mail.log | tail -4 | sed 's/^.*postfix/  postfix/'
echo "== mailq =="
mailq 2>/dev/null | tail -2
echo "== find stored message headers (DKIM check) =="
find /www /home /var -maxdepth 4 -path '*noreply*' -name 'cur' -o -maxdepth 4 -path '*noreply*' -name 'new' 2>/dev/null | head -3
echo "== cleanup =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->exec("DELETE FROM users WHERE username=\"mail_from_test\"");
echo "users left: ", $db->query("SELECT COUNT(*) FROM users")->fetchColumn(), "\n";
'