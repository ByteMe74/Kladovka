#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== http1.1 with compressed =="
curl "${RES[@]}" --http1.1 --compressed -D /tmp/h.txt https://kladovka.dr6ter.ru/ -o /tmp/home2.html
grep -iE 'HTTP/|content-length|content-encoding|transfer-encoding' /tmp/h.txt
wc -c /tmp/home2.html
echo "== body head =="
head -c 400 /tmp/home2.html; echo
echo "== any kl markers =="
grep -c 'kl-wrap' /tmp/home2.html || true
echo "== ?p=4 =="
curl "${RES[@]}" --compressed -o /tmp/p4.html -w 'p4: HTTP %{http_code} %{size_download} bytes\n' 'https://kladovka.dr6ter.ru/?p=4'
grep -c 'kl-wrap' /tmp/p4.html || true
echo "== php error since 23:12 =="
awk '/2026\/09\/09 23:1[2-9]|2026\/09\/09 23:2/' /www/wwwlogs/kladovka.dr6ter.ru.error.log | grep -i 'php\|fatal\|request: "GET / HTTP' | head -20
echo "== restore legacy =="
cp /root/kladovka-wp-backup-20260909-224815/index.php /www/wwwroot/kladovka.dr6ter.ru/legacy/index.php
head -3 /www/wwwroot/kladovka.dr6ter.ru/legacy/index.php