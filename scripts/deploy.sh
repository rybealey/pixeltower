#!/bin/bash
# scripts/deploy.sh — idempotent, change-aware deploy on the production VPS.
# Called by .github/workflows/deploy.yml after the latest main is checked out.
# Safe to run manually: ENV_FILE=.env.production ./scripts/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.production}"
SHA_FILE=".deploy-sha"
LOCK_FILE="/tmp/pixeltower-deploy.lock"
LOG_FILE="/var/log/pixeltower-deploy.log"

# Serialize concurrent deploys (GH concurrency + manual SSH can still race).
exec 9>"$LOCK_FILE"
flock -n 9 || { echo "[deploy] another deploy in progress — exiting"; exit 1; }

# Tee all output to the log file for post-hoc inspection. Fall back to /tmp if
# /var/log isn't writable (e.g. first manual run before logrotate is configured).
if ! touch "$LOG_FILE" 2>/dev/null; then
  LOG_FILE="/tmp/pixeltower-deploy.log"
fi
exec > >(tee -a "$LOG_FILE") 2>&1
echo "────────────────────────────────────────────────────────────"
echo "[deploy] $(date -Iseconds) starting on $(hostname)"

PREV_SHA=$(cat "$SHA_FILE" 2>/dev/null || echo "")
CURR_SHA=$(git rev-parse HEAD)
echo "[deploy] prev=${PREV_SHA:-<none>} curr=$CURR_SHA"

# Classify what changed. Empty PREV_SHA or FORCE_REBUILD=1 → rebuild all.
need_nitro=0; need_atom=0; need_emu=0; need_sql=0
if [ -z "$PREV_SHA" ] || [ "${FORCE_REBUILD:-0}" = "1" ]; then
  need_nitro=1; need_atom=1; need_emu=1; need_sql=1
  echo "[deploy] full rebuild (first deploy or FORCE_REBUILD=1)"
else
  CHANGED=$(git diff --name-only "$PREV_SHA" "$CURR_SHA" || echo "")
  if [ -z "$CHANGED" ]; then
    echo "[deploy] no changes since $PREV_SHA — services will still be up -d'd for drift correction"
  fi
  echo "$CHANGED" | grep -qE '^(nitro-patches/|scripts/build-client\.sh$)'   && need_nitro=1 || true
  echo "$CHANGED" | grep -qE '^atomcms/'                                     && need_atom=1  || true
  echo "$CHANGED" | grep -qE '^(plugins/|arcturus-patches/|docker/emulator/)' && need_emu=1  || true
  echo "$CHANGED" | grep -qE '^(plugins/[^/]+/sql/|scripts/seed-db\.sh$)'    && need_sql=1  || true
  echo "[deploy] rebuild needed: nitro=$need_nitro atom=$need_atom emu=$need_emu sql=$need_sql"
fi

# Pre-deploy DB dump so a bad migration is easy to roll back. Skip if db
# container isn't running yet (first-ever deploy on a fresh box).
BACKUP_DIR="data/backups/pre-deploy"
mkdir -p "$BACKUP_DIR"
if docker compose --env-file "$ENV_FILE" ps -q db | grep -q .; then
  echo "[deploy] dumping DB to $BACKUP_DIR/$CURR_SHA.sql.gz"
  docker compose --env-file "$ENV_FILE" exec -T db sh -c \
    'mariadb-dump --single-transaction -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' \
    | gzip -c > "$BACKUP_DIR/$CURR_SHA.sql.gz"
  ls -1t "$BACKUP_DIR"/*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
else
  echo "[deploy] db container not running — skipping pre-deploy dump"
fi

# Rebuilds in dependency order: client + theme first (static assets),
# then emulator image, then container up + migrations.
if [ "$need_nitro" = 1 ]; then
  echo "[deploy] Nitro client build"
  ENV_FILE="$ENV_FILE" ./scripts/build-client.sh
fi
if [ "$need_atom" = 1 ]; then
  echo "[deploy] AtomCMS theme build"
  docker compose --env-file "$ENV_FILE" --profile tools run --rm atom-builder
fi
if [ "$need_emu" = 1 ]; then
  echo "[deploy] emulator image build"
  docker compose --env-file "$ENV_FILE" build emulator
fi

# Re-apply gamedata overrides every deploy — the scripts are idempotent and
# the whitelist/substitution configs live under gamedata-overrides/, which
# isn't covered by any rebuild-classifier above. Cheap enough to run
# unconditionally so the live FigureData.json / text files can't drift from
# the tracked override config.
#
# First normalize ownership of gamedata/gamedata/ — the converter container
# writes files as root, leaving the deploy user unable to open them for
# writing. A one-shot docker-run chown (docker defaults to running as root,
# so it can rewrite ownership on host-bind-mounted files) brings everything
# back under the deploy user so the host-side python scripts can rewrite
# the files in place.
if [ -d gamedata ] && command -v docker >/dev/null 2>&1; then
  docker run --rm --user 0:0 \
    -v "$PWD/gamedata:/gd" alpine:3 \
    sh -c "chown -R $(id -u):$(id -g) /gd && chmod -R u+rwX /gd" \
    >/dev/null 2>&1 || echo "[deploy] WARN: gamedata chown skipped"
fi

if command -v python3 >/dev/null 2>&1; then
  python3 scripts/apply-text-overrides.py || echo "[deploy] WARN: apply-text-overrides.py failed"
  python3 scripts/apply-figuredata-filter.py || echo "[deploy] WARN: apply-figuredata-filter.py failed"
else
  echo "[deploy] WARN: python3 not on host PATH — skipping gamedata overrides"
fi

# Populate gamedata/c_images/album1584/ with badge .gifs from habboassets.com.
# pull-badges.sh is idempotent — after the first catch-up run it only fetches
# newly-published badges, so running every deploy keeps the album fresh
# without re-downloading the whole pack. Fresh prod boxes that skipped the
# manual bootstrap land here with an empty album; this is what hydrates it.
# Run the badge pull with the deploy-lock fd closed (fd 9) and stdin
# detached. First-ever hydration is ~10–15 min — if SSH drops mid-pull,
# xargs children would inherit the lock fd and block every subsequent
# deploy until they finish. Closing fd 9 in the child lets future
# deploys proceed even if this pull is still running.
./scripts/pull-badges.sh 9<&- < /dev/null || echo "[deploy] WARN: pull-badges.sh failed"

echo "[deploy] up -d --remove-orphans (recreates containers for any rebuilt images)"
docker compose --env-file "$ENV_FILE" up -d --remove-orphans

# Apply DB changes once containers are up.
if [ "$need_sql" = 1 ]; then
  echo "[deploy] applying plugin SQL migrations"
  ENV_FILE="$ENV_FILE" ./scripts/seed-db.sh
fi
echo "[deploy] Laravel migrate"
docker compose --env-file "$ENV_FILE" exec -T php php artisan migrate --force

# Post-deploy health check.
echo "[deploy] health check"
healthy=0
for attempt in $(seq 1 20); do
  if curl -fsS -o /dev/null -m 5 http://localhost/ \
     && curl -fsS -o /dev/null -m 5 'http://localhost/imaging/?figure=hd-180-1&size=l'; then
    echo "[deploy] health: OK (attempt $attempt)"
    healthy=1
    break
  fi
  sleep 3
done
if [ "$healthy" != 1 ]; then
  echo "[deploy] health check FAILED after 20 attempts"
  docker compose --env-file "$ENV_FILE" ps
  exit 2
fi
docker compose --env-file "$ENV_FILE" ps

# Record the deployed SHA so the next run can compute a diff. Only written on
# a green deploy — a red run leaves the previous SHA intact so the next green
# run still diffs against the last known-good.
echo "$CURR_SHA" > "$SHA_FILE"

docker image prune -f >/dev/null
echo "[deploy] $(date -Iseconds) DONE — now on $CURR_SHA"
