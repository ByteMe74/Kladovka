#!/bin/bash
echo "== SPF/DKIM DNS dr6ter.ru =="
for r in dr6ter.ru kladovka.dr6ter.ru; do
  echo "--- $r TXT ---"
  dig +short TXT $r 2>/dev/null || nslookup -type=TXT $r 2>/dev/null | grep -i txt
done
echo "--- $r DKIM (_domainkey) ---"
dig +short TXT default._domainkey.dr6ter.ru 2>/dev/null || echo "(нет записи default._domainkey)"
echo "== opendkim =="
systemctl is-active opendkim 2>/dev/null || echo "opendkim not active"
ls /etc/opendkim/keys/ 2>/dev/null | head
echo "== postfix dkim milter =="
grep -iE 'milter|dkim' /etc/postfix/main.cf 2>/dev/null || echo "(нет milter в main.cf)"