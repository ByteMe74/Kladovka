#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
B=/root/wp-removed-20260910
echo "== lint new index =="
php -l /tmp/index.php
echo "== archive WP =="
mkdir -p $B
cd $R
mv wp-admin wp-includes wp-content $B/ 2>/dev/null || true
for f in wp-config.php wp-activate.php wp-blog-header.php wp-comments-post.php wp-cron.php wp-links-opml.php wp-load.php wp-login.php wp-mail.php wp-settings.php wp-signup.php wp-trackback.php license.txt readme.html xmlrpc.php wp-sitemap.php; do
  [ -f "$f" ] && mv "$f" $B/ || true
done
[ -f index.php ] && mv index.php $B/index-wp.php || true
echo "== deploy our index =="
cp /tmp/index.php $R/index.php
chown www:www $R/index.php
echo "== webroot now =="
ls $R
echo "== WP gone? =="
ls $B | head -20