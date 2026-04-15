#!/bin/bash
set -euo pipefail

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="/backups/db-${STAMP}.sql.gz"

echo "[$(date -Is)] dumping all databases -> ${OUT}"
mariadb-dump \
  -h "${DB_HOST}" -P "${DB_PORT}" \
  -u root -p"${DB_ROOT_PASSWORD}" \
  --all-databases --single-transaction --quick --routines --triggers --events \
  | gzip -9 > "${OUT}.tmp"
mv "${OUT}.tmp" "${OUT}"
echo "[$(date -Is)] wrote $(du -h "${OUT}" | cut -f1)"

# Retention
find /backups -maxdepth 1 -type f -name 'db-*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS}" -print -delete \
  | sed 's/^/[retention] pruned /'

echo "[$(date -Is)] done"
