#!/bin/bash
DB=/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db
echo "== users before cleanup =="
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
for r in c.execute('SELECT id,username,created_at,ip FROM users'):
    print(r)
"
echo "== delete test users =="
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
c.execute(\"DELETE FROM users WHERE username IN ('test_client','rl_a','rl_b','rl_c')\")
c.commit()
print('deleted test users, remaining:', c.execute('SELECT COUNT(*) FROM users').fetchone()[0])
"
echo "== reset reg attempts for 127.0.0.1 only =="
python3 -c "
import json
p='/www/wwwroot/kladovka.dr6ter.ru/data/reg-attempts.json'
d=json.load(open(p))
d.pop('127.0.0.1',None)
json.dump(d,open(p,'w'))
print('reg-attempts now:',d)
"
echo "== final: users =="
python3 -c "
import sqlite3
c=sqlite3.connect('$DB')
print(c.execute('SELECT id,username,ip FROM users').fetchall())
"