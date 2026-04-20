#!/bin/bash
set -euo pipefail

: "${DB_HOST:=db}"
: "${DB_PORT:=3306}"
: "${DB_ROOT_PASSWORD:?missing}"
: "${BACKUP_RETENTION_DAYS:=14}"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
out="/backups/pixeltower-${stamp}.sql.gz"

echo "[backup] dumping → $out"
mariadb-dump \
  --host="$DB_HOST" --port="$DB_PORT" \
  --user=root --password="$DB_ROOT_PASSWORD" \
  --all-databases --single-transaction --quick --routines --triggers \
  | gzip -9 > "$out"

echo "[backup] pruning older than ${BACKUP_RETENTION_DAYS}d"
find /backups -name 'pixeltower-*.sql.gz' -type f -mtime +"$BACKUP_RETENTION_DAYS" -delete

echo "[backup] done"
