#!/bin/bash
cat /www/wwwroot/server-config.php 2>/dev/null | sed 's/\(password_hash[^=]*=\s*\).*/\1"***"/'
echo "---"
ls -la /www/wwwroot/kladovka.dr6ter.ru/data/ 2>/dev/null
echo "---"
cat /www/wwwroot/kladovka.dr6ter.ru/legacy/index.php >/dev/null 2>&1 && echo "legacy ok" || echo "legacy missing"