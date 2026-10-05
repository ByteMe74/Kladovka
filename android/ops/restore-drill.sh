#!/bin/bash
# Тренировка восстановления: бэкап действительно разворачивается?
#
# Запуск по расписанию: 40 4 * * 1 /www/server/kladovka-config/restore-drill.sh
#
# Зачем это нужно, если бэкап уже проверяется на сервере. Потому что проверка
# целостности врёт. Я на этом поймался за один вечер: `sudo sqlite3 отсутствующий_файл`
# создаёт пустую базу и отвечает `integrity_check: ok`. Восстановление выглядело
# удавшимся — а базы с данными не было. Файл бэкапа при этом был настоящим.
#
# То есть `integrity_check` доказывает целостность структуры, и только её.
# Никакой проверки файла не скажет, развернётся ли из него склад с вещами.
#
# Поэтому здесь главное не «файл не побит», а «после развёртывания в базе есть
# таблицы и в них строки». Боевая база не затрагивается: всё происходит во
# временном каталоге, который удаляется в конце.
set -uo pipefail

DB=/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db
SITE=/www/wwwroot/kladovka.dr6ter.ru
CONFIG=/www/server/kladovka-config/server-config.php
BKUP=/www/server/kladovka-config/backups/latest.db.gz
STATE=/www/server/kladovka-config/backups/restore-drill.state
LOG=/www/server/kladovka-config/backups/restore-drill.log

WORK=$(mktemp -d /tmp/kladovka-drill-XXXXXX)
PHP_PID=''
# Сервер поднимается в конце; если тренировка прервётся раньше, он не должен
# остаться висеть и держать порт.
cleanup() {
    [ -n "$PHP_PID" ] && kill "$PHP_PID" 2>/dev/null
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] FAIL: $*" >> "$LOG"
    echo "drill FAIL $*" > "$STATE"
    exit 1
}

echo "[$(date '+%Y-%m-%d %H:%M:%S')] старт: $(basename "$(readlink -f "$BKUP" 2>/dev/null || echo "$BKUP")")" >> "$LOG"

[ -e "$BKUP" ] || fail "нет файла бэкапа $BKUP"

# Распаковка. Перенаправление обязано быть ВНУТРИ sudo: в `sudo zcat > файл`
# его выполняет shell, а не sudo, и под обычным пользователем запись падает с
# «Отказано в доступе» — ровно так, как случилось при первой попытке.
sudo bash -c "gunzip -c '$BKUP' > '$WORK/drilled.db'" || fail "бэкап не распаковывается"

SIZE=$(stat -c %s "$WORK/drilled.db" 2>/dev/null || echo 0)
# Пустая база — это около 20 КБ. Всё, что меньше, данными не является.
[ "$SIZE" -gt 65536 ] || fail "после распаковки $SIZE байт — это не склад"

INTEG=$(sudo sqlite3 "$WORK/drilled.db" "PRAGMA integrity_check;" 2>/dev/null || echo "не прочиталась")
[ "$INTEG" = "ok" ] || fail "integrity_check: $INTEG"

# Главная часть: в развёрнутой базе должны быть таблицы и в них строки.
#
# Наличие таблиц проверяется отдельным запросом к sqlite_master, а не «просто
# посчитать строки». Причина практическая: счётчик выполняется в подстановке
# команды, то есть в подпроцессе, и вызов fail оттуда не останавливает основной
# скрипт — тот продолжал работу и записывал причину «нет вещей» вместо «нет
# таблицы items». Проверка при этом ловила проблему, но сообщала не ту: человек,
# читающий журнал, получал неверную причину.
for t in users places items; do
    HAVE=$(sudo sqlite3 "$WORK/drilled.db" \
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$t';" 2>/dev/null || echo 0)
    [ "$HAVE" = "1" ] || fail "в развёрнутой базе нет таблицы $t"
done

# Пустой склад не считаем успехом: тренировка должна доказать, что склад
# восстанавливается, а не что распаковалось нечто размером с файл.
USERS=$(sudo sqlite3 "$WORK/drilled.db" "SELECT COUNT(*) FROM users;" 2>/dev/null || echo 0)
ITEMS=$(sudo sqlite3 "$WORK/drilled.db" "SELECT COUNT(*) FROM items;" 2>/dev/null || echo 0)
PLACES=$(sudo sqlite3 "$WORK/drilled.db" "SELECT COUNT(*) FROM places;" 2>/dev/null || echo 0)

[ "${ITEMS:-0}" -gt 0 ] 2>/dev/null || fail "в развёрнутой базе нет ни одной вещи — склад не восстановился"
[ "${PLACES:-0}" -gt 0 ] 2>/dev/null || fail "в развёрнутой базе нет ни одного места"

# Связи не должны повиснуть: вещь, у которой контейнер и стеллаж из другой
# половины склада, — это следствие битой копии, и на живом сервере такую вещь
# человек потом ищет глазами.
ORPHAN=$(sudo sqlite3 "$WORK/drilled.db" "
  SELECT COUNT(*) FROM items i
  WHERE i.containerId IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM containers c WHERE c.id = i.containerId);" 2>/dev/null || echo 0)

USERS_HUMAN=$(sudo sqlite3 "$WORK/drilled.db" \
    "SELECT GROUP_CONCAT(username, ' ') FROM users;" 2>/dev/null || echo "?")

# Осиротевшие ссылки не считаем поводом провалить тренировку: они могли быть
# в складе и до копии, и это вопрос данных, а не бэкапа. Но говорим о них,
# потому что молча пропустить их — значило бы сделать вид, что всё в порядке.
[ "$ORPHAN" -gt 0 ] && echo "[$(date '+%Y-%m-%d %H:%M:%S')] замечание: $ORPHAN вещей ссылаются на несуществующий контейнер" >> "$LOG"

# Второе, чего не хватало тренировке: база разворачивалась, но сайт на ней не
# поднимался. Проверка «файл не побит» ничего не говорит о том, откроет ли
# склад приложение, — а именно это и интересует при восстановлении. Поэтому
# сайт копируется рядом с развёрнутой базой, поднимается на отдельном порту и
# к нему делается настоящий запрос. Боевая база не затрагивается: копия с
# отдельным конфигом, работает на 127.0.0.1, каталог удаляется в trap.
#
# Токен берём у того пользователя, который не отозван и у которого больше всего
# вещей. У остальных export вернул бы пустой список, и проверка «сайт отдал
# данные» прошла бы вхолостую.
SITE_TOKEN=$(sudo sqlite3 "$WORK/drilled.db" "
    SELECT u.token FROM users u
    WHERE u.token <> ''
      AND u.id NOT IN (SELECT user_id FROM revoked_users)
    ORDER BY (SELECT COUNT(*) FROM items i WHERE i.ownerId = u.id) DESC
    LIMIT 1;" 2>/dev/null || echo "")

[ -n "$SITE_TOKEN" ] || fail "в развёрнутой базе нет неотозванного пользователя с токеном — сайт нечем проверять"

SITE_UID=$(sudo sqlite3 "$WORK/drilled.db" "
    SELECT u.id FROM users u
    WHERE u.token = '$SITE_TOKEN' LIMIT 1;" 2>/dev/null || echo 0)
SITE_EXPECT=$(sudo sqlite3 "$WORK/drilled.db" \
    "SELECT COUNT(*) FROM items WHERE ownerId = $SITE_UID;" 2>/dev/null || echo 0)

mkdir -p "$WORK/site"
sudo cp "$SITE/api.php" "$SITE/index.php" "$WORK/site/" 2>/dev/null \
    || fail "не копируются файлы сайта — тренировку сайта проводить не на чем"

# Конфиг копируется с боевым секретом намеренно: с тем же session_secret
# токен из развёрнутой базы остаётся действительным, иначе проверялось бы
# не то. Меняется ровно один путь — на развёрнутую копию.
sudo cp "$CONFIG" "$WORK/server-config.php" 2>/dev/null \
    || fail "не копируется конфиг"
sudo sed -i "s#'db' => '[^']*'#'db' => '$WORK/drilled.db'#" "$WORK/server-config.php"

# Проверка, что путь действительно переключился на копию. Ошибка здесь удалила бы
# боевую базу через пару дней: сайт из копии пишет в базу, а путь остался бы
# боевым. Дешёвая проверка, поэтому не пропускаем.
grep -q "'db' => '$WORK/drilled.db'" "$WORK/server-config.php" \
    || fail "в конфиге копии остался боевой путь к базе — проверка прервана, чтобы не ударить по боевой базе"

PORT=$((20000 + RANDOM % 20000))
php -S "127.0.0.1:$PORT" -t "$WORK/site" > "$WORK/php.log" 2>&1 &
PHP_PID=$!
sleep 2

# Сервер мог не начать слушать: порт занят, не хватило прав, PHP упал при
# разборе конфига. Проверяем живость сразу, а не после ожидания ответа.
kill -0 "$PHP_PID" 2>/dev/null || fail "веб-сервер не запустился: $(head -2 "$WORK/php.log" 2>/dev/null | tr '\n' ' ')"

# У curl обязателен --max-time. Без него тренировка висит намертво, если сервер
# принял соединение и не ответил: cron ждёт её завершения и не запускает
# следующий час. Проверено — со сломанным `php -S` скрипт не возвращался.
curl -s --max-time 20 -o "$WORK/index.out" -w '%{http_code}' "http://127.0.0.1:$PORT/index.php" > "$WORK/index.code" 2>/dev/null
INDEX_CODE=$(cat "$WORK/index.code" 2>/dev/null || echo 000)
[ "$INDEX_CODE" = "200" ] || fail "сайт на развёрнутой базе не открылся: index.php ответил $INDEX_CODE"
grep -q 'Кладовка' "$WORK/index.out" || fail "index.php ответил, но без ожидаемого содержимого"

curl -s --max-time 20 -o "$WORK/export.json" -w '%{http_code}' \
    -H "Authorization: Bearer $SITE_TOKEN" \
    "http://127.0.0.1:$PORT/api.php?action=export" > "$WORK/export.code" 2>/dev/null
EXPORT_CODE=$(cat "$WORK/export.code" 2>/dev/null || echo 000)
[ "$EXPORT_CODE" = "200" ] || fail "API на развёрнутой базе не отдал данные: export ответил $EXPORT_CODE"

SITE_GOT=$(php -r '
    $d = json_decode(file_get_contents($argv[1]), true);
    echo (is_array($d) && isset($d["items"])) ? count($d["items"]) : -1;
' "$WORK/export.json" 2>/dev/null || echo -1)

[ "$SITE_GOT" = "$SITE_EXPECT" ] \
    || fail "API отдал $SITE_GOT вещей, ожидалось $SITE_EXPECT — склад поднялся не весь"

kill "$PHP_PID" 2>/dev/null
PHP_PID=''

echo "drill ok $(date +%s) items=$ITEMS places=$PLACES users=$USERS accounts='$USERS_HUMAN' orphans=$ORPHAN size=$SIZE site=up site_items=$SITE_GOT/$SITE_EXPECT" > "$STATE"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ok: вещей=$ITEMS мест=$PLACES пользователей=$USERS [$USERS_HUMAN] байт=$SIZE; сайт поднялся, отдал $SITE_GOT из $SITE_EXPECT вещей пользователя #$SITE_UID" >> "$LOG"

exit 0
