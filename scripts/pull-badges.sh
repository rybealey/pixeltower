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
# Stream each page through curl | python → MANIFEST.tmp (stdout) and a
# small stderr tempfile (for the `__COUNT__ N` sentinel + any traceback).
# Holding the full 350KB page in a bash variable tripped some prod-specific
# limit; file streaming sidesteps that and also dodges ARG_MAX when curl's
# payload is handed to python.
stderr_file=$(mktemp)
trap 'rm -f "$stderr_file"' EXIT
while :; do
  : > "$stderr_file"
  set +e
  curl -fsSL --retry 3 --max-time 60 "$API?limit=$PAGE_LIMIT&offset=$offset" | \
    python3 -c "
import json, sys
d = json.load(sys.stdin)
print(f'__COUNT__ {len(d[\"badges\"])}', file=sys.stderr)
for b in d['badges']:
    code = b.get('code') or ''
    url = b.get('url_habboassets') or ''
    if code and url:
        print(f'{code}\t{url}')
" >> "$MANIFEST.tmp" 2> "$stderr_file"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then
    echo "  [warn] page offset=$offset failed (rc=$rc) — stopping manifest build" >&2
    sed 's/^/    /' "$stderr_file" >&2
    break
  fi
  last=$(awk '/^__COUNT__/ {print $2}' "$stderr_file")
  echo "  offset=$offset fetched=${last:-0}"
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
