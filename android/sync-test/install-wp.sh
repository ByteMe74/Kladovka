#!/bin/bash
set -e
ROOT=/www/wwwroot/kladovka.dr6ter.ru
cd /tmp

echo "== 1. WP 7.1 (github) =="
if [ ! -s /tmp/wp.tar.gz ]; then
  curl -sL --max-time 600 -o wp.tar.gz https://codeload.github.com/WordPress/WordPress/tar.gz/refs/tags/7.1
fi
ls -la /tmp/wp.tar.gz
rm -rf wpsrc && mkdir wpsrc
tar -xzf wp.tar.gz -C wpsrc
WPDIR=$(ls -d wpsrc/WordPress-* | head -1)
echo "source: $WPDIR"

echo "== 2. sqlite plugin =="
curl -sL -o sqldb.tar.gz https://codeload.github.com/WordPress/sqlite-database-integration/tar.gz/refs/heads/trunk
rm -rf sqldb && mkdir sqldb
tar -xzf sqldb.tar.gz -C sqldb
SQLDIR=$(ls -d sqldb/sqlite-database-integration-* | head -1)
echo "plugin: $SQLDIR"

echo "== 3. wp-cli =="
curl -sL -o wp-cli.phar https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar
chmod +x wp-cli.phar
php wp-cli.phar --version --allow-root

echo "== 4. archive current index as legacy =="
mkdir -p $ROOT/legacy
[ -f $ROOT/index.php ] && mv $ROOT/index.php $ROOT/legacy/index.php

echo "== 5. copy WP files (keep api.php/data/favicon/legacy/.well-known) =="
cp -r $WPDIR/. $ROOT/

echo "== 6. sqlite plugin (release v3.0.1) =="
rm -rf $ROOT/wp-content/db $ROOT/wp-content/sqlite-database-integration
unzip -q -o /tmp/sqlite-rel.zip -d $ROOT/wp-content/
mv $ROOT/wp-content/plugin-sqlite-database-integration $ROOT/wp-content/sqlite-database-integration
cp $ROOT/wp-content/sqlite-database-integration/db.copy $ROOT/wp-content/db.php
ls $ROOT/wp-content/sqlite-database-integration | head

echo "== 7. wp-config.php =="
SALT=$(head -c 48 /dev/urandom | base64)
AUTH=$(head -c 48 /dev/urandom | base64)
SECURE=$(head -c 48 /dev/urandom | base64)
LOGGED=$(head -c 48 /dev/urandom | base64)
NONCE=$(head -c 48 /dev/urandom | base64)
AUTHSALT=$(head -c 48 /dev/urandom | base64)
SECURESALT=$(head -c 48 /dev/urandom | base64)
LOGGEDSALT=$(head -c 48 /dev/urandom | base64)
NONCESALT=$(head -c 48 /dev/urandom | base64)

cat > $ROOT/wp-config.php <<EOF
<?php
/** WordPress на SQLite (официальный плагин sqlite-database-integration). */
define( 'DB_ENGINE', 'sqlite' );
define( 'DB_NAME', 'kladovka' );
define( 'DB_USER', '' );
define( 'DB_PASSWORD', '' );
define( 'DB_HOST', 'localhost' );
define( 'DB_CHARSET', 'utf8mb4' );
define( 'DB_COLLATE', '' );
define( 'AUTH_KEY',         '${AUTH}' );
define( 'SECURE_AUTH_KEY',  '${SECURE}' );
define( 'LOGGED_IN_KEY',    '${LOGGED}' );
define( 'NONCE_KEY',        '${NONCE}' );
define( 'AUTH_SALT',        '${AUTHSALT}' );
define( 'SECURE_AUTH_SALT', '${SECURESALT}' );
define( 'LOGGED_IN_SALT',   '${LOGGEDSALT}' );
define( 'NONCE_SALT',       '${NONCESALT}' );
\$table_prefix = 'wp_';
define( 'WP_DEBUG', false );
if ( ! defined( 'ABSPATH' ) ) { define( 'ABSPATH', __DIR__ . '/' ); }
require_once ABSPATH . 'wp-settings.php';
EOF
echo "wp-config written"

echo "== 8. install =="
ADMIN_PW=$(head -c 12 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 14)
echo "$ADMIN_PW" > /root/kladovka-wp-admin.txt
chmod 600 /root/kladovka-wp-admin.txt
php wp-cli.phar core install --path=$ROOT --url=https://kladovka.dr6ter.ru --title='Кладовка' --admin_user=admin --admin_password="$ADMIN_PW" --admin_email=admin@dr6ter.ru --skip-email --allow-root

echo "== 9. permissions =="
chown -R www:www $ROOT
chmod 640 /root/kladovka-wp-admin.txt

echo "== 10. checks =="
ls $ROOT | head -25
php wp-cli.phar option get siteurl --path=$ROOT --allow-root
echo "DONE. admin password saved to /root/kladovka-wp-admin.txt"