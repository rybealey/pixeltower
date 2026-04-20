#!/bin/bash
# Stage nitro-react's *.example configs, rewrite URLs to match our nginx
# routing (gamedata under /gamedata/, emulator WS at /ws), then run
# yarn build:prod in a one-shot container.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
set -a; . "$ENV_FILE"; set +a

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

echo "[client] running yarn build:prod in container (takes ~10-15 min)"
docker compose --env-file "$ENV_FILE" --profile tools run --rm nitro-builder

echo "[done] nitro/dist built → served at $SCHEME://$DOMAIN/client/"
