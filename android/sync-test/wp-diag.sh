#!/bin/bash
echo "== sudo mysql (socket) =="
sudo mysql -e 'SELECT VERSION();' 2>&1 | head -3
echo "== debian.cnf =="
cat /etc/mysql/debian.cnf 2>/dev/null | head -8
echo "== panel config keys =="
python3 - <<'PY'
import json,sys
try:
    d=json.load(open('/www/server/panel/config/config.json'))
    for k,v in d.items():
        s=str(v)
        print(k, '=', (s[:40]+'...') if len(s)>40 else s)
except Exception as e:
    print('err', e)
PY
echo "== wp download test =="
curl -sIL --max-time 25 https://wordpress.org/latest.tar.gz | head -6
echo "---"
curl -sIL --max-time 25 https://ru.wordpress.org/latest-ru_RU.tar.gz | head -6