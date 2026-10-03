#!/bin/bash
echo "== MTA check =="
which sendmail postfix msmtp exim4 2>/dev/null || echo "no MTA binaries in PATH"
php -r 'echo "sendmail_path=", ini_get("sendmail_path"), "\n";'
echo "== services =="
systemctl is-active postfix 2>/dev/null || service postfix status 2>&1 | head -3 || true
echo "== ports 25/587/465 =="
ss -ltnp 2>/dev/null | grep -E ':25|:587|:465' || echo "none listening"
echo "== mailq =="
mailq 2>/dev/null | head -5 || echo "no mailq"
echo "== php mail() test =="
php -r '
$ok = @mail("test-kladovka@dr6ter.ru", "Kl tab test", "test from kladovka server", "From: Kladovka <no-reply@kladovka.dr6ter.ru>\r\nContent-Type: text/plain; charset=utf-8");
var_dump($ok);
'
echo "== mail log tail =="
ls -la /var/log/mail* 2>/dev/null || echo "no mail log files"
tail -5 /var/log/mail.log 2>/dev/null || tail -5 /var/log/maillog 2>/dev/null || echo "(no readable mail log)"