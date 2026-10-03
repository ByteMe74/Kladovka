#!/bin/bash
cat /www/wwwroot/kladovka.dr6ter.ru/wp-content/db/wp-pdo-mysql-on-sqlite.php
echo "=== tags ==="
curl -s --max-time 20 'https://api.github.com/repos/WordPress/sqlite-database-integration/tags?per_page=8' | python3 -c '
import sys,json
try:
    for t in json.load(sys.stdin): print(t["name"])
except Exception as e: print("ERR", e)
'
echo "=== releases ==="
curl -s --max-time 20 'https://api.github.com/repos/WordPress/sqlite-database-integration/releases?per_page=3' | python3 -c '
import sys,json
try:
    for r in json.load(sys.stdin): print(r["tag_name"], r["name"], [a["name"] for a in r.get("assets",[])])
except Exception as e: print("ERR", e)
'
echo "=== composer? ==="
which composer php 2>/dev/null