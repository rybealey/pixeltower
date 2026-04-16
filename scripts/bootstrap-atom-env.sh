#!/bin/bash
# Produces ./atomcms/.env from ./atomcms/.env.example, overriding the values
# that our Docker topology dictates (DB host, Nitro paths, etc.). Preserves
# any values the user has already set in ./atomcms/.env if it exists.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f atomcms/.env.example ]; then
  echo "atomcms/.env.example not found. Clone AtomCMS first." >&2
  exit 1
fi

if [ -f atomcms/.env ]; then
  echo "atomcms/.env already exists — not overwriting. Delete it first if you want to regenerate."
  exit 0
fi

# Load root .env if present, so we can inherit DB creds + DOMAIN.
[ -f .env ] && set -a && . ./.env && set +a

: "${DOMAIN:=localhost}"
: "${APP_ENV:=local}"
: "${DB_DATABASE:=atom}"
: "${DB_USERNAME:=atom}"
: "${DB_PASSWORD:=change-me-strong}"

if [ "$APP_ENV" = "production" ]; then
  APP_URL="https://$DOMAIN"
  SCHEME="https"
  WS_SCHEME="wss"
  FORCE_HTTPS=true
else
  APP_URL="http://localhost"
  SCHEME="http"
  WS_SCHEME="ws"
  FORCE_HTTPS=false
fi

cp atomcms/.env.example atomcms/.env

# Patch DB + URLs to match our Docker topology.
sed_i() { sed -i.bak "$1" atomcms/.env && rm atomcms/.env.bak; }

sed_i "s|^APP_NAME=.*|APP_NAME=Pixelworld|"
sed_i "s|^APP_ENV=.*|APP_ENV=$APP_ENV|"
sed_i "s|^APP_DEBUG=.*|APP_DEBUG=$([ "$APP_ENV" = "production" ] && echo false || echo true)|"
sed_i "s|^APP_URL=.*|APP_URL=$APP_URL|"
sed_i "s|^DB_HOST=.*|DB_HOST=db|"
sed_i "s|^DB_DATABASE=.*|DB_DATABASE=$DB_DATABASE|"
sed_i "s|^DB_USERNAME=.*|DB_USERNAME=$DB_USERNAME|"
sed_i "s|^DB_PASSWORD=.*|DB_PASSWORD=$DB_PASSWORD|"
sed_i "s|^FORCE_HTTPS=.*|FORCE_HTTPS=$FORCE_HTTPS|"
sed_i "s|^RCON_HOST=.*|RCON_HOST=emulator|"

# Point Nitro URLs at our nginx-served paths.
sed_i "s|^NITRO_STATIC_URL=.*|NITRO_STATIC_URL=$SCHEME://$DOMAIN/gamedata|"
sed_i "s|^NITRO_CLIENT_URL=.*|NITRO_CLIENT_URL=$SCHEME://$DOMAIN/client|"
sed_i "s|^NITRO_STATIC_PATH=.*|NITRO_STATIC_PATH=/var/www/gamedata|"
sed_i "s|^NITRO_IMAGER_URL=.*|NITRO_IMAGER_URL=$SCHEME://$DOMAIN/imager|"

echo "Wrote atomcms/.env for APP_ENV=$APP_ENV DOMAIN=$DOMAIN"
echo "Next: docker compose run --rm php php artisan key:generate"
