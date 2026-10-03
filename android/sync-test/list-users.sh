#!/bin/bash
sudo python3 - <<'PY'
import sqlite3
c=sqlite3.connect('/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db')
print('users:')
for r in c.execute('SELECT id,username,email,email_verified,ip FROM users'):
    print(' ', r)
PY