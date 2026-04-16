#!/bin/bash
# Fetches c_images for Pixelworld from habboassets.com bulk endpoints.
# For the two subfolders habboassets doesn't cover (Badgeparts, room_backgrounds),
# prints a TODO with habbo-downloader commands to run locally (not on the VPS).
# Idempotent: skips a subfolder if it's already populated.
set -euo pipefail

cd "$(dirname "$0")/.."

BASE=gamedata/c_images
ICONS=gamedata/dcr/hof_furni/icons

mkdir -p "$BASE/album1584" "$BASE/catalogue" \
         "$BASE/Badgeparts/generated" "$BASE/room_backgrounds" \
         "$ICONS"

need_tool() {
  command -v "$1" >/dev/null 2>&1 || { echo "missing $1" >&2; exit 1; }
}
need_tool curl
need_tool unzip

dl_and_unzip() {  # $1=url  $2=target_dir
  local url="$1" dir="$2" tmp
  local existing
  existing=$(find "$dir" -type f 2>/dev/null | head -n 1)
  if [ -n "$existing" ]; then
    echo "[skip] $dir already populated — delete it to re-fetch"
    return 0
  fi
  tmp=$(mktemp -d)
  echo "[pull] $url → $dir"
  curl -fL --progress-bar -o "$tmp/pack.zip" "$url"
  unzip -q -o "$tmp/pack.zip" -d "$dir"
  rm -rf "$tmp"
  echo "[done] $(find "$dir" -type f | wc -l | tr -d ' ') files in $dir"
}

dl_and_unzip "https://www.habboassets.com/badges/all"               "$BASE/album1584"
dl_and_unzip "https://www.habboassets.com/images/catalog-icons/all" "$BASE/catalogue"
dl_and_unzip "https://www.habboassets.com/images/furni-icons/all"   "$ICONS"

cat <<'EOT'

──────────────────────────────────────────────────────────────────
For Badgeparts, reception backgrounds, HOF furniture SWFs, and catalog
SQLs, run:  ./scripts/pull-default-pack.sh
(pulls from morningstar/arcturus-morningstar-default-swf-pack)
──────────────────────────────────────────────────────────────────
EOT
