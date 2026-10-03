#!/bin/bash
set -e
cd /tmp
curl -sL --max-time 120 -o sqlite-rel.zip https://github.com/WordPress/sqlite-database-integration/releases/download/v3.0.1/plugin-sqlite-database-integration.zip
ls -la sqlite-rel.zip
rm -rf sqlite-rel && mkdir sqlite-rel
unzip -q -o sqlite-rel.zip -d sqlite-rel
echo "=== top ==="
ls sqlite-rel
echo "=== find db.copy/load.php ==="
find sqlite-rel -maxdepth 3 -name 'db.copy' -o -maxdepth 3 -name 'load.php' | head