#!/bin/bash
set -euo pipefail

# The converter wants http/https URLs. Serve the SWF pack over loopback so it
# can slurp files without us having to patch its source.
cd /swfs
http-server -p 8080 --cors -s >/tmp/hs.log 2>&1 &
HS=$!
trap "kill $HS 2>/dev/null || true" EXIT

# Give http-server a moment to bind
for _ in $(seq 1 10); do
  if curl -fsS "http://127.0.0.1:8080/figuremap.xml" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

cd /converter
# Copy our pre-baked configuration.json into the converter dir (overlapping
# bind-mounts aren't allowed, so we stage it at /config and copy in).
if [ -f /config/configuration.json ]; then
  cp /config/configuration.json ./configuration.json
fi

if [ ! -d node_modules ]; then
  echo "[converter] installing deps"
  yarn install --frozen-lockfile
fi

if [ ! -d build ]; then
  echo "[converter] building"
  yarn build
fi

# Ensure output dir exists
mkdir -p /gamedata

echo "[converter] running full conversion (this can take 10–30 min)"
exec yarn start
