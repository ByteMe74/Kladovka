#!/bin/bash
echo "== wp tags =="
curl -s --max-time 20 'https://api.github.com/repos/WordPress/WordPress/tags?per_page=8' | python3 -c '
import sys,json
try:
    d=json.load(sys.stdin)
    for t in d: print(t["name"])
except Exception as e: print("ERR", e)
'
echo "== sqlite-db-integration repo =="
curl -s --max-time 20 'https://api.github.com/repos/WordPress/sqlite-database-integration' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("default_branch"), d.get("pushed_at"))'
echo "== wp-cli =="
curl -sI --max-time 15 https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar | head -3