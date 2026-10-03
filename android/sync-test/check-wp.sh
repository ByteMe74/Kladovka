#!/bin/bash
echo "== OS/PHP =="
cat /etc/os-release | head -2
php -v | head -1
echo "== MySQL/MariaDB =="
which mysql mariadb 2>/dev/null; systemctl list-units --type=service 2>/dev/null | grep -iE 'mysql|maria' || echo "no mysql service"
ls /www/server/mysql/bin/mysql 2>/dev/null && echo "aapanel mysql present" || echo "no aapanel mysql bin"
echo "== disk =="
df -h /www | tail -1
echo "== webroot =="
ls -la /www/wwwroot/kladovka.dr6ter.ru/
echo "== php modules =="
php -m | grep -iE 'curl|json|sqlite|pdo|mbstring|xml|zip' | tr '\n' ' '; echo
echo "== memory =="
free -h | head -2
echo "== aapanel apps =="
ls /www/server/panel/site 2>/dev/null | head -3 || true