#!/bin/bash
D=/www/wwwroot/kladovka.dr6ter.ru/wp-content/db
ls -la $D/ | head -30
echo "=== README (install section) ==="
grep -n -i -A 12 'usage\|install\|getting started\|quick start' $D/README.md | head -80