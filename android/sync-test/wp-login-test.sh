#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
J=/tmp/kl-jar.txt
rm -f $J
curl "${RES[@]}" --compressed -c $J https://kladovka.dr6ter.ru/ -o /tmp/anon.html
NONCE=$(grep -o 'name="kl_login_nonce" value="[^"]*"' /tmp/anon.html | head -1 | sed 's/.*value="//;s/"//')
echo "login nonce: $NONCE"
echo "== login POST =="
curl "${RES[@]}" -c $J -b $J -o /dev/null -w 'login: HTTP %{http_code}, redirect %{redirect_url}\n' \
  --data-urlencode "kl_login=1" --data-urlencode "kl_login_nonce=$NONCE" \
  --data-urlencode "log=admin" --data-urlencode "pwd=UAol2JzPtIvVnS" \
  https://kladovka.dr6ter.ru/
echo "== page after login =="
curl "${RES[@]}" --compressed -b $J https://kladovka.dr6ter.ru/ -o /tmp/auth.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'Добавить\|Удалить\|Выйти\|Админка\|kl-form-nonce="[^"]*"\|data-nonce="[^"]*"' /tmp/auth.html | sort | uniq -c | head
echo "== kl=raw with auth =="
curl "${RES[@]}" -b $J -o /tmp/raw.json -w 'raw: HTTP %{http_code}\n' https://kladovka.dr6ter.ru/?kl=raw
python3 -c 'import json;d=json.load(open("/tmp/raw.json"));print("places:",len(d["places"]),"shelves:",len(d["shelves"]),"containers:",len(d["containers"]),"items:",len(d["items"]))'