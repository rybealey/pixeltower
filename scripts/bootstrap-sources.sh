#!/bin/bash
# Clones all third-party source trees the stack depends on. Safe to re-run:
# it skips directories that already exist. Delete a dir first to pull fresh.
set -euo pipefail

cd "$(dirname "$0")/.."

clone_if_missing() {
  local url="$1" dest="$2"
  if [ -d "$dest/.git" ] || [ -d "$dest" ]; then
    echo "[skip] $dest already present — delete it to re-clone"
    return
  fi
  echo "[clone] $url → $dest"
  git clone --depth 1 "$url" "$dest"
}

clone_if_missing https://github.com/atom-retros/atomcms         atomcms
clone_if_missing https://github.com/billsonnn/nitro-react       nitro
clone_if_missing https://github.com/billsonnn/nitro-converter   docker/converter/app

# Imager lives inside duckietm's tutorial repo — grab just that subtree.
if [ ! -d docker/imager/app ]; then
  TMP=$(mktemp -d)
  echo "[clone] duckietm/Complete-Retro-on-Ubuntu (for imager)"
  git clone --depth 1 https://github.com/duckietm/Complete-Retro-on-Ubuntu "$TMP"
  cp -r "$TMP/Docker/imager" docker/imager/app
  rm -rf "$TMP"
fi

echo
echo "All sources present. Next:"
echo "  1. cp .env.example .env   (edit)"
echo "  2. ./scripts/bootstrap-atom-env.sh"
echo "  3. docker compose up -d db && docker compose run --rm php composer install"
