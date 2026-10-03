#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== verbose headers =="
curl "${RES[@]}" -o /dev/null -D - https://kladovka.dr6ter.ru/ | head -15
echo "== error logs =="
ls -t /www/wwwlogs/*.log 2>/dev/null | head -5
tail -30 /www/wwwlogs/kladovka.dr6ter.ru.error.log 2>/dev/null || tail -30 /www/wwwlogs/*.error.log 2>/dev/null | head -40
echo "== fpm error =="
tail -20 /www/server/php/83/var/log/php-fpm.log 2>/dev/null | tail -20
echo "== try page direct via wp-cli =="
cd /www/wwwroot/kladovka.dr6ter.ru
php /tmp/wp-cli.phar post get 4 --field=post_content --allow-root
echo "== shortcode test =="
php /tmp/wp-cli.phar eval 'echo substr(do_shortcode("[kladovka]"),0,500);' --allow-root