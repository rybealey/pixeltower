#!/bin/sh
set -eu

APP_ENV_VAL="${APP_ENV:-local}"
SRC=/etc/nginx/templates-src/default.dev.conf.template

case "$APP_ENV_VAL" in
  production) SRC=/etc/nginx/templates-src/default.prod.conf.template ;;
esac

echo "[nginx] APP_ENV=$APP_ENV_VAL → $SRC"

# Ensure cloudflare.conf exists so `include` in the prod template never fails.
# Operator refreshes it via scripts/nginx-cloudflare-refresh.sh (outside container).
if [ ! -f /etc/nginx/cloudflare.conf ]; then
  : > /etc/nginx/cloudflare.conf
fi

# Substitute only the variables we expect — leave $uri / $host etc. alone.
envsubst '${DOMAIN} ${EMU_WS_PORT}' < "$SRC" > /etc/nginx/conf.d/default.conf
