#!/bin/bash
set -e
TS=$(date +%Y%m%d-%H%M%S)
BK=/root/kladovka-wp-backup-$TS
echo "== backup =="
mkdir -p $BK
cp -r /www/wwwroot/kladovka.dr6ter.ru/. $BK/ 2>/dev/null || true
cp /www/server/panel/vhost/nginx/kladovka.dr6ter.ru.conf $BK/ 2>/dev/null || true
ls -la $BK | head -20
echo "backup saved to $BK"

echo "== mysql root access =="
if mysql -uroot -e 'SELECT VERSION();' 2>/dev/null; then
  echo "mysql -uroot (socket) OK"
elif [ -f /www/server/panel/config/config.json ]; then
  MPW=$(python3 -c "import json;print(json.load(open('/www/server/panel/config/config.json')).get('mysql_root',''))" 2>/dev/null)
  if [ -n "$MPW" ] && mysql -uroot -p"$MPW" -e 'SELECT 1;' 2>/dev/null; then
    echo "mysql root via panel config OK"
  else
    echo "MYSQL_ROOT_HINT=$MPW"
    echo "mysql root password unknown, will try socket+sudo"
  fi
else
  echo "no panel config"
fi

echo "== download wordpress =="
cd /tmp
if [ ! -f wp.tar.gz ]; then
  curl -sL -o wp.tar.gz https://ru.wordpress.org/latest-ru_RU.tar.gz || curl -sL -o wp.tar.gz https://wordpress.org/latest.tar.gz
fi
ls -la wp.tar.gz
rm -rf /tmp/wp-src && mkdir /tmp/wp-src
tar -xzf wp.tar.gz -C /tmp/wp-src
ls /tmp/wp-src | head