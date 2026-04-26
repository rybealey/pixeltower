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

# nitro-converter can write `null` to ExternalTexts.json / UITexts.json if
# the source fetch returned nothing. For ExternalTexts, regenerate from the
# populated external_texts.txt if it exists (31k+ keys from the Morningstar
# default pack). Otherwise fall back to {} so Nitro doesn't NPE on null.
for f in gamedata/gamedata/ExternalTexts.json gamedata/gamedata/UITexts.json; do
  if [ -f "$f" ] && [ "$(tr -d '[:space:]' < "$f")" = "null" ]; then
    if [ "$f" = "gamedata/gamedata/ExternalTexts.json" ] && \
       [ -s gamedata/gamedata/external_texts.txt ]; then
      echo "[post] $f was null → regenerating from external_texts.txt"
      python3 -c "
import json
d = {}
with open('gamedata/gamedata/external_texts.txt', 'r', encoding='utf-8', errors='replace') as fp:
    for line in fp:
        line = line.rstrip('\n\r')
        if not line or line.startswith('#') or '=' not in line: continue
        k, _, v = line.partition('=')
        d[k] = v
with open('$f', 'w', encoding='utf-8') as fp:
    json.dump(d, fp, ensure_ascii=False)
print(f'  rebuilt with {len(d)} keys')
"
    else
      echo "[post] $f was null → replacing with {}"
      echo '{}' > "$f"
    fi
  fi
done

echo "[done] gamedata/ populated with .nitro bundles"
