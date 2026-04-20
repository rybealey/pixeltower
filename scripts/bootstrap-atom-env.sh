#!/bin/bash
# Render atomcms/.env from atomcms/.env.example + root .env.
# Overwrites only keys we control (DB, URLs, APP_*, FORCE_HTTPS).
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "[error] $ENV_FILE missing." >&2
  exit 1
fi
set -a; . "$ENV_FILE"; set +a

if [ ! -d atomcms ]; then
  echo "[error] atomcms/ missing — run scripts/bootstrap-sources.sh first." >&2
  exit 1
fi

SRC=atomcms/.env.example
DST=atomcms/.env
if [ ! -f "$SRC" ]; then
  echo "[error] $SRC missing." >&2
  exit 1
fi

if [ -f "$DST" ]; then
  echo "[atom-env] $DST already exists — not overwriting. Delete it to re-render."
  exit 0
fi

cp "$SRC" "$DST"

set_kv() {
  local key="$1" val="$2"
  if grep -qE "^#?${key}=" "$DST"; then
    # BSD sed (macOS) + GNU sed compat: use -i.bak then remove .bak
    sed -i.bak -E "s|^#?${key}=.*|${key}=${val//|/\\|}|" "$DST"
  else
    echo "${key}=${val}" >> "$DST"
  fi
}

FORCE_HTTPS=false
if [ "${APP_ENV:-local}" = "production" ]; then
  FORCE_HTTPS=true
fi

set_kv APP_NAME               "\"${APP_NAME:-Pixeltower}\""
set_kv APP_ENV                "${APP_ENV:-local}"
set_kv APP_DEBUG              "${APP_DEBUG:-true}"
set_kv APP_URL                "${APP_URL:-http://localhost}"
set_kv FORCE_HTTPS            "$FORCE_HTTPS"

set_kv DB_CONNECTION          "mysql"
set_kv DB_HOST                "${DB_HOST:-db}"
set_kv DB_PORT                "${DB_PORT:-3306}"
set_kv DB_DATABASE            "${DB_DATABASE:?}"
set_kv DB_USERNAME            "${DB_USERNAME:?}"
set_kv DB_PASSWORD            "${DB_PASSWORD:?}"

set_kv NITRO_IMAGER_URL       "${NITRO_IMAGER_URL:?}"
set_kv NITRO_STATIC_URL       "${NITRO_STATIC_URL:?}"
set_kv NITRO_CLIENT_URL       "${NITRO_CLIENT_URL:?}"
set_kv NITRO_STATIC_PATH      "${NITRO_STATIC_PATH:-/var/www/gamedata}"

set_kv TINYMCE_API_KEY        "${TINYMCE_API_KEY:-}"
set_kv TURNSTILE_ENABLED      "${TURNSTILE_ENABLED:-false}"
set_kv TURNSTILE_SITE_KEY     "${TURNSTILE_SITE_KEY:-}"
set_kv TURNSTILE_SECRET_KEY   "${TURNSTILE_SECRET_KEY:-}"
set_kv FINDRETROS_ENABLED     "${FINDRETROS_ENABLED:-false}"
set_kv FINDRETROS_NAME        "${FINDRETROS_NAME:-pixeltower}"

rm -f "${DST}.bak"
echo "[atom-env] wrote $DST"
