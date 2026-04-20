#!/bin/bash
# Patch nitro-react's runtime configs with DOMAIN + protocol from .env, then
# run yarn install + yarn build:prod in a one-shot container.
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

echo "[client] patching nitro/public/*.json (DOMAIN=$DOMAIN, $SCHEME/$WSSCHEME)"
for f in nitro/public/renderer-config.json nitro/public/ui-config.json; do
  if [ -f "$f" ]; then
    # Replace the upstream placeholders.
    sed -i.bak \
      -e "s|###YOUR DOMAIN###|$DOMAIN|g" \
      -e "s|http://###YOUR DOMAIN###|$SCHEME://$DOMAIN|g" \
      -e "s|ws://###YOUR DOMAIN###|$WSSCHEME://$DOMAIN|g" \
      "$f"
    rm -f "$f.bak"
  fi
done

echo "[client] running yarn build:prod in container"
docker compose --env-file "$ENV_FILE" --profile tools run --rm nitro-builder

echo "[done] nitro/dist built"
