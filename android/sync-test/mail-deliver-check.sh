#!/bin/bash
R=/www/wwwroot/kladovka.dr6ter.ru
DB=$R/data/kladovka.db
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
php -l /tmp/api.php
cp /tmp/api.php $R/api.php && chown www:www $R/api.php
echo "== fresh register (email) =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=register -H 'Content-Type: application/json' -d '{"username":"mail_check","email":"mail-check@dr6ter.ru","password":"secret123"}' -w '\n[%{http_code}]\n'
echo "== wait for delivery + check log =="
sleep 4
grep -E 'to=<mail-check@dr6ter.ru>' /var/log/mail.log | tail -3 | sed 's/^.*postfix/  postfix/'
echo "== mailq =="
mailq 2>/dev/null | tail -3
echo "== cleanup =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->exec("DELETE FROM users WHERE username IN (\"mail_check\",\"old_account\",\"emai_l_user\")");
echo "users left: ", $db->query("SELECT COUNT(*) FROM users")->fetchColumn(), "\n";
'