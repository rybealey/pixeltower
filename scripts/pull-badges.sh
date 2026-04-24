#!/bin/bash
# Pull every badge .gif from habboassets.com into gamedata/c_images/album1584/.
# Paginates https://www.habboassets.com/api/v1/badges (limit 1000) to build a
# manifest of <code> <url> pairs, then downloads in parallel. Idempotent —
# skips files that already exist, so reruns only fetch newly-published badges.
set -euo pipefail

cd "$(dirname "$0")/.."

DEST=gamedata/c_images/album1584
MANIFEST=gamedata/.badges.manifest
API=https://www.habboassets.com/api/v1/badges
PAGE_LIMIT=1000
PARALLEL=8

mkdir -p "$DEST"

echo "[badges] building manifest via $API"
: > "$MANIFEST.tmp"
offset=0
while :; do
  page=$(curl -fsSL --retry 3 --max-time 60 "$API?limit=$PAGE_LIMIT&offset=$offset")
  # Python prints `__COUNT__ N` as the first stdout line, then one tab-
  # separated manifest row per badge. Real errors stay on stderr and
  # surface in the deploy log instead of being swallowed by a redirect.
  result=$(python3 -c "
import json, sys
d = json.loads(sys.argv[1])
print(f'__COUNT__ {len(d[\"badges\"])}')
for b in d['badges']:
    code = b.get('code') or ''
    url = b.get('url_habboassets') or ''
    if code and url:
        print(f'{code}\t{url}')
" "$page")
  last=$(printf '%s\n' "$result" | head -n1 | awk '{print $2}')
  printf '%s\n' "$result" | tail -n +2 >> "$MANIFEST.tmp"
  echo "  offset=$offset fetched=$last"
  [ "${last:-0}" -lt "$PAGE_LIMIT" ] && break
  offset=$((offset + PAGE_LIMIT))
done
# Dedupe on code (first occurrence wins — API returns newest first).
awk -F'\t' '!seen[$1]++' "$MANIFEST.tmp" > "$MANIFEST"
rm -f "$MANIFEST.tmp"

total=$(wc -l < "$MANIFEST" | tr -d ' ')
echo "[badges] manifest: $total unique badges"

# Filter to ones we don't already have, then download in parallel with xargs.
missing=$(awk -F'\t' -v dest="$DEST" '{ if (system("test -f " dest "/" $1 ".gif") != 0) print }' "$MANIFEST")
miss_count=$(printf '%s\n' "$missing" | grep -c . || true)
echo "[badges] missing: $miss_count / $total"

if [ "$miss_count" -gt 0 ]; then
  # Emit "<url> <path>" pairs space-separated (urls/paths contain no spaces).
  printf '%s\n' "$missing" | \
    awk -F'\t' -v dest="$DEST" '{ printf "%s %s/%s.gif\n", $2, dest, $1 }' | \
    xargs -P "$PARALLEL" -n2 bash -c '
      url="$1"; out="$2"
      curl -fsSL --retry 3 --max-time 30 -o "$out.tmp" "$url" \
        && mv "$out.tmp" "$out" \
        || { rm -f "$out.tmp"; echo "[warn] failed: $url" >&2; }
    ' _
fi

have=$(find "$DEST" -name '*.gif' | wc -l | tr -d ' ')
echo "[done] $have badge files in $DEST"
