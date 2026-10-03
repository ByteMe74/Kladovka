#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== homepage =="
curl "${RES[@]}" https://kladovka.dr6ter.ru/ -o /tmp/home.html -w 'HTTP %{http_code}, %{size_download} bytes\n'
echo "== markers =="
grep -o 'kl-wrap\|kl-tabs\|С1\|С2\|С3\|Контейнер 1\|Kladowka\|Кладовая\|нович' /tmp/home.html | sort | uniq -c | head
echo "== wp-admin reachable =="
curl "${RES[@]}" -o /dev/null -w 'wp-login: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/wp-login.php
echo "== api still alive =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" -o /dev/null -w 'api login: HTTP %{http_code}\n'
echo "== legacy =="
curl "${RES[@]}" -o /dev/null -w 'legacy: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/legacy/
echo "== favicon =="
curl "${RES[@]}" -o /dev/null -w 'favicon: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/favicon.svg