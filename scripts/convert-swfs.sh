#!/bin/bash
# Run nitro-converter against a SWF source directory, producing .nitro
# bundles under gamedata/{bundled,figure,effect,furniture,pet}/.
# Default input: gamedata/dcr/hof_furni (populated by pull-default-pack.sh).
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
SWF_PACK_DIR="${SWF_PACK_DIR:-$PWD/gamedata/dcr/hof_furni}"

if [ ! -d "$SWF_PACK_DIR" ]; then
  echo "[error] SWF_PACK_DIR=$SWF_PACK_DIR not found." >&2
  echo "        Run ./scripts/pull-default-pack.sh first, or point SWF_PACK_DIR at your own pack." >&2
  exit 1
fi

if [ ! -d docker/converter/app ]; then
  echo "[error] docker/converter/app missing — run scripts/bootstrap-sources.sh." >&2
  exit 1
fi

echo "[convert] SWF_PACK_DIR=$SWF_PACK_DIR"

# nitro-converter hardcodes output at ./assets/bundled/<type> (its pwd).
# Swap that directory for a symlink to our gamedata/ bind-mount so files
# land where nginx serves them. Host-side operation avoids bind-mount
# permission issues inside the container.
if [ -d docker/converter/app/assets ] && [ ! -L docker/converter/app/assets ]; then
  echo "[convert] migrating pre-existing docker/converter/app/assets → gamedata/"
  cp -rn docker/converter/app/assets/. gamedata/ 2>/dev/null || true
  rm -rf docker/converter/app/assets
fi
if [ ! -L docker/converter/app/assets ]; then
  ( cd docker/converter/app && ln -sfn ../../../gamedata assets )
fi

SWF_PACK_DIR="$SWF_PACK_DIR" \
  docker compose --env-file "$ENV_FILE" --profile tools run --rm converter

echo "[done] gamedata/ populated with .nitro bundles"
