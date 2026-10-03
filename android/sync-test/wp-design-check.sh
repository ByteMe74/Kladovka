#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== css vars on page =="
grep -o '\-\-kl-primary:[^;]*;\|--kl-accent:[^;]*;\|--kl-bg:[^;]*;\|--kl-radius:[^;]*;' /tmp/futuristic.html | head
echo "== google fonts =="
grep -o 'fonts.googleapis.com/css2[^"]*' /tmp/futuristic.html | head -2
echo "== keyframes in delivered css =="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ | grep -o '@keyframes kl-[a-zA-Z]*' | sort -u
echo "== customize after login =="
rm -f /tmp/j2.txt
NONCE=$(curl "${RES[@]}" --compressed -c /tmp/j2.txt https://kladovka.dr6ter.ru/ | grep -o 'name="kl_login_nonce" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
curl "${RES[@]}" -c /tmp/j2.txt -b /tmp/j2.txt -o /dev/null -w 'login: %{http_code}\n' \
  --data-urlencode "kl_login=1" --data-urlencode "kl_login_nonce=$NONCE" \
  --data-urlencode "log=admin" --data-urlencode "pwd=UAol2JzPtIvVnS" https://kladovka.dr6ter.ru/
curl "${RES[@]}" -b /tmp/j2.txt -o /dev/null -w 'customize: %{http_code}\n' 'https://kladovka.dr6ter.ru/wp-admin/customize.php?url=/'
echo "== settings saved? =="
curl "${RES[@]}" -b /tmp/j2.txt -o /dev/null -w 'wp-admin theme mods: %{http_code}\n' 'https://kladovka.dr6ter.ru/wp-admin/options.php'