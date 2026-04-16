#!/bin/bash
# Produces ./emulator/base-database.sql by concatenating the 117 per-table
# .sql files from git.krews.org/morningstar/ms4-base-database. Idempotent.
set -euo pipefail

cd "$(dirname "$0")/.."

OUT=emulator/base-database.sql
if [ -s "$OUT" ]; then
  echo "[skip] $OUT exists ($(du -h "$OUT" | cut -f1)). Delete to rebuild."
  exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "[clone] morningstar/ms4-base-database"
git clone --depth 1 -b main https://git.krews.org/morningstar/ms4-base-database.git "$TMP/db"

mkdir -p emulator
{
  echo "-- Combined Arcturus Morningstar base database"
  echo "-- Source: https://git.krews.org/morningstar/ms4-base-database (ref: main)"
  echo "-- Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "SET FOREIGN_KEY_CHECKS=0;"
  echo "SET SQL_MODE=\"NO_AUTO_VALUE_ON_ZERO\";"
  echo
  # Deterministic alphabetical order; Arcturus tables have no circular FK deps
  # that require a specific order beyond FOREIGN_KEY_CHECKS=0.
  find "$TMP/db" -maxdepth 1 -name '*.sql' -type f | sort | while read -r f; do
    echo "-- ─── $(basename "$f") ───"
    cat "$f"
    echo
  done
  echo "SET FOREIGN_KEY_CHECKS=1;"
} > "$OUT"

echo "[done] wrote $OUT  ($(du -h "$OUT" | cut -f1), $(grep -c '^CREATE TABLE' "$OUT") tables)"
echo
echo "Import at first boot:"
echo "  docker compose up -d db"
echo "  docker compose exec -T db mariadb -uroot -p\"\$DB_ROOT_PASSWORD\" \"\$DB_DATABASE\" < emulator/base-database.sql"
