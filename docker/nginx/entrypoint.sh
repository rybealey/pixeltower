#!/bin/sh
# Selects dev or prod nginx config based on APP_ENV and renders env vars into it.
set -eu

APP_ENV="${APP_ENV:-local}"
EMU_WS_PORT="${EMU_WS_PORT:-2095}"
DOMAIN="${DOMAIN:-localhost}"

case "$APP_ENV" in
  production) SRC=/etc/nginx/templates-src/default.prod.conf.template ;;
  *)          SRC=/etc/nginx/templates-src/default.dev.conf.template  ;;
esac

mkdir -p /etc/nginx/conf.d
# Remove any templated default.conf that the nginx image ships/generates
rm -f /etc/nginx/conf.d/default.conf

export DOMAIN EMU_WS_PORT
envsubst '${DOMAIN} ${EMU_WS_PORT}' < "$SRC" > /etc/nginx/conf.d/default.conf

echo "[entrypoint] APP_ENV=$APP_ENV DOMAIN=$DOMAIN EMU_WS_PORT=$EMU_WS_PORT"
