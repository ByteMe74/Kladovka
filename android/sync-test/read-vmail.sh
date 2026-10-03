#!/bin/bash
sudo python3 - <<'PY'
import base64, os, re
d='/www/vmail/dr6ter.ru/noreply/new'
# выберем самое свежее письмо (с максимальным именем)
f=sorted(os.listdir(d))[-1]
raw=open(os.path.join(d,f),'rb').read().decode('utf-8',errors='ignore')
# блок base64 после последнего заголовка (после пустой строки перед телом)
parts=raw.split('\r\n\r\n') if '\r\n\r\n' in raw else raw.split('\n\n')
b64=parts[-1].replace('\r','').replace('\n','')
try:
    body=base64.b64decode(b64).decode('utf-8')
    print('=== письмо:', f)
    print(body)
except Exception as e:
    print('decode error:', e)
    print(raw[-500:])
PY