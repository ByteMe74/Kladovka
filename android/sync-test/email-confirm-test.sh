#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
DB=$R/data/kladovka.db
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== lint =="
php -l /tmp/api.php
php -l /tmp/index.php
echo "== deploy =="
cp /tmp/api.php $R/api.php && cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php

echo "== 1. register WITH email =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=register -H 'Content-Type: application/json' -d '{"username":"emai_l_user","email":"emai-l-user@dr6ter.ru","password":"secret123"}' -w '\n[%{http_code}]\n'
echo "-- DB row --"
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
for r in c.execute('SELECT id,username,email,email_verified,confirm_token!=\"\" tok,confirm_expires>0 exp FROM users WHERE username=\"emai_l_user\"'):
    print(r)
"
echo "== 2. login before confirm =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"emai_l_user","password":"secret123"}' | python3 -m json.tool
echo "== 3. data access with unverified token -> 403 =="
UTOK=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"emai_l_user","password":"secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $UTOK" -o /dev/null -w 'list: [%{http_code}]\n'
echo "== 4. click confirm link (token from DB) =="
CT=$(python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
print(c.execute('SELECT confirm_token FROM users WHERE username=\"emai_l_user\"').fetchone()[0])
")
curl "${RES[@]}" -s -o /dev/null -w 'confirm redirect: [%{http_code}] -> %{redirect_url}\n' "https://kladovka.dr6ter.ru/api.php?action=confirm&u=emai_l_user&t=$CT"
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
print('verified after confirm:', c.execute('SELECT email_verified, confirm_token FROM users WHERE username=\"emai_l_user\"').fetchone())
"
echo "== 5. login after confirm + data =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"emai_l_user","password":"secret123"}' | python3 -m json.tool
UTOK2=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"emai_l_user","password":"secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=status -H "Authorization: Bearer $UTOK2" | python3 -c 'import sys,json;print("status:",json.load(sys.stdin))'