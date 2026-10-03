#!/bin/bash
echo "== panel mysql files =="
ls /www/server/panel/data/ | grep -iE 'mysql|database|sql' 
echo "== config json variants =="
for f in /www/server/panel/config/mysql.json /www/server/panel/config/database.json /www/server/panel/data/databases.json /www/server/panel/data/mysql.json; do
  [ -f "$f" ] && echo "### $f" && cat "$f" | head -40
done
echo "== sw_mysql_pass.py head =="
head -60 /www/server/panel/class/safe_warning/sw_mysql_pass.py 2>/dev/null
echo "== grep AES/password keys =="
grep -rEola '"mysql_root[a-z_]*"\s*[:=]\s*"[^"]+"' /www/server/panel 2>/dev/null | head -5
grep -rEola 'mysql_[a-z_]*password\s*=\s*["'"'"'][^"'"'"']+' /www/server/panel/class 2>/dev/null | head -5