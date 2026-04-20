#!/bin/bash
set -euo pipefail

: "${BACKUP_SCHEDULE:=0 4 * * *}"
: "${TZ:=UTC}"

echo "[backup] schedule: $BACKUP_SCHEDULE ($TZ)"

# Write crontab — cron runs the script under env; export vars into a file
# the script can source.
env | grep -E '^(DB_|BACKUP_|TZ)' > /etc/backup.env

cat > /etc/crontabs/root <<EOF
${BACKUP_SCHEDULE} . /etc/backup.env; /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1
EOF

mkdir -p /var/log
touch /var/log/backup.log

# foreground cron + tail the log
crond -f -L /dev/stderr &
exec tail -F /var/log/backup.log
