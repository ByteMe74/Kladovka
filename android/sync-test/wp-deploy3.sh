#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== lint =="
php -l /tmp/kladovka.php
cp /tmp/kladovka.php /tmp/kladovka.css $R/wp-content/mu-plugins/
chown www:www $R/wp-content/mu-plugins/kladovka.php $R/wp-content/mu-plugins/kladovka.css
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== homepage (server-rendered content, no JS) =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/s1.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o '<td>С1\|<td>С2\|<td>С3\|кл-tabbtn\|Контейнер 1\| данных' /tmp/s1.html | sort | uniq -c
echo "== default tab = shelves? =="
grep -o 'class="kl-tabbtn on[^"]*"' /tmp/s1.html | head -3
echo "== tab=containers =="
curl "${RES[@]}" --compressed 'https://kladovka.dr6ter.ru/?tab=containers' -o /tmp/s2.html -w 'HTTP %{http_code}\n'
grep -o 'Контейнер 1' /tmp/s2.html | head -2
echo "== search q=С2 =="
curl "${RES[@]}" --compressed 'https://kladovka.dr6ter.ru/?tab=shelves&q=%D0%A12' -o /tmp/s3.html -w 'HTTP %{http_code}\n'
grep -o 'найдено: 1 из 3' /tmp/s3.html | head -1