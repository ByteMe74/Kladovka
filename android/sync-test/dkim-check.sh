#!/bin/bash
echo "== who listens on 11332 =="
ss -tlnp 2>/dev/null | grep 11332 || echo "nothing on 11332"
echo "== dkim/milter processes =="
ps aux | grep -iE 'dkim|rspamd|11332' | grep -v grep | head -5
echo "== rspamd dkim config =="
rspamadm dkim 2>/dev/null | head -10 || echo "rspamadm не доступен"
ls /etc/rspamd/local.d/dkim_signing.conf 2>/dev/null && cat /etc/rspamd/local.d/dkim_signing.conf 2>/dev/null | head -20
echo "== test real send + read delivered headers =="
php -r '
$body="тест DKIM\n";
$subj="=?UTF-8?B?".base64_encode("Кладовка: DKIM test")."?=";
$from="=?UTF-8?B?".base64_encode("Кладовка")."?= <noreply@dr6ter.ru>";
$h="From: $from\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n";
var_dump(@mail("noreply@dr6ter.ru", $subj, base64_encode($body), $h));
'
sleep 4
mailq 2>/dev/null | tail -2
echo "== grep delivered headers in dovecot store =="
MAILHOME=$(find /var/mail /home /var/vmail -maxdepth 2 -name '*noreply*' 2>/dev/null | head -3)
echo "mailstore: ${MAILHOME:-пусто}"