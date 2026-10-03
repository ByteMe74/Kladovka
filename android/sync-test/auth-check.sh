#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
NONCE=$(curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ 2>/dev/null | grep -o 'name="pw_nonce" value="[^"]*"' | sed 's/.*value="//;s/"//')
echo "nonce: $NONCE"
curl "${RES[@]}" --compressed -c /tmp/fj.txt -d "password=$ADMIN_PW&PWNONCE=$NONCE" -L https://kladovka.dr6ter.ru/ -o /tmp/auth2.html -w 'auth: %{http_code} %{size_download}b\n'
grep -o 'lastSig\|kl-first\|async function refresh\|sig===lastSig' /tmp/auth2.html | sort | uniq -c