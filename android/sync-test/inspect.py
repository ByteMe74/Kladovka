import sqlite3
c = sqlite3.connect(r'C:\Users\Night\kladovka\sync-test\phone-execout.db')
for (t,) in c.execute("select name from sqlite_master where type='table'"):
    print(t, '->', c.execute('select count(*) from ' + t).fetchone()[0])
print('shelves:', c.execute('select id,name from shelves').fetchall())