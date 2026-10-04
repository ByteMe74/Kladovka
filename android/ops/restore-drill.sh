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
BKUP=/www/server/kladovka-config/backups/latest.db.gz
STATE=/www/server/kladovka-config/backups/restore-drill.state
LOG=/www/server/kladovka-config/backups/restore-drill.log

WORK=$(mktemp -d /tmp/kladovka-drill-XXXXXX)
trap 'rm -rf "$WORK"' EXIT

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

echo "drill ok $(date +%s) items=$ITEMS places=$PLACES users=$USERS accounts='$USERS_HUMAN' orphans=$ORPHAN size=$SIZE" > "$STATE"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ok: вещей=$ITEMS мест=$PLACES пользователей=$USERS [$USERS_HUMAN] осиротевших ссылок=$ORPHAN байт=$SIZE" >> "$LOG"

# Осиротевшие ссылки не считаем поводом провалить тренировку: они могли быть
# в складе и до копии, и это вопрос данных, а не бэкапа. Но говорим о них,
# потому что молча пропустить их — значило бы сделать вид, что всё в порядке.
[ "$ORPHAN" -gt 0 ] && echo "[$(date '+%Y-%m-%d %H:%M:%S')] замечание: $ORPHAN вещей ссылаются на несуществующий контейнер" >> "$LOG"

exit 0
