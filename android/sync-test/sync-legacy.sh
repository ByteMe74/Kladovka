#!/bin/bash
set -e
R=/www/wwwroot/kladovka.dr6ter.ru
cp $R/index.php $R/legacy/index.php
sed -i "s#__DIR__ . '/../server-config.php'#__DIR__ . '/../../server-config.php'#" $R/legacy/index.php
chown www:www $R/legacy/index.php
php -l $R/legacy/index.php
echo "legacy synced"