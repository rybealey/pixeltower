#!/bin/bash
# Clone all third-party source trees the stack depends on. Safe to re-run;
# skips directories that already exist. Delete a dir first to pull fresh.
set -euo pipefail

cd "$(dirname "$0")/.."

clone_if_missing() {
  local url="$1" dest="$2" branch="${3:-}"
  if [ -d "$dest/.git" ] || [ -d "$dest" ]; then
    echo "[skip] $dest already present"
    return
  fi
  echo "[clone] $url → $dest${branch:+ (branch $branch)}"
  if [ -n "$branch" ]; then
    git clone --depth 1 --branch "$branch" "$url" "$dest"
  else
    git clone --depth 1 "$url" "$dest"
  fi
}

clone_if_missing https://github.com/airilx96/atomcms               atomcms
clone_if_missing https://github.com/billsonnn/nitro-react          nitro
clone_if_missing https://github.com/billsonnn/nitro-converter      docker/converter/app
clone_if_missing https://github.com/billsonnn/nitro-imager         docker/imager/app

# Seed gamedata with Nitro default-assets (pre-built .nitro bundles covering
# baseline figure, effect, bundled, furniture, pet — fills gaps the converter
# can't produce without the original SWFs).
if [ ! -d gamedata/.default-assets-pulled ]; then
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  echo "[clone] git.krews.org/nitro/default-assets (baseline .nitro bundles)"
  git clone --depth 1 https://git.krews.org/nitro/default-assets.git "$TMP/default-assets"
  mkdir -p gamedata
  for sub in bundled figure effect furniture pet gamedata c_images; do
    if [ -d "$TMP/default-assets/$sub" ]; then
      echo "  [+] gamedata/$sub/"
      rsync -a --ignore-existing "$TMP/default-assets/$sub/" "gamedata/$sub/"
    fi
  done
  touch gamedata/.default-assets-pulled
fi

echo
echo "All sources present. Next:"
echo "  1. cp .env.example .env   (edit DB passwords)"
echo "  2. ./scripts/pull-gamedata.sh"
echo "  3. ./scripts/pull-default-pack.sh"
echo "  4. ./scripts/pull-emulator-sql.sh"
