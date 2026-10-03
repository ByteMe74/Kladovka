#!/bin/bash
R=/www/wwwroot/kladovka.dr6ter.ru
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
php -l /tmp/api.php && php -l /tmp/index.php || exit 1
cp /tmp/api.php $R/api.php && cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php
echo "== assign existing records to Viktor (id=5) =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
foreach(["places","shelves","containers","items"] as $t){
    $db->exec("UPDATE $t SET ownerId=5 WHERE ownerId=0");
    echo "$t ownerIds: "; var_export($db->query("SELECT DISTINCT ownerId FROM $t")->fetchAll(PDO::FETCH_COLUMN)); echo "\n";
}
'
echo "== vasya: видит ли он записи Виктора без доступа? =="
VT=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"Viktor","password":"WaitItsLike1"}' | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))')
echo "Viktor token len: ${#VT}"
# vasya логин
VAS=$(curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"username":"vasya","password":"Vasya!2026"}' | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))')
echo "vasya token len: ${#VAS}"
echo "-- Viktor list (должны быть С1/С2/С3/К01) --"
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $VT" | python3 -c '
import sys,json
d=json.load(sys.stdin)
for t in ["places","shelves","containers","items"]: print(t, [(r["id"],r.get("ownerId")) for r in d[t]])
'
echo "-- vasya list (пусто? он не подтвердил почту -> 403) --"
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $VAS" -o /tmp/vl.json -w '[%{http_code}] '
cat /tmp/vl.json; echo