#!/bin/bash
# Generate an uppercase alphanumeric beta key and wire it into AtomCMS's
# existing beta-gate (the `website_beta_codes` table checked by
# App\Rules\BetaCodeRule during registration).
#
# This also flips the `requires_beta_code` website setting to '1'
# idempotently — without it AtomCMS skips the beta-code check entirely,
# so the key wouldn't actually gate anything.
#
# A newly-registered user consumes the key (CreateNewUser sets
# website_beta_codes.user_id = $newUserId), so each key is single-use.
#
# Usage:
#   ./scripts/generate-beta-key.sh                 # prod default (.env.production)
#   ENV_FILE=.env ./scripts/generate-beta-key.sh   # local dev
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env.production}"
if [ ! -f "$ENV_FILE" ]; then
  ENV_FILE=".env"
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "[error] no env file (tried .env.production and .env)" >&2
  exit 1
fi

# Load DB_* (skip UID/GID + comments + blanks — same as seed-db.sh).
_ENVTMP=$(mktemp)
trap 'rm -f "$_ENVTMP"' EXIT
grep -Ev '^(UID|GID)=|^\s*#|^\s*$' "$ENV_FILE" > "$_ENVTMP"
set -a
. "$_ENVTMP"
set +a

: "${DB_DATABASE:?missing in $ENV_FILE}"
: "${DB_ROOT_PASSWORD:?missing in $ENV_FILE}"

# 16-char uppercase alphanumeric key. /dev/urandom on both macOS and
# Linux, tr filters to A-Z0-9.
KEY=$(LC_ALL=C tr -dc 'A-Z0-9' < /dev/urandom | head -c 16)
if [ "${#KEY}" -ne 16 ]; then
  echo "[error] failed to generate 16-char key (got '${KEY}')" >&2
  exit 1
fi

# Fed via stdin to dodge shell quoting around the password. Uses
# INSERT ... ON DUPLICATE KEY UPDATE on website_settings (the `key`
# column is UNIQUE per the AtomCMS schema) so flipping the gate is
# idempotent whether the row exists yet or not.
DC="docker compose --env-file $ENV_FILE"
$DC exec -T db sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' <<SQL
INSERT INTO website_settings (\`key\`, value, comment)
VALUES ('requires_beta_code', '1', 'Determines whether users need a beta code to register or not (0 for no & 1 for yes)')
ON DUPLICATE KEY UPDATE value = '1';

INSERT INTO website_beta_codes (code, created_at, updated_at)
VALUES ('${KEY}', NOW(), NOW());
SQL

echo "$KEY"
