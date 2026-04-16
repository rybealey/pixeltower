#!/bin/bash
# Pulls sound_machine_sample_0..800 from habboassets.com/sounds into the
# Nitro-expected path. Idempotent: skips files already present.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT=gamedata/dcr/hof_furni/mp3
mkdir -p "$OUT"

echo "[sounds] downloading to $OUT (8 parallel)"
seq 1 801 | xargs -P 8 -I {} sh -c '
  id={}
  n=$((id-1))
  f="'"$OUT"'/sound_machine_sample_${n}.mp3"
  [ -s "$f" ] && exit 0
  curl -sSfL -o "$f" "https://www.habboassets.com/sounds/${id}" || rm -f "$f"
'

# Cull any zero-byte files that slipped through
find "$OUT" -maxdepth 1 -name '*.mp3' -size 0 -delete

count=$(find "$OUT" -maxdepth 1 -name '*.mp3' | wc -l | tr -d ' ')
size=$(du -sh "$OUT" | cut -f1)
echo "[sounds] $count files, $size"
