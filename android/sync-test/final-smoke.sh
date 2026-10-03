#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== final smoke: login page =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/f1.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Войти\|Создать аккаунт\|Пароль' /tmp/f1.html | sort | uniq -c
echo "== final smoke: register =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/?view=register -o /tmp/f2.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Сколько будет\|защита от ботов\|Повторите пароль' /tmp/f2.html | sort | uniq -c
echo "== final smoke: admin auth end-to-end =="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s "https://kladovka.dr6ter.ru/api.php?action=status" -H "Authorization: Bearer $TOKEN" -w '\nstatus: [%{http_code}]\n'
echo "== no errors in logs (after 00:40) =="
grep -iE 'fatal|parse error|uncaught' /www/wwwlogs/kladovka.dr6ter.ru.error.log | grep -vE '\.env|404|hello-world|wp-' | awk '$2 >= "00:40"' | tail -5 || echo "(none)"
echo "== done =="