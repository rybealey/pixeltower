#!/bin/bash
set -euo pipefail

cd /emulator

# Prefer a host-provided JAR under /emulator (bind mount). Fall back to the
# version we built into the image at /opt/arcturus/Emulator.jar.
JAR=$(ls -1 /emulator/*.jar 2>/dev/null | head -n1 || true)
if [ -z "${JAR}" ]; then
  JAR=/opt/arcturus/Emulator.jar
  echo "[emulator] Using baked-in JAR: $JAR"
else
  echo "[emulator] Using host-provided JAR: $JAR"
fi

# Plugins: honor host /emulator/plugins; copy in any baked-in default plugins
# (e.g. NitroWebsockets) that aren't already present. This makes the stack
# work out-of-the-box without the host having to stage plugin JARs.
mkdir -p /emulator/plugins
if [ -d /opt/arcturus/default-plugins ]; then
  for jar in /opt/arcturus/default-plugins/*.jar; do
    [ -f "$jar" ] || continue
    name=$(basename "$jar")
    if [ ! -f "/emulator/plugins/$name" ]; then
      echo "[emulator] installing default plugin: $name"
      cp "$jar" "/emulator/plugins/$name"
    fi
  done
fi

# Render config.ini from template if present, otherwise leave an existing one alone.
if [ -f /emulator/config.ini.template ]; then
  echo "[emulator] Rendering config.ini from /emulator/config.ini.template"
  envsubst < /emulator/config.ini.template > /emulator/config.ini
elif [ ! -f /emulator/config.ini ]; then
  echo "[emulator] ERROR: no /emulator/config.ini and no config.ini.template found." >&2
  echo "[emulator] Expected ./emulator/config.ini.template on the host (bind-mounted)." >&2
  exit 1
fi

: "${EMU_XMX:=2048m}"

echo "[emulator] Launching $JAR (Xmx=$EMU_XMX)"
exec java -Dfile.encoding=UTF-8 -Xmx"${EMU_XMX}" -jar "$JAR"
