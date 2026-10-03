#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== fix legacy path =="
cp $R/index.php $R/legacy/index.php
sed -i "s#__DIR__ . '/../server-config.php'#__DIR__ . '/../../server-config.php'#" $R/legacy/index.php
chown www:www $R/legacy/index.php
php -l $R/legacy/index.php
grep -n "server-config" $R/legacy/index.php | head -2
echo "== robots.txt =="
printf 'User-agent: *\nDisallow: /\n' > $R/robots.txt
chown www:www $R/robots.txt
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== legacy check =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/legacy/ -o /tmp/chk-leg.html -w 'legacy: %{http_code} %{size_download}b\n'
grep -c 'Пароль администратора' /tmp/chk-leg.html || true
curl "${RES[@]}" --compressed -c /tmp/legj.txt -d "password=$ADMIN_PW" -L https://kladovka.dr6ter.ru/legacy/ -o /tmp/chk-leg2.html -w 'legacy login: %{http_code} %{size_download}b\n'
grep -o 'Кладовка\|Стеллажи\|API-ключ' /tmp/chk-leg2.html | sort | uniq -c
echo "== robots =="
curl "${RES[@]}" https://kladovka.dr6ter.ru/robots.txt -w ' [%{http_code}]\n'
echo "== error log after fix =="
sleep 1
grep -iE 'fatal|parse error' /www/wwwlogs/kladovka.dr6ter.ru.error.log | grep -v '\.env\|404\.html\|hello-world\|wp-' | tail -3 || echo "(none)"