#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/home3.html
echo "== data in page =="
grep -o 'С1\|С2\|С3\|Контейнер 1' /tmp/home3.html | sort | uniq -c
echo "== wp admin password =="
cat /root/kladovka-wp-admin.txt; echo
echo "== kl=raw auth check =="
curl "${RES[@]}" -o /dev/null -w 'kl=raw (anon): HTTP %{http_code}\n' 'https://kladovka.dr6ter.ru/?kl=raw'
echo "== api status =="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -o /dev/null -w 'api list: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $TOKEN"