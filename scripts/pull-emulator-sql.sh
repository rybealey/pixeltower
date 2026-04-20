#!/bin/bash
# Clone the Arcturus Morningstar base database and concatenate every .sql
# under it into emulator/base-database.sql for one-shot import.
set -euo pipefail

cd "$(dirname "$0")/.."

REPO=https://git.krews.org/morningstar/ms4-base-database.git
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "[db] shallow-cloning $REPO"
git clone --depth 1 "$REPO" "$TMP/db"

mkdir -p emulator
OUT=emulator/base-database.sql
echo "-- Concatenated Arcturus Morningstar base DB" > "$OUT"
echo "-- Generated: $(date -u +%FT%TZ)" >> "$OUT"

count=0
while IFS= read -r -d '' f; do
  echo "" >> "$OUT"
  echo "-- ==== $(basename "$f") ====" >> "$OUT"
  cat "$f" >> "$OUT"
  count=$((count + 1))
done < <(find "$TMP/db" -type f -name '*.sql' -print0 | sort -z)

size=$(wc -c < "$OUT" | tr -d ' ')
echo "[db] wrote $OUT ($count files, $size bytes)"
