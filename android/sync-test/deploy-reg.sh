#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== lint =="
php -l /tmp/api.php
php -l /tmp/index.php
echo "== deploy =="
cp /tmp/api.php $R/api.php
cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php
echo "== api list still ok (admin token) =="
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $TOKEN" -o /dev/null -w 'list: [%{http_code}]\n'
echo "== login page renders toggle =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/lg.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -c 'Создать аккаунт' /tmp/lg.html
echo "== register view + captcha =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/?view=register -b /tmp/regj.txt -c /tmp/regj.txt -o /tmp/rg.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Сколько будет [0-9+×-]*\|защита от ботов' /tmp/rg.html | head -3