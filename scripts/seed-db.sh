#!/bin/bash
# Seed the database: base schema + catalog SQLs + NitroWebsockets whitelist +
# console.mode off. Requires `docker compose up -d db` to have succeeded.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
if [ ! -f "$ENV_FILE" ]; then
  echo "[error] $ENV_FILE missing. Copy .env.example and edit it first." >&2
  exit 1
fi
set -a; . "$ENV_FILE"; set +a

: "${DB_DATABASE:?missing in $ENV_FILE}"
: "${DB_ROOT_PASSWORD:?missing in $ENV_FILE}"
: "${DOMAIN:?missing in $ENV_FILE}"

DC="docker compose --env-file $ENV_FILE"
mariadb_exec() {
  $DC exec -T db sh -c "exec mariadb -uroot -p\"\$MARIADB_ROOT_PASSWORD\" \"\$MARIADB_DATABASE\""
}

BASE=emulator/base-database.sql
if [ ! -f "$BASE" ]; then
  echo "[error] $BASE missing — run scripts/pull-emulator-sql.sh first." >&2
  exit 1
fi

echo "[seed] waiting for db to accept connections…"
for i in $(seq 1 30); do
  if $DC exec -T db sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" -e "SELECT 1" "$MARIADB_DATABASE"' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[seed] importing $BASE"
mariadb_exec < "$BASE"

if ls emulator/catalog-sqls/*.sql >/dev/null 2>&1; then
  for f in emulator/catalog-sqls/*.sql; do
    echo "[seed] catalog: $(basename "$f")"
    mariadb_exec < "$f" || echo "  [warn] import had errors (continuing)"
  done
else
  echo "[seed] no catalog SQLs found (skipping) — run pull-default-pack.sh if needed"
fi

echo "[seed] applying NitroWebsockets whitelist (DOMAIN=$DOMAIN)"
DOMAIN="$DOMAIN" envsubst '${DOMAIN}' < emulator/nitrowebsockets-settings.sql | mariadb_exec

echo "[seed] disabling console.mode"
echo "UPDATE emulator_settings SET \`value\`='0' WHERE \`key\`='console.mode';" | mariadb_exec

echo "[done] database seeded"
