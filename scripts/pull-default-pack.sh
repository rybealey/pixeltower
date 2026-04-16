#!/bin/bash
# Pulls the Morningstar default SWF pack and extracts:
#   - HOF furniture SWFs → ./gamedata/dcr/hof_furni/ (for converter re-run)
#   - Badgeparts → ./gamedata/c_images/Badgeparts/
#   - Reception backgrounds → ./gamedata/c_images/reception/
#   - Remaining c_images subdirs we're missing
#   - Catalog SQLs → ./emulator/catalog-sqls/
# Shallow clone (~1-5GB), extracted assets only, clone deleted after.
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
echo "[pack] $swf_count furniture SWFs in gamedata/dcr/hof_furni/"

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

echo "[pack] extracting Catalog SQLs"
mkdir -p emulator/catalog-sqls
rsync -a "$TMP/pack/Catalog-SQLS/" emulator/catalog-sqls/
echo "  $(ls emulator/catalog-sqls/*.sql 2>/dev/null | wc -l | tr -d ' ') SQL files"

echo
echo "[done] Assets extracted. Next steps:"
echo "  1. Re-run the converter to produce furniture .nitro bundles:"
echo "     Merge hof_furni SWFs with your SWF pack, then:"
echo "     SWF_PACK_DIR=<merged_path> docker compose --profile tools run --rm converter"
echo "  2. Import catalog SQLs after base-database.sql:"
echo "     for f in emulator/catalog-sqls/*.sql; do"
echo "       docker compose exec -T db mariadb -uroot -p\"\$DB_ROOT_PASSWORD\" \"\$DB_DATABASE\" < \"\$f\""
echo "     done"
