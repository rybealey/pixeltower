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
# Load .env skipping UID/GID (bash-readonly) and comment/blank lines.
# Using a tempfile instead of `<(...)` — process substitution + `set -a`
# behaves unreliably in macOS's default bash 3.2.
_ENVTMP=$(mktemp)
trap 'rm -f "$_ENVTMP"' EXIT
grep -Ev '^(UID|GID)=|^\s*#|^\s*$' "$ENV_FILE" > "$_ENVTMP"
set -a
. "$_ENVTMP"
set +a

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

# Re-importing base-database.sql on top of an already-migrated DB clobbers
# columns that AtomCMS's `artisan migrate` added to base tables (users,
# camera_web, etc.). Laravel still records those migrations as "run" so
# it won't re-apply them — leaving the DB in a broken phantom state.
# Guard: only import the base dump if the habbo.migrations table is absent
# or empty, or if the operator explicitly forces it via FORCE_RESEED_BASE=1.
mig_rows=$($DC exec -T db sh -c \
  'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" -N -B \
   -e "SELECT COUNT(*) FROM migrations" 2>/dev/null' \
  | tr -d '[:space:]' || true)
if [ -n "$mig_rows" ] && [ "$mig_rows" -gt 0 ] 2>/dev/null && [ "${FORCE_RESEED_BASE:-0}" != "1" ]; then
  echo "[seed] migrations table has $mig_rows rows — skipping base-database.sql re-import"
  echo "[seed]   (set FORCE_RESEED_BASE=1 to force. Re-importing will clobber atomcms"
  echo "[seed]    migration DDL; you'll then need to DELETE those rows from migrations"
  echo "[seed]    and re-run 'php artisan migrate --force'.)"
else
  echo "[seed] importing $BASE"
  mariadb_exec < "$BASE"
fi

# The ms4-base-database dump is a snapshot that predates some tables the
# Arcturus dev branch expects. Apply the IF-NOT-EXISTS patch afterwards so
# chat_bubbles, catalog_items_limited, calendar_rewards exist before login.
if [ -f emulator/missing-tables.sql ]; then
  echo "[seed] applying missing-tables patch (dev-branch schema gaps)"
  mariadb_exec < emulator/missing-tables.sql
fi

# Apply Pixeltower plugin migrations in filename order (V001, V002, ...).
# Each plugin owns its own sql/ dir; everything is CREATE TABLE IF NOT EXISTS
# / INSERT IGNORE so re-runs are idempotent.
for plugin_sql_dir in plugins/*/sql; do
  [ -d "$plugin_sql_dir" ] || continue
  for f in "$plugin_sql_dir"/*.sql; do
    [ -f "$f" ] || continue
    echo "[seed] plugin migration: $f"
    mariadb_exec < "$f"
  done
done

if ls emulator/catalog-sqls/*.sql >/dev/null 2>&1; then
  for f in emulator/catalog-sqls/*.sql; do
    echo "[seed] catalog: $(basename "$f")"
    mariadb_exec < "$f" || echo "  [warn] import had errors (continuing)"
  done
else
  echo "[seed] no catalog SQLs found (skipping) — run pull-default-pack.sh if needed"
fi

# emulator/catalog-sqls/catalog_pages.sql DROP+CREATEs catalog_pages on
# every deploy from the Morningstar pack, wiping any custom row inserted
# by a plugin V*.sql migration. Apply our tracked custom pages file now,
# after the upstream import — same "post-catalog fixup" pattern as the
# items_base re-bind below.
if [ -f emulator/pixeltower-custom-catalog.sql ]; then
  echo "[seed] applying pixeltower custom catalog pages"
  mariadb_exec < emulator/pixeltower-custom-catalog.sql
fi

# emulator/catalog-sqls/items_base.sql DROPs + recreates the table, clobbering
# any custom interaction_type values. Re-bind every items_base row referenced
# by rp_functional_furniture back to 'rp_functional' so walk-on / click
# handlers fire post-boot. V007 creates rp_functional_furniture earlier in
# this script, so the JOIN is always safe.
echo "[seed] re-binding rp_functional interaction_type overrides"
mariadb_exec <<'SQL'
UPDATE items_base ib
JOIN rp_functional_furniture rf ON rf.item_base_id = ib.id AND rf.enabled = 1
SET ib.interaction_type = 'rp_functional';
SQL

echo "[seed] applying NitroWebsockets whitelist (DOMAIN=$DOMAIN)"
sed "s|\${DOMAIN}|$DOMAIN|g" emulator/nitrowebsockets-settings.sql | mariadb_exec

echo "[seed] disabling console.mode"
echo "UPDATE emulator_settings SET \`value\`='0' WHERE \`key\`='console.mode';" | mariadb_exec

echo "[seed] setting default home room to 57"
mariadb_exec <<'SQL'
UPDATE emulator_settings SET `value`='57' WHERE `key`='hotel.home.room';
UPDATE website_settings  SET `value`='57' WHERE `key`='hotel_home_room';
SQL

echo "[seed] rebranding welcome alerts: Habbo Hotel -> PixelRP"
mariadb_exec <<'SQL'
UPDATE emulator_settings SET `value`='PixelRP'                    WHERE `key`='hotel.name';
UPDATE emulator_settings SET `value`='Welcome to PixelRP %user%!' WHERE `key`='hotel.welcome.alert.message';
UPDATE emulator_texts    SET `value`='Welcome to PixelRP %user%'  WHERE `key`='hotel.alert.message.welcome';
SQL

echo "[done] database seeded"
