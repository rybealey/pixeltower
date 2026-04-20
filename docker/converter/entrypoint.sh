#!/bin/bash
set -euo pipefail

cd /converter

# Install + build nitro-converter (bind-mounted from docker/converter/app)
if [ ! -d node_modules ]; then
  echo "[converter] yarn install"
  yarn install
fi
if [ ! -d dist ]; then
  echo "[converter] yarn build"
  yarn build
fi

# Drop our configuration.json into place (upstream expects it at project root)
if [ -f /config/configuration.json ]; then
  cp /config/configuration.json /converter/configuration.json
fi

# ./assets should already be a host-side symlink to /gamedata (set up by
# scripts/convert-swfs.sh before launching this container), so the hardcoded
# converter output path ./assets/bundled/<type> lands in /gamedata/bundled/.

# Serve gamedata/ over HTTP so URLs in configuration.json resolve.
# /gamedata = our gamedata bind-mount. /swfs also exists (SWF_PACK_DIR)
# but the config paths use /gamedata/dcr/hof_furni so both work.
echo "[converter] serving /gamedata on 127.0.0.1:8080"
http-server /gamedata -p 8080 -a 127.0.0.1 -s &
HS_PID=$!
trap 'kill "$HS_PID" 2>/dev/null || true' EXIT

sleep 1

echo "[converter] yarn start (furniture only; figures/effects/pets come from default-assets)"
yarn start
