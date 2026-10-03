#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "===== 1. PHP lint ====="
php -l /www/wwwroot/kladovka.dr6ter.ru/index.php
php -l /www/wwwroot/kladovka.dr6ter.ru/api.php
echo
echo "===== 2. HTTP (login page) ====="
curl "${RES[@]}" --compressed https://kladovka.dr6ter.ru/ -o /tmp/chk-login.html -w 'code=%{http_code} size=%{size_download}\n'
grep -c 'Пароль администратора' /tmp/chk-login.html
echo
echo "===== 3. HTTP -> HTTPS redirect ====="
curl -s --resolve kladovka.dr6ter.ru:80:127.0.0.1 -o /dev/null -w 'code=%{http_code} redirect=%{redirect_url}\n' http://kladovka.dr6ter.ru/
echo
echo "===== 4. login + main page ======"
curl "${RES[@]}" --compressed -c /tmp/chk-j.txt -d "password=$ADMIN_PW" -L https://kladovka.dr6ter.ru/ -o /tmp/chk-main.html -w 'code=%{http_code} size=%{size_download}\n'
for m in 'Стеллажи' 'Контейнеры' 'Вещи' 'API-ключ' 'Скачать бэкап' 'Автообновление'; do printf '%-16s %s\n' "$m" "$(grep -c "$m" /tmp/chk-main.html)"; done
echo
echo "===== 5. API actions ====="
TOKEN=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))')
echo "token: ${#TOKEN} chars"
H="Authorization: Bearer $TOKEN"
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=status -H "$H" -w ' [%{http_code}]\n' -o /tmp/chk-status.json; cat /tmp/chk-status.json; echo
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "$H" -o /tmp/chk-list.json -w 'list: [%{http_code}]\n'
python3 -c 'import json;d=json.load(open("/tmp/chk-list.json"));print("list keys:",sorted(d.keys()),"| counts:",{k:len(v) for k,v in d.items()})'
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=export -H "$H" -o /tmp/chk-export.json -w 'export: [%{http_code}]\n'
python3 -c 'import json;d=json.load(open("/tmp/chk-export.json"));print("export keys:",sorted(d.keys()))'
echo "-- save_place test --"
NEWID=$(curl "${RES[@]}" -s -X POST https://kladovka.dr6ter.ru/api.php?action=save_place -H "$H" -H 'Content-Type: application/json' -d '{"name":"__checktmp__","notes":"auto-check"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("id",0))')
echo "created id=$NEWID"
curl "${RES[@]}" -s -X POST https://kladovka.dr6ter.ru/api.php?action=delete_place -H "$H" -H 'Content-Type: application/json' -d "{\"id\":$NEWID}" -w ' [%{http_code}]\n' -o /dev/null
echo "-- bad token --"
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer wrong" -w ' [%{http_code}]\n' -o /dev/null
echo
echo "===== 6. static resources ====="
for u in favicon.svg favicon.png apple-touch-icon.png legacy/ robots.txt wp-login.php; do
  curl "${RES[@]}" -o /dev/null -w "$u: %{http_code}\n" "https://kladovka.dr6ter.ru/$u"
done
echo
echo "===== 7. ownership ====="
ls -l /www/wwwroot/kladovka.dr6ter.ru/index.php /www/wwwroot/kladovka.dr6ter.ru/api.php
echo
echo "===== 8. sqlite integrity ====="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
echo implode(" ", $db->query("PRAGMA integrity_check")->fetch(PDO::FETCH_NUM)), "\n";
foreach(["places","shelves","containers","items"] as $t){$n=$db->query("SELECT COUNT(*) FROM $t")->fetchColumn();echo "$t: $n\n";}
'
echo
echo "===== 9. build_check api.php ======"
python3 -c 'import requests' 2>/dev/null && echo "requests available" || echo "no requests module (skip)"
echo
echo "===== 10. PHP errors since deploy ====="
grep -iE 'fatal|parse error|warning|error' /www/wwwlogs/kladovka.dr6ter.ru.error.log 2>/dev/null | grep -vE '\.env|404\.html|hello-world|wp-(admin|login|json|content)' | tail -8 || echo "(no matching errors)"