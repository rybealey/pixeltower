#!/bin/bash
# Stage nitro-react's *.example configs, rewrite URLs to match our nginx
# routing (gamedata under /gamedata/, emulator WS at /ws), then run
# yarn build:prod in a one-shot container.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
_ENVTMP=$(mktemp)
trap 'rm -f "$_ENVTMP"' EXIT
grep -Ev '^(UID|GID)=|^\s*#|^\s*$' "$ENV_FILE" > "$_ENVTMP"
set -a
. "$_ENVTMP"
set +a

: "${DOMAIN:?missing in $ENV_FILE}"
: "${APP_ENV:?missing}"

if [ ! -d nitro ]; then
  echo "[error] nitro/ missing — run scripts/bootstrap-sources.sh first." >&2
  exit 1
fi

if [ "$APP_ENV" = "production" ]; then
  SCHEME=https
  WSSCHEME=wss
else
  SCHEME=http
  WSSCHEME=ws
fi

echo "[client] staging renderer-config.json + ui-config.json"
for base in renderer-config ui-config; do
  src="nitro/public/${base}.json.example"
  dst="nitro/public/${base}.json"
  if [ -f "$src" ] && [ ! -f "$dst" ]; then
    cp "$src" "$dst"
  fi
done

# Map nitro-react's upstream placeholder hosts onto our routing:
#   asset.url   : https://website.com            → $SCHEME://$DOMAIN/gamedata
#   c_images    : https://website.com/c_images/  → $SCHEME://$DOMAIN/gamedata/c_images/
#   hof_furni   : https://website.com/dcr/...    → $SCHEME://$DOMAIN/gamedata/dcr/...
#   socket.url  : wss://ws.website.com:2096      → $WSSCHEME://$DOMAIN/ws
#   ui-config url.prefix : https://website.com   → $SCHEME://$DOMAIN
rewrite_renderer() {
  local f="$1"
  sed -i.bak \
    -e "s|wss://ws.website.com:2096|$WSSCHEME://$DOMAIN/ws|g" \
    -e "s|https://website.com/c_images/|$SCHEME://$DOMAIN/gamedata/c_images/|g" \
    -e "s|https://website.com/dcr/hof_furni|$SCHEME://$DOMAIN/gamedata/dcr/hof_furni|g" \
    -e "s|https://website.com|$SCHEME://$DOMAIN/gamedata|g" \
    "$f"
  rm -f "$f.bak"
}
rewrite_ui() {
  local f="$1"
  sed -i.bak \
    -e "s|https://website.com/c_images/|$SCHEME://$DOMAIN/gamedata/c_images/|g" \
    -e "s|https://website.com|$SCHEME://$DOMAIN|g" \
    -e "s|https://camera.url|$SCHEME://$DOMAIN/camera|g" \
    "$f"
  rm -f "$f.bak"
}

echo "[client] patching renderer-config.json ($SCHEME://$DOMAIN, ws at /ws)"
[ -f nitro/public/renderer-config.json ] && rewrite_renderer nitro/public/renderer-config.json

echo "[client] patching ui-config.json"
[ -f nitro/public/ui-config.json ] && rewrite_ui nitro/public/ui-config.json

# nitro-react's vite.config.js ships without a `base`, so Vite emits
# root-absolute asset paths (/assets/...) but nginx only serves Nitro under
# /client/. Inject base: '/client/'. Idempotent — no-op if already present.
if [ -f nitro/vite.config.js ] && ! grep -q "^[[:space:]]*base:" nitro/vite.config.js; then
  echo "[client] injecting base: '/client/' into nitro/vite.config.js"
  sed -i.bak \
    -e "s|^export default defineConfig({|export default defineConfig({\\
    base: '/client/',|" \
    nitro/vite.config.js
  rm -f nitro/vite.config.js.bak
fi

# The inline NitroConfig.config.urls in nitro/index.html ships with absolute
# paths ('/renderer-config.json', '/ui-config.json'). Vite's `base` does not
# rewrite inline script bodies, so nginx would 404 those under /client/.
# Rewrite to relative so the template's `<base href="./">` resolves them
# under /client/. Idempotent — no-op if already relative.
if [ -f nitro/index.html ] && grep -q "'/renderer-config.json'" nitro/index.html; then
  echo "[client] patching nitro/index.html NitroConfig URLs to relative"
  sed -i.bak \
    -e "s|'/renderer-config.json'|'renderer-config.json'|g" \
    -e "s|'/ui-config.json'|'ui-config.json'|g" \
    nitro/index.html
  rm -f nitro/index.html.bak
fi

# Apply Nitro client patches (TSX/JSX diffs too structural for sed). Each
# patch in nitro-patches/*.patch is applied against the nitro/ checkout.
# Idempotent: `git apply -R --check` succeeds iff the patch is already
# applied, in which case we skip the forward apply.
if [ -d nitro-patches ]; then
  for patch in nitro-patches/*.patch; do
    [ -f "$patch" ] || continue
    name=$(basename "$patch")
    if (cd nitro && git apply -R --check "../$patch" 2>/dev/null); then
      echo "[client] patch already applied: $name"
    elif (cd nitro && git apply --whitespace=nowarn "../$patch" 2>&1); then
      echo "[client] applied patch: $name"
    else
      echo "[client] ERROR: patch failed to apply: $name" >&2
      exit 1
    fi
  done
fi

echo "[client] running yarn build:prod in container (takes ~10-15 min)"
docker compose --env-file "$ENV_FILE" --profile tools run --rm nitro-builder

echo "[done] nitro/dist built → served at $SCHEME://$DOMAIN/client/"
