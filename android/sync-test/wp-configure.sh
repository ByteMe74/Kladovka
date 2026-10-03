#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
mkdir -p $R/wp-content/mu-plugins
cp /tmp/kladovka.php /tmp/kladovka.css $R/wp-content/mu-plugins/
chown -R www:www $R/wp-content/mu-plugins
echo "== mu-plugins =="
ls -la $R/wp-content/mu-plugins/

cd $R
echo "== theme =="
php /tmp/wp-cli.phar theme list --allow-root --fields=name,status
ACTIVE=$(php /tmp/wp-cli.phar theme list --allow-root --status=active --field=name 2>/dev/null | head -1)
if [ -z "$ACTIVE" ]; then
  FIRST=$(php /tmp/wp-cli.phar theme list --allow-root --field=name | head -1)
  echo "no active theme, activating: $FIRST"
  php /tmp/wp-cli.phar theme activate "$FIRST" --allow-root
fi

echo "== create page =="
PID=$(php /tmp/wp-cli.phar post create --post_type=page --post_title='Кладовая' --post_content='[kladovka]' --post_status=publish --porcelain --allow-root)
echo "page id: $PID"
php /tmp/wp-cli.phar option update show_on_front page --allow-root
php /tmp/wp-cli.phar option update page_on_front $PID --allow-root
php /tmp/wp-cli.phar option update blogname 'Кладовка' --allow-root

echo "== perms =="
chown -R www:www $R/wp-content/database $R/wp-content/uploads 2>/dev/null || true

echo "== page url =="
php /tmp/wp-cli.phar option get home --allow-root