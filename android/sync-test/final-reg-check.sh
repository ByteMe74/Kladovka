#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
R=/www/wwwroot/kladovka.dr6ter.ru
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== A. admin login unaffected =="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $TOKEN" -o /dev/null -w 'list: [%{http_code}] (admin)\n'
echo "== B. existing account (Viktor) -> confirm panel on login =="
curl "${RES[@]}" --compressed -c /tmp/vj.txt --data-urlencode "username=Viktor" --data-urlencode "password=test123" https://kladovka.dr6ter.ru/ -o /tmp/vp.html -w '[%{http_code}] %{size_download}b\n'
echo "(если пароль неверный - просто проверяем страницу входа)"
grep -o 'Подтвердите почту\|Укажите почту\|Кладовка' /tmp/vp.html | sort | uniq -c | head
echo "== C. register view has email field =="
curl "${RES[@]}" --compressed 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rv.html -w '[%{http_code}]\n'
grep -o 'name="reg_email"\|Пароль\|Создать аккаунт' /tmp/rv.html | sort | uniq -c
echo "== D. bad token still 401 =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer nope" -o /dev/null -w '[%{http_code}]\n'
echo "== E. confirm link invalid -> fail redirect =="
curl "${RES[@]}" -s -o /dev/null -w '[%{http_code}] -> %{redirect_url}\n' 'https://kladovka.dr6ter.ru/api.php?action=confirm&u=whatever&t=zzz'
echo "== F. PHP errors since deploy =="
grep -iE 'fatal|parse error|uncaught' /www/wwwlogs/kladovka.dr6ter.ru.error.log 2>/dev/null | grep -vE '\.env|404|hello-world|wp-' | grep '2026/09/10 00:4' | tail -5 || echo "(нет ошибок)"
echo "== done =="