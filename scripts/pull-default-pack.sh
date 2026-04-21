#!/bin/bash
# Pull the Morningstar default SWF pack and extract:
#   - HOF furniture SWFs    → ./gamedata/dcr/hof_furni/      (input for converter)
#   - Badgeparts            → ./gamedata/c_images/Badgeparts/
#   - Reception backgrounds → ./gamedata/c_images/reception/
#   - Other c_images        → ./gamedata/c_images/<dir>/
#   - Catalog SQLs          → ./emulator/catalog-sqls/
# Shallow clone, extract, delete clone.
set -euo pipefail

cd "$(dirname "$0")/.."

REPO=https://git.krews.org/morningstar/arcturus-morningstar-default-swf-pack.git
TMP=$(mktemp -d)
trap 'echo "[pack] cleaning up temp dir"; rm -rf "$TMP"' EXIT

echo "[pack] shallow-cloning Morningstar default SWF pack (this may take a while)"
git clone --depth 1 "$REPO" "$TMP/pack"

echo "[pack] extracting HOF furniture SWFs"
mkdir -p gamedata/dcr/hof_furni
rsync -a --exclude=icons --exclude=mp3 --exclude=.DS_Store \
  "$TMP/pack/dcr/hof_furni/" gamedata/dcr/hof_furni/
swf_count=$(find gamedata/dcr/hof_furni -maxdepth 1 -name '*.swf' | wc -l | tr -d ' ')
echo "[pack] $swf_count furniture SWFs → gamedata/dcr/hof_furni/"

echo "[pack] extracting gordon/ (figure + effect + pet SWFs, ~60MB)"
mkdir -p gamedata/gordon
rsync -a --exclude=.DS_Store "$TMP/pack/gordon/" gamedata/gordon/
gordon_count=$(find gamedata/gordon -name '*.swf' | wc -l | tr -d ' ')
echo "[pack] $gordon_count gordon SWFs → gamedata/gordon/"

echo "[pack] extracting c_images subdirectories"
mkdir -p gamedata/c_images
for dir in Badgeparts reception web_promo web_promo_small Quests articles \
           guilds habbopages newroom talent targetedoffers \
           AdWarningsUK Habbo-Stories client_static hot_campaign_images_no \
           playlist catalogue_otherlangs album3606 album137; do
  if [ -d "$TMP/pack/c_images/$dir" ]; then
    rsync -a --ignore-existing "$TMP/pack/c_images/$dir/" "gamedata/c_images/$dir/"
    echo "  [+] c_images/$dir/"
  fi
done

echo "[pack] extracting gamedata xml/txt config files"
mkdir -p gamedata/gamedata
for f in external_variables.txt furnidata.xml productdata.txt \
         figuredata.xml figuremap.xml HabboAvatarActions.json \
         effectmap.xml HabboVariables.txt; do
  if [ -f "$TMP/pack/gamedata/$f" ]; then
    cp -n "$TMP/pack/gamedata/$f" "gamedata/gamedata/$f"
    echo "  [+] gamedata/gamedata/$f"
  fi
done

# external_flash_texts.txt → external_texts.txt (canonical Nitro name)
# plus an inline txt→JSON pass so Nitro's text loader has content
# immediately even before convert-swfs.sh runs. The client reads
# ExternalTexts.json merged with UITexts.json; without this, UI keys like
# navigator.createroom.title render as the literal key.
if [ -f "$TMP/pack/gamedata/external_flash_texts.txt" ]; then
  cp -f "$TMP/pack/gamedata/external_flash_texts.txt" gamedata/gamedata/external_texts.txt
  echo "  [+] gamedata/gamedata/external_texts.txt ($(wc -l < gamedata/gamedata/external_texts.txt | tr -d ' ') lines)"
  python3 -c "
import json
d = {}
with open('gamedata/gamedata/external_texts.txt', 'r', encoding='utf-8', errors='replace') as f:
    for line in f:
        line = line.rstrip('\n\r')
        if not line or line.startswith('#'): continue
        if '=' not in line: continue
        k, _, v = line.partition('=')
        d[k] = v
with open('gamedata/gamedata/ExternalTexts.json', 'w', encoding='utf-8') as f:
    json.dump(d, f, ensure_ascii=False)
print(f'  [+] gamedata/gamedata/ExternalTexts.json ({len(d)} keys)')
"
fi

echo "[pack] extracting Catalog SQLs"
mkdir -p emulator/catalog-sqls
rsync -a "$TMP/pack/Catalog-SQLS/" emulator/catalog-sqls/ 2>/dev/null || \
  rsync -a "$TMP/pack/catalog-sqls/" emulator/catalog-sqls/ 2>/dev/null || true
sql_count=$(ls emulator/catalog-sqls/*.sql 2>/dev/null | wc -l | tr -d ' ')
echo "  $sql_count catalog SQL files"

echo
echo "[done] pack extracted. Next:"
echo "  ./scripts/pull-emulator-sql.sh   (base-database)"
echo "  ./scripts/seed-db.sh             (imports base + catalog)"
echo "  ./scripts/convert-swfs.sh        (furniture/figure → .nitro)"
