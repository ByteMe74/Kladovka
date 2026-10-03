#!/bin/bash
ADMIN_PW="${KLADOVKA_ADMIN_PW:?KLADOVKA_ADMIN_PW is not set - export it before running this script}"
TOKEN=$(curl -sk -X POST "https://kladovka.dr6ter.ru/api.php?action=login" --resolve kladovka.dr6ter.ru:443:127.0.0.1 -H 'Content-Type: application/json' -d "{\"password\":\"$ADMIN_PW\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -sk -X POST "https://kladovka.dr6ter.ru/api.php?action=delete_place" --resolve kladovka.dr6ter.ru:443:127.0.0.1 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"id":5}'
curl -sk "https://kladovka.dr6ter.ru/api.php?action=list" --resolve kladovka.dr6ter.ru:443:127.0.0.1 -H "Authorization: Bearer $TOKEN" | python3 -c '
import sys,json
d=json.load(sys.stdin)
print({t:len(d[t]) for t in d})
for t in d:
    for r in d[t]:
        print(t, r.get("id"), repr(r.get("name")), "createdAt=", r.get("createdAt"))
'