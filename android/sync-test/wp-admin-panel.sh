#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
rm -f /tmp/j3.txt /tmp/adm.html
NONCE=$(curl "${RES[@]}" --compressed -c /tmp/j3.txt https://kladovka.dr6ter.ru/ | grep -o 'name="kl_login_nonce" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
curl "${RES[@]}" -c /tmp/j3.txt -b /tmp/j3.txt -o /dev/null -w 'login: %{http_code}\n' \
  --data-urlencode "kl_login=1" --data-urlencode "kl_login_nonce=$NONCE" \
  --data-urlencode "log=admin" --data-urlencode "pwd=UAol2JzPtIvVnS" https://kladovka.dr6ter.ru/
echo "== admin panel =="
curl "${RES[@]}" -b /tmp/j3.txt 'https://kladovka.dr6ter.ru/wp-admin/admin.php?page=kladovka-db' -o /tmp/adm.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Кладовка — база данных\|nav-tab-active\|Вещи ([0-9]*)\|Стеллажи ([0-9]*)\|Контейнеры ([0-9]*)\|С1\|Контейнер 1\|Добавить вещь' /tmp/adm.html | sort | uniq -c | head
echo "== menu item on admin page =="
curl "${RES[@]}" -b /tmp/j3.txt 'https://kladovka.dr6ter.ru/wp-admin/index.php' -o /tmp/dash.html -w 'HTTP %{http_code}\n'
grep -o '📦 Кладовка\|kladovka-db' /tmp/dash.html | sort | uniq -c | head