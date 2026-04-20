#!/bin/bash
# Pull c_images (badges, icons, catalogue) and sound_machine mp3s from
# habboassets.com. Idempotent — skips files that already exist.
set -euo pipefail

cd "$(dirname "$0")/.."

GAMEDATA=gamedata
mkdir -p \
  "$GAMEDATA/c_images/album1584" \
  "$GAMEDATA/c_images/catalogue" \
  "$GAMEDATA/dcr/hof_furni/icons" \
  "$GAMEDATA/dcr/hof_furni/mp3"

fetch() {
  local url="$1" out="$2"
  if [ -f "$out" ]; then return; fi
  mkdir -p "$(dirname "$out")"
  curl -fsSL --retry 3 -o "$out.tmp" "$url" && mv "$out.tmp" "$out" || {
    rm -f "$out.tmp"
    echo "[warn] failed: $url"
  }
}

echo "[assets] c_images/album1584 (badges, may take a while)…"
# habboassets.com hosts individual badge files. Fall back to shallow-clone of
# an organized mirror if available; otherwise warn and move on.
if ! [ -f "$GAMEDATA/c_images/album1584/.done" ]; then
  if git clone --depth 1 https://git.krews.org/nitro/habbo-downloader-assets.git "$GAMEDATA/.badge-src" 2>/dev/null; then
    rsync -a --ignore-existing "$GAMEDATA/.badge-src/c_images/" "$GAMEDATA/c_images/" || true
    rm -rf "$GAMEDATA/.badge-src"
  else
    echo "[info] skipping badge bulk pull; seed manually from habboassets.com/c_images/album1584/"
  fi
  touch "$GAMEDATA/c_images/album1584/.done"
fi

echo "[assets] sound_machine mp3 pack…"
if ! [ -f "$GAMEDATA/dcr/hof_furni/mp3/.done" ]; then
  # Parallel pull — 801 files indexed sequentially.
  for i in $(seq 1 801); do
    fetch "https://www.habboassets.com/web_images/sound_machine/sound_machine_sample_${i}.mp3" \
          "$GAMEDATA/dcr/hof_furni/mp3/sound_machine_sample_${i}.mp3" &
    if (( i % 20 == 0 )); then wait; fi
  done
  wait
  touch "$GAMEDATA/dcr/hof_furni/mp3/.done"
fi

echo "[done] gamedata staged in $GAMEDATA/"
