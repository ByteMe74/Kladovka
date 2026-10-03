#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
mkdir -p $R/wp-content/plugins
rm -rf $R/wp-content/plugins/sqlite-database-integration
mv $R/wp-content/sqlite-database-integration $R/wp-content/plugins/sqlite-database-integration
ls $R/wp-content/plugins/sqlite-database-integration | head -6
echo "== wp core install =="
ADMIN_PW=$(cat /root/kladovka-wp-admin.txt)
cd $R && php /tmp/wp-cli.phar core install --url=https://kladovka.dr6ter.ru --title='Кладовка' --admin_user=admin --admin_password="$ADMIN_PW" --admin_email=admin@dr6ter.ru --skip-email --allow-root
echo "== verify =="
php /tmp/wp-cli.phar option get siteurl --allow-root
php /tmp/wp-cli.phar option get home --allow-root
ls -la $R/wp-content/database/ 2>/dev/null || echo "db dir missing"