#!/bin/bash
RES=(--resolve kladovka.dr6ter.ru:443:127.0.0.1 -sk)
echo "== GET register + captcha question =="
curl "${RES[@]}" --compressed -b /tmp/cj.txt -c /tmp/cj.txt 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rg1.html -w 'HTTP %{http_code}\n'
Q=$(grep -o 'Сколько будет [0-9]* + [0-9]*' /tmp/rg1.html | head -1 | sed 's/Сколько будет //')
echo "question: $Q"
A=$(python3 -c "print(eval('$Q'.replace('×','*').replace('−','-')))")
echo "answer: $A"
# wrong captcha first
echo "== wrong captcha =="
curl "${RES[@]}" --compressed -b /tmp/cj.txt -c /tmp/cj.txt --data-urlencode "reg_username=test_client" --data-urlencode "reg_password=secret123" --data-urlencode "reg_password2=secret123" --data-urlencode "reg_captcha=999" 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rg2.html -w 'HTTP %{http_code}\n'
grep -o 'Неверный ответ на контрольный вопрос' /tmp/rg2.html | head -1
# now with correct captcha (need fresh question from rg2 render — but form re-renders; get from rg2)
Q2=$(grep -o 'Сколько будет [0-9]* + [0-9]*' /tmp/rg2.html | head -1 | sed 's/Сколько будет //')
A2=$(python3 -c "print(eval('$Q2'.replace('×','*').replace('−','-')))")
echo "== correct captcha (q=$Q2) =="
curl "${RES[@]}" --compressed -b /tmp/cj.txt -c /tmp/cj.txt --data-urlencode "reg_username=test_client" --data-urlencode "reg_password=secret123" --data-urlencode "reg_password2=secret123" --data-urlencode "reg_captcha=$A2" 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rg3.html -w 'HTTP %{http_code}\n'
grep -o 'Аккаунт создан, вы вошли\|Кладовка\|Стеллажи\|Скачать бэкап' /tmp/rg3.html | sort | uniq -c | head
echo "== duplicate username =="
curl "${RES[@]}" --compressed -b /tmp/cj.txt -c /tmp/cj.txt 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rg4.html
Q3=$(grep -o 'Сколько будет [0-9]* + [0-9]*' /tmp/rg4.html | head -1 | sed 's/Сколько будет //')
A3=$(python3 -c "print(eval('$Q3'.replace('×','*').replace('−','-')))")
curl "${RES[@]}" --compressed -b /tmp/cj.txt -c /tmp/cj.txt --data-urlencode "reg_username=test_client" --data-urlencode "reg_password=another1" --data-urlencode "reg_password2=another1" --data-urlencode "reg_captcha=$A3" 'https://kladovka.dr6ter.ru/?view=register' -o /tmp/rg5.html -w 'HTTP %{http_code}\n'
grep -o 'уже занято' /tmp/rg5.html | head -1
echo "== login as registered user =="
curl "${RES[@]}" --compressed -c /tmp/ulj.txt --data-urlencode "username=test_client" --data-urlencode "password=secret123" 'https://kladovka.dr6ter.ru/' -o /tmp/ul.html -w 'HTTP %{http_code} %{size_download}b\n'
grep -o 'test_client\|Стеллажи\|Скачать бэкап' /tmp/ul.html | sort | uniq -c | head