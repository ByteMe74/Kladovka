#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
B=/root/wp-removed-20260910
[ -f $R/wp-config-sample.php ] && mv $R/wp-config-sample.php $B/ || true
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== login page =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/nl.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Пароль администратора\|Futuristic\|kl-fadeUp\|login-card' /tmp/nl.html | sort | uniq -c
echo "== login POST =="
curl "${RES[@]}" --compressed -c /tmp/nj.txt -d "password=$ADMIN_PW" -L https://kladovka.dr6ter.ru/ -o /tmp/na.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Кладовка\|Стеллажи\|Места\|Контейнеры\|Вещи\|kl-glow\|Автообновление' /tmp/na.html | sort | uniq -c | head
echo "== api still alive =="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $TOKEN" -o /tmp/ls.json -w 'api list: HTTP %{http_code}\n'
python3 -c 'import json;d=json.load(open("/tmp/ls.json"));print("places:",len(d["places"]),"shelves:",len(d["shelves"]),"containers:",len(d["containers"]),"items:",len(d["items"]))'
echo "== legacy still up =="
curl "${RES[@]}" -o /dev/null -w 'legacy: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/legacy/