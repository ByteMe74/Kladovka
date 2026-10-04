#!/bin/bash
# Кладовка: проверка живости сайта, базы и свежести бэкапов.
#
# Запуск по крону: 0 * * * * /www/backup/check-kladovka.sh
#
# Прежняя версия делала ровно одно: curl главной страницы и запись кода ответа.
# На этом она оставалась зелёной, пока база пять дней не копировалась, потому
# что лендинг от копий не зависит. Замер лога показывал, что монитор тоже молчал
# с 28 сентября: у скрипта не было бита исполнения, cron не мог его запустить,
# а почты на этой машине нет.
#
# Теперь проверок четыре, и три из них ловят то, что раньше проскакивало:
#   1. сайт отвечает 200;
#   2. api.php отвечает на публичное действие (значит PHP и база живы);
#   3. бэкап базы свежий (не старше 26 часов при ежедневном расписании);
#   4. последний бэкап открывается и содержит таблицу items.
#
# Писать отчёт только при смене состояния. Иначе получится то же, из-за чего
# всё это чинится: в логе тысячи строк «OK», между которыми не видно ничего.
set -uo pipefail

URL="https://kladovka.dr6ter.ru"
LOG="/www/backup/kladovka-monitor.log"
STATE="/www/backup/kladovka-monitor.state"
BKUP="/www/server/kladovka-config/backups/latest.db.gz"
PHOTOS_BKUP="/www/server/kladovka-config/backups/latest-photos.tar.gz"
DB="/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db"
MAX_AGE_HOURS=26   # копия делается в 3:30, значит между 3:30 и 5:30 следующего
                  # дня разница до 26 часов

mkdir -p "$(dirname "$LOG")"
touch "$LOG"

problems=()

# 1. Сайт жив
CODE=$(curl -s -o /dev/null -w "%{http_code}" -k --max-time 20 "$URL/" 2>/dev/null)
[ -n "$CODE" ] || CODE="000"      # curl не отработал — код вывода может быть пустым
[ "$CODE" = "200" ] || problems+=("сайт вернул $CODE")

# 2. PHP и база живы: публичное действие, токен не нужен
API=$(curl -s -o /dev/null -w "%{http_code}" -k --max-time 20 \
      "$URL/api.php?action=latestApk" 2>/dev/null)
[ -n "$API" ] || API="000"
[ "$API" = "200" ] || problems+=("api.php вернул $API")

# 3. Бэкап свежий. Проверяем по ссылке latest.db.gz, а не по наличию любых
#    файлов: старые копии в каталоге остаются месяц, и по ним «свежесть» не
#    определить.
if [ ! -e "$BKUP" ]; then
    problems+=("бэкапа нет: $BKUP")
else
    # -L обязателен: $BKUP — симлинк на latest.db.gz, и без -L stat берёт время
    # САМОЙ ссылки, а не копии. Ссылку переставляет скрипт бэкапа, поэтому такая
    # проверка всегда видела бы «только что» и пропускала бы зависшие копии.
    BKUP_MTIME=$(stat -L -c %Y "$BKUP" 2>/dev/null || echo 0)
    AGE_H=$(( ( $(date +%s) - "$BKUP_MTIME" ) / 3600 ))
    if [ "$AGE_H" -gt "$MAX_AGE_HOURS" ]; then
        problems+=("бэкап старый: ${AGE_H} ч. (порог ${MAX_AGE_HOURS})")
    fi
    # 4. Копия открывается и в ней есть что восстанавливать
    TMP=$(mktemp /tmp/kladovka-monitor-XXXXXX.db)
    if zcat "$BKUP" > "$TMP" 2>/dev/null; then
        INTEG=$(sqlite3 "$TMP" "PRAGMA integrity_check;" 2>/dev/null | head -1)
        if [ "$INTEG" != "ok" ]; then
            # Пустой integrity_check означает, что файл не база вовсе: SQLite
            # не смогла его прочитать. Без этой развилки сообщение получалось бы
            # вида "integrity_check= в бэкапе нет таблицы items" — из двух
            # неисправностей одна склеивалась с другой в нечитаемую фразу.
            [ -z "$INTEG" ] && INTEG="файл не читается как база"
            problems+=("бэкап битый: $INTEG")
        fi
        ITEMS=$(sqlite3 "$TMP" "SELECT COUNT(*) FROM items;" 2>/dev/null || echo "")
        # Отдельная проверка таблицы, а не «?» в тексте ошибки: пустой результат
        # и «нет таблицы» — разные вещи, и в сообщении они должны читаться
        # раздельно, а не слитно.
        if [ "$INTEG" = "ok" ] && [ -z "$ITEMS" ]; then
            problems+=("в бэкапе нет таблицы items")
        fi
    else
        problems+=("бэкап не распаковывается")
    fi
    rm -f "$TMP"
fi

# 5. Фотографии. Проверять надо не «есть ли файлы в /photos», а «есть ли в БАЗЕ
#    вещи со ссылкой на фото»: если такие вещи есть, а архива нет — данные
#    восстановятся без картинок, и человек узнает об этом только потом.
#    Пока фотографий нет вовсе, лишней тревоги не поднимаем.
NEED_PHOTOS=$(sqlite3 "$DB" "SELECT COUNT(*) FROM items WHERE photoPath IS NOT NULL AND photoPath <> '';" 2>/dev/null || echo "?")
if [ "$NEED_PHOTOS" != "?" ] && [ "${NEED_PHOTOS:-0}" -gt 0 ]; then
    if [ ! -e "$PHOTOS_BKUP" ]; then
        problems+=("в базе $NEED_PHOTOS вещей с фото, а архива фотографий нет")
    else
        # Проверяем НЕ код возврата tar, а сам список: на испорченном архиве
        # tar не ругается, он молча выдаёт пустоту и возвращает 0. Ошибка
        # возврата ловилась бы только на совсем нечитаемом файле.
        P_LISTED=$(tar -tzf "$PHOTOS_BKUP" 2>/dev/null | grep -c "^photos/[^/][^/]*$" || true)
        if [ "${P_LISTED:-0}" -eq 0 ]; then
            problems+=("архив фотографий пуст или не открывается")
        fi
    fi
fi

# Отчёт только при смене состояния: иначе в логе тонна одинаковых строк,
# и замер сигнала теряется ровно так же, как сейчас.
if [ ${#problems[@]} -eq 0 ]; then
    NOW="OK: сайт $CODE, api $API, бэкап свежий"
    PREV=$(cat "$STATE" 2>/dev/null || echo "")
    if [ "$PREV" != "$NOW" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] $NOW" >> "$LOG"
        echo "$NOW" > "$STATE"
    fi
else
    NOW="ALERT: ${problems[*]}"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $NOW" >> "$LOG"
    echo "$NOW" > "$STATE"
fi

# Лог не должен расти безгранично
tail -500 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"