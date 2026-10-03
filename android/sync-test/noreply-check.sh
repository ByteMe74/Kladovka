#!/bin/bash
echo "== dovecot users (passwd-style) =="
sudo doveadm user '*' 2>/dev/null | head -20 || echo "doveadm не доступен"
echo "== virtual users file =="
ls -la /etc/postfix/vmailbox /etc/postfix/virtual /etc/dovecot/users 2>/dev/null
grep -i 'noreply\|dr6ter' /etc/postfix/vmailbox 2>/dev/null | head -10
echo "== postfix main.cf relay =="
grep -E 'myhostname|mydomain|mydestination|virtual_mailbox|transport_maps|relay' /etc/postfix/main.cf 2>/dev/null | head -15
echo "== test: send real mail to noreply@dr6ter.ru =="
php -r '
$body="Здравствуйте!\n\nТест письма с сервера кладовки. Ссылка: https://kladovka.dr6ter.ru/api.php?action=confirm&u=test&t=abc123\n";
$subj="=?UTF-8?B?".base64_encode("Кладовка: проверка почты")."?=";
$from="=?UTF-8?B?".base64_encode("Кладовка")."?= <no-reply@kladovka.dr6ter.ru>";
$h="From: $from\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n";
var_dump(@mail("noreply@dr6ter.ru", $subj, base64_encode($body), $h));
'
sleep 5
echo "== log =="
grep -E 'to=<noreply@dr6ter.ru>' /var/log/mail.log | tail -4 | sed 's/^.*postfix/  postfix/'
echo "== mailq =="
mailq 2>/dev/null | tail -3