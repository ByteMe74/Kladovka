#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== lint =="
php -l /tmp/index.php
echo "== deploy =="
cp /tmp/index.php $R/index.php
chown www:www $R/index.php
echo "== fix legacy =="
cp $R/index.php $R/legacy/index.php
sed -i "s#__DIR__ . '/../server-config.php'#__DIR__ . '/../../server-config.php'#" $R/legacy/index.php
chown www:www $R/legacy/index.php
echo "== test =="
curl -sk --resolve kladovka.dr6ter.ru:443:127.0.0.1 --compressed https://kladovka.dr6ter.ru/ -o /tmp/final.html -w 'HTTP %{http_code} %{size_download}b\n'
echo "== markers =="
grep -o 'lastSig\|kl-first\|kl-fadeUp' /tmp/final.html | sort | uniq -c
echo "== JS syntax =="
python3 -c 'import re; t=open("/tmp/final.html").read(); m=re.search(r"<script>(.*?)</script>",t,re.S); print("JS extracted:",len(m.group(1)),"chars" if m else "NOT FOUND")'
echo "=== ALL OK ==="