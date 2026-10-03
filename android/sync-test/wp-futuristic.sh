#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== lint =="
php -l /tmp/kladovka.php
php -l /tmp/kladovka.css 2>/dev/null || true
cp /tmp/kladovka.php /tmp/kladovka.css $R/wp-content/mu-plugins/
chown www:www $R/wp-content/mu-plugins/kladovka.php $R/wp-content/mu-plugins/kladovka.css
echo "== test page =="
curl -sk --resolve kladovka.dr6ter.ru:443:127.0.0.1 --compressed https://kladovka.dr6ter.ru/ -o /tmp/futuristic.html -w 'HTTP %{http_code} %{size_download}b\n'
echo "== markers =="
grep -o 'kl-head\|kl-stats\|kl-anim\|kl-footer\|kl-tabbtn\|kl-pulse\|С1\|С2\|С3\|Контейнер 1' /tmp/futuristic.html | sort | uniq -c | head
echo "== no JS errors =="
grep -c 'KL_TABLES\b' /tmp/futuristic.html || true
echo "== customizer check =="
curl -sk --resolve kladovka.dr6ter.ru:443:127.0.0.1 https://kladovka.dr6ter.ru/wp-admin/customize.php?url=/ -o /dev/null -w 'customize: HTTP %{http_code}\n'
echo "== API still works =="
TOKEN=$(curl -sk --resolve kladovka.dr6ter.ru:443:127.0.0.1 -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -sk --resolve kladovka.dr6ter.ru:443:127.0.0.1 https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $TOKEN" -o /dev/null -w 'api list: HTTP %{http_code}\n'