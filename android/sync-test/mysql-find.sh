#!/bin/bash
echo "== .my.cnf =="
cat /root/.my.cnf 2>/dev/null
cat /www/server/mysql/.my.cnf 2>/dev/null || true
ls -la /www/server/mysql/ 2>/dev/null | head -20
echo "== search panel for mysql pass =="
grep -rEl 'mysql.*pass|pass.*mysql|root_password|mysql_root' /www/server/panel --include='*.py' --include='*.json' --include='*.conf' 2>/dev/null | head -10
echo "== data dirs =="
ls /www/server/panel/data/ 2>/dev/null | head -20
cat /www/server/panel/data/default.pl 2>/dev/null; echo
echo "== try common =="
for p in 'zahedier0mi8Vae' '1a2b3c4d' 'aapanel' 'root' '123456'; do
  if mysql -uroot -p"$p" -e 'SELECT 1;' 2>/dev/null; then echo "PASSWORD FOUND: $p"; break; fi
done
echo done