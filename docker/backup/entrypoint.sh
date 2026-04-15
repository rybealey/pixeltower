#!/bin/bash
set -euo pipefail

: "${BACKUP_SCHEDULE:=0 4 * * *}"
: "${BACKUP_RETENTION_DAYS:=14}"
: "${TZ:=UTC}"

export BACKUP_RETENTION_DAYS DB_HOST DB_PORT DB_ROOT_PASSWORD

# Write env for cron (cron strips env by default)
{
  echo "DB_HOST=${DB_HOST}"
  echo "DB_PORT=${DB_PORT}"
  echo "DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}"
  echo "BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS}"
  echo "TZ=${TZ}"
} > /etc/environment

# Install the crontab
echo "${BACKUP_SCHEDULE} /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1" > /etc/crontabs/root

touch /var/log/backup.log
echo "[backup] schedule='${BACKUP_SCHEDULE}' retention=${BACKUP_RETENTION_DAYS}d tz=${TZ}"

# Run one immediate backup on boot if /backups is empty, so you get a sanity-check artifact.
if [ -z "$(ls -A /backups 2>/dev/null || true)" ]; then
  echo "[backup] /backups is empty — running an initial backup now"
  /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1 || echo "[backup] initial backup failed (db may still be starting); cron will retry"
fi

# Run cron in foreground, tail the log so `docker compose logs backup` is useful.
crond -f -l 8 &
tail -F /var/log/backup.log
