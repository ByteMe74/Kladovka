#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
R=/www/wwwroot/kladovka.dr6ter.ru
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
php -l /tmp/api.php || exit 1
cp /tmp/api.php $R/api.php && cp /tmp/index.php $R/index.php
chown www:www $R/api.php $R/index.php
# прогнать schema() (миграции выполняются при первом обращении к api.php)
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d '{"password":"x"}' -o /dev/null -w 'warmup: [%{http_code}]\n'
echo "== assign existing records to Viktor (id=5) =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
foreach(["places","shelves","containers","items"] as $t){
    $db->exec("UPDATE $t SET ownerId=5 WHERE ownerId=0");
}
// подготовка тестовых юзеров: t1 (владелец), t2 (второй)
$db->exec("DELETE FROM shares");
$db->exec("DELETE FROM users WHERE username IN (\"t1\",\"t2\")");
$db->prepare("INSERT INTO users (username,email,password_hash,email_verified,created_at,ip) VALUES (?,?,?,1,?,?)")
   ->execute(["t1","t1@dr6ter.ru",password_hash("pass1111",PASSWORD_DEFAULT),1789260000000,"127.0.0.1"]);
$db->prepare("INSERT INTO users (username,email,password_hash,email_verified,created_at,ip) VALUES (?,?,?,1,?,?)")
   ->execute(["t2","t2@dr6ter.ru",password_hash("pass2222",PASSWORD_DEFAULT),1789260000000,"127.0.0.1"]);
echo "test users ready\n";
'
LOGIN(){ curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=login -H 'Content-Type: application/json' -d "$1" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("token",""))'; }
T1=$(LOGIN '{"username":"t1","password":"pass1111"}')
T2=$(LOGIN '{"username":"t2","password":"pass2222"}')
ADM=$(LOGIN "{\"password\":\"$ADMIN_PW\"}")
echo "t1 token: ${T1:0:8}…  t2: ${T2:0:8}…"
echo "== админ list: видит всё (включая С1..К01 Виктора) =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $ADM" | python3 -c '
import sys,json
d=json.load(sys.stdin)
for t in ["shelves","containers"]: print(" ",t,[(r["id"],r.get("ownerId")) for r in d[t]])
'
echo "== t1 list до записей (пусто) =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $T1" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({t:len(v) for t,v in d.items()})'
echo "== t1 создаёт контейнер и стеллаж =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=save_container -H 'Content-Type: application/json' -H "Authorization: Bearer $T1" -d '{"id":0,"name":"К10"}'
echo
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=save_shelf -H 'Content-Type: application/json' -H "Authorization: Bearer $T1" -d '{"id":0,"name":"С10"}'
echo
echo "== t2 list (не должен видеть t1) =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $T2" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({t:len(v) for t,v in d.items()})'
echo "== t1 расшаривает t2 =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=share -H 'Content-Type: application/json' -H "Authorization: Bearer $T1" -d '{"with_username":"t2"}'
echo
echo "== t2 list ДОЛЖЕН видеть К10/С10 t1 =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $T2" | python3 -c '
import sys,json
d=json.load(sys.stdin)
for t in ["shelves","containers"]: print(" ",t,[(r["id"],r.get("ownerId")) for r in d[t]])
'
echo "== t2 не может удалить запись t1? (403) =="
CID=$(curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $T1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["containers"][0]["id"])')
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=delete_container -H 'Content-Type: application/json' -H "Authorization: Bearer $T2" -d "{\"id\":$CID}" -o /tmp/del.json -w '[%{http_code}] '; cat /tmp/del.json; echo
echo "== t1 unshare =="
curl "${RES[@]}" -X POST https://kladovka.dr6ter.ru/api.php?action=unshare -H 'Content-Type: application/json' -H "Authorization: Bearer $T1" -d '{"with_username":"t2"}'
echo
echo "== t2 list снова пусто =="
curl "${RES[@]}" -s https://kladovka.dr6ter.ru/api.php?action=list -H "Authorization: Bearer $T2" | python3 -c 'import sys,json;d=json.load(sys.stdin);print({t:len(v) for t,v in d.items()})'
echo "== cleanup t1/t2 =="
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
foreach(["places","shelves","containers","items"] as $t){
    $db->exec("DELETE FROM $t WHERE ownerId IN (SELECT id FROM users WHERE username IN (\"t1\",\"t2\"))");
    $db->exec("DELETE FROM id_map WHERE owner_id IN (SELECT id FROM users WHERE username IN (\"t1\",\"t2\"))");
}
$db->exec("DELETE FROM shares");
$db->exec("DELETE FROM users WHERE username IN (\"t1\",\"t2\")");
echo "cleanup done; remaining users: ", $db->query("SELECT COUNT(*) FROM users")->fetchColumn(), "\n";
'