#!/usr/bin/env bash
# Черновая текстура чернильницы (16x16). Заменяется ручной графикой в итерации 6.
# Запуск: tools/assets/make-inkwell.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT=src/main/resources/assets/libresearch/textures/item/inkwell.png
mkdir -p "$(dirname "$OUT")"

magick -size 16x16 xc:none \
  -fill '#8A5A33' -draw 'rectangle 6,1 9,2' \
  -fill '#6E4526' -draw 'rectangle 6,3 9,3' \
  -fill '#C9C2B8' -draw 'rectangle 6,4 9,5' \
  -fill '#8F877C' -draw 'rectangle 5,6 5,7' -draw 'rectangle 10,6 10,7' \
  -fill '#C9C2B8' -draw 'rectangle 6,6 9,7' \
  -fill '#8F877C' -draw 'rectangle 4,8 11,8' \
  -fill '#C9C2B8' -draw 'rectangle 3,9 12,13' \
  -fill '#1E1B18' -draw 'rectangle 4,10 11,12' \
  -fill '#3A332C' -draw 'rectangle 4,11 11,12' \
  -fill '#EDE6DA' -draw 'rectangle 4,9 4,9' -draw 'rectangle 5,9 5,9' \
  -fill '#8F877C' -draw 'rectangle 3,14 12,14' \
  "$OUT"

echo "готово: $OUT"
