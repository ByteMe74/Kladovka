#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
echo "== wp-config head =="
head -20 $R/wp-config.php
echo "== db.php =="
cat $R/wp-content/db.php
echo "== wp db check =="
cd $R && php /tmp/wp-cli.phar db check --allow-root 2>&1 | head -20 || true
echo "== DB_ENGINE refs in core =="
grep -rn 'DB_ENGINE' $R/wp-includes/*.php $R/wp-settings.php 2>/dev/null | head -12
echo "== sqlite refs in load.php =="
grep -n 'sqlite\|db.php' $R/wp-includes/load.php | head -12
echo "== database dir =="
ls -la $R/wp-content/database/ 2>/dev/null || echo "no wp-content/database"