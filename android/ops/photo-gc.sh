#!/bin/bash
# Убирает фотографии, на которые больше никто не ссылается.
#
# Запуск по расписанию: 50 4 * * 0 /www/server/kladovka-config/photo-gc.sh
#
# Зачем. В базе лежат только ссылки на файлы, сами картинки — в /photos. Вещь
# удалили на телефоне, а файл на сервере остался: удалять его нечем, потому что
# то же фото может быть у другого человека (дедупликация по md5 — один файл на
# всех, у кого та же картинка). Каталог растёт и со временем забьёт диск.
#
# Почему это безопасно. Файл удаляется, только если на него не ссылается НИ ОДНА
# запись в базе — ни у одного пользователя. Проверка идёт по всем items, а не по
# одному владельцу: иначе фотография, которой поделились, исчезла бы у того, с кем
# её разделили.
#
# Скрипт идемпотентен: повторный запуск после успешного ничего не удаляет.
# По умолчанию — показать, что будет удалено, ничего не трогая. Удаление только
# с флагом --apply.
set -uo pipefail

DB=/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db
PHOTOS=/www/wwwroot/kladovka.dr6ter.ru/photos
LOG=/www/server/kladovka-config/backups/photo-gc.log
APPLY=0

for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=1 ;;
        -h|--help)
            echo "Показать осиротевшие фотографии: $0"
            echo "Удалить их:                     $0 --apply"
            exit 0
            ;;
    esac
done

[ -d "$PHOTOS" ] || { echo "нет каталога $PHOTOS"; exit 0; }
[ -f "$DB" ]    || { echo "нет базы $DB"; exit 1; }

# Все ссылки, которые реально используются. Собираем в ассоциативный массив,
# чтобы проверка была за O(1), а не перебором базы на каждый файл.
declare -A REFERENCED
while IFS= read -r ref; do
    [ -n "$ref" ] || continue
    # В базе путь вида /photos/m-<md5>.jpg, на диске лежит только имя файла.
    # Сравнивать их напрямую нельзя: тогда каждый файл выглядит осиротевшим,
    # и сборщик удаляет всё подряд — включая те, на которые ссылаются вещи.
    REFERENCED["$(basename "$ref")"]=1
done < <(sqlite3 "$DB" "SELECT DISTINCT photoPath FROM items WHERE photoPath IS NOT NULL AND photoPath <> '';" 2>/dev/null)

TOTAL_REFS=${#REFERENCED[@]}
echo "[$(date '+%Y-%m-%d %H:%M:%S')] в базе ссылок на фото: $TOTAL_REFS" >> "$LOG"

# Идём по файлам. Удаляем только те, чьё имя не встречается среди ссылок.
# Сверяем по имени файла, а не по полному пути: в базе путь вида
# /photos/m-<md5>.jpg, а на диске — просто m-<md5>.jpg.
REMOVED=0
KEPT=0
while IFS= read -r -d '' f; do
    base=$(basename "$f")
    if [ -n "${REFERENCED[$base]:-}" ]; then
        KEPT=$((KEPT + 1))
    else
        if [ "$APPLY" -eq 1 ]; then
            rm -f -- "$f" && REMOVED=$((REMOVED + 1)) && \
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] удалён осиротевший: $base" >> "$LOG"
        else
            echo "  осиротевший: $base"
        fi
    fi
done < <(find "$PHOTOS" -maxdepth 1 -type f -print0 2>/dev/null)

if [ "$APPLY" -eq 1 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] оставлено: $KEPT, удалено: $REMOVED" >> "$LOG"
    echo "Оставлено: $KEPT, удалено: $REMOVED"
else
    echo "Оставлено: $KEPT. Это показ, ничего не удалено. Для удаления: $0 --apply"
fi

exit 0