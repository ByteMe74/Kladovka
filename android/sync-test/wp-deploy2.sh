#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== php lint =="
php -l /tmp/kladovka.php
echo "== deploy =="
cp /tmp/kladovka.php /tmp/kladovka.css $R/wp-content/mu-plugins/
chown www:www $R/wp-content/mu-plugins/kladovka.php $R/wp-content/mu-plugins/kladovka.css
echo "== delete hello world =="
cd $R
php /tmp/wp-cli.phar post list --post_type=post --field=ID --allow-root | xargs -r -I{} php /tmp/wp-cli.phar post delete {} --force --allow-root || true
echo "== users =="
php /tmp/wp-cli.phar user list --fields=ID,user_login,roles --allow-root
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== page markers (anon) =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/home4.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'kl-head\|kl-stats\|kl-login\|kladovka-page\|С1\|С2\|С3\|Контейнер 1\|Войти для управления' /tmp/home4.html | sort | uniq -c