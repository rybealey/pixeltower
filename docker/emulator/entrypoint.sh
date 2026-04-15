#!/bin/bash
set -euo pipefail

cd /emulator

JAR=$(ls -1 *.jar 2>/dev/null | head -n1 || true)
if [ -z "${JAR}" ]; then
  echo "[emulator] No JAR found in /emulator. Drop Emulator.jar + plugins/ into ./emulator/ on the host." >&2
  exit 1
fi

if [ -f config.ini.template ]; then
  echo "[emulator] Rendering config.ini from template"
  envsubst < config.ini.template > config.ini
fi

: "${EMU_XMX:=2048m}"

echo "[emulator] Launching $JAR (Xmx=$EMU_XMX)"
exec java -Dfile.encoding=UTF-8 -Xmx"${EMU_XMX}" -jar "$JAR"
