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
# Filter out UID/GID (readonly shell vars) and comments. Export line-by-line
# to handle values containing spaces (e.g. BACKUP_SCHEDULE=0 4 * * *).
if [ -f .env ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      \#*|""|UID|GID) continue ;;
    esac
    export "$key=$value" 2>/dev/null || true
  done < .env
fi

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

# Atom's .env.example defaults all c_images paths to /swf/c_images/... but
# our gamedata bind mount puts them at /c_images/... — override each.
GD_PATH=/var/www/gamedata
GD_URL=$SCHEME://$DOMAIN/gamedata
sed_i "s|^NITRO_BADGE_PATH=.*|NITRO_BADGE_PATH=$GD_PATH/c_images/album1584|"
sed_i "s|^NITRO_BADGE_URL=.*|NITRO_BADGE_URL=$GD_URL/c_images/album1584|"
sed_i "s|^NITRO_GROUP_BADGE_PATH=.*|NITRO_GROUP_BADGE_PATH=$GD_PATH/c_images/Badgeparts/generated|"
sed_i "s|^NITRO_GROUP_BADGE_URL=.*|NITRO_GROUP_BADGE_URL=$GD_URL/c_images/Badgeparts/generated|"
sed_i "s|^NITRO_CATALOG_IMAGE_PATH=.*|NITRO_CATALOG_IMAGE_PATH=$GD_PATH/c_images/catalogue|"
sed_i "s|^NITRO_CATALOG_IMAGE_URL=.*|NITRO_CATALOG_IMAGE_URL=$GD_URL/c_images/catalogue|"
sed_i "s|^NITRO_BACKGROUND_PATH=.*|NITRO_BACKGROUND_PATH=$GD_PATH/c_images/room_backgrounds|"
sed_i "s|^NITRO_BACKGROUND_URL=.*|NITRO_BACKGROUND_URL=$GD_URL/c_images/room_backgrounds|"
sed_i "s|^NITRO_FURNITURE_ICON_PATH=.*|NITRO_FURNITURE_ICON_PATH=$GD_PATH/dcr/hof_furni/icons|"
sed_i "s|^NITRO_FURNITURE_ICON_URL=.*|NITRO_FURNITURE_ICON_URL=$GD_URL/dcr/hof_furni/icons|"

echo "Wrote atomcms/.env for APP_ENV=$APP_ENV DOMAIN=$DOMAIN"
echo "Next: docker compose run --rm php php artisan key:generate"
