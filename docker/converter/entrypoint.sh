#!/bin/bash
set -euo pipefail

cd /converter

# Install + build nitro-converter (bind-mounted from docker/converter/app)
if [ ! -d node_modules ]; then
  echo "[converter] yarn install"
  yarn install --frozen-lockfile || yarn install
fi
if [ ! -d dist ]; then
  echo "[converter] yarn build"
  yarn build || true
fi

# nitro-converter pulls SWFs over HTTP. Serve /swfs on 127.0.0.1:8080 so the
# baked configuration.json URLs resolve.
echo "[converter] serving /swfs on 127.0.0.1:8080"
http-server /swfs -p 8080 -a 127.0.0.1 -s &
HS_PID=$!
trap 'kill "$HS_PID" 2>/dev/null || true' EXIT

sleep 1

# Copy baked config into the app's expected location
if [ -f /config/configuration.json ]; then
  mkdir -p /converter/config
  cp /config/configuration.json /converter/config/configuration.json
fi

echo "[converter] yarn start"
yarn start
