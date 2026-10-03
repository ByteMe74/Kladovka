#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== rate limit: register 3 users via API, expect 4th -> 429 =="
for i in rl_a rl_b rl_c rl_d; do
  CODE=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=register -H 'Content-Type: application/json' -d "{\"username\":\"$i\",\"password\":\"secret123\"}" -o /tmp/rl.json -w '%{http_code}')
  MSG=$(python3 -c 'import json;print(json.load(open("/tmp/rl.json")).get("error","ok"))' 2>/dev/null)
  echo "$i -> $CODE $MSG"
done
echo "== rate limit state file =="
cat /www/wwwroot/kladovka.dr6ter.ru/data/reg-attempts.json 2>/dev/null | python3 -c 'import sys,json;d=json.load(sys.stdin);print({k:len(v) for k,v in d.items()})'
echo "== admin login still ok =="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=export -H "Authorization: Bearer $TOKEN" -o /dev/null -w 'admin export: [%{http_code}]\n'
echo "== user token can read data =="
utok=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"test_client","password":"secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $utok" -o /dev/null -w 'user list: [%{http_code}]\n'
echo "== bad user login -> 401 + counted =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"test_client","password":"wrong"}' -o /dev/null -w 'bad pw: [%{http_code}]\n'
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"test_client","password":"wrong2"}' -o /dev/null -w 'bad pw2: [%{http_code}]\n'
echo "== users in db =="
sqlite3 /www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db 'SELECT id,username FROM users' 2>/dev/null || python3 -c "
import sqlite3
c=sqlite3.connect('/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db')
print(c.execute('SELECT id,username,length(password_hash) FROM users').fetchall())
"