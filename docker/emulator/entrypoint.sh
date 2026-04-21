#!/bin/sh
set -eu

cd /emulator

# Render config.ini from env
: "${DB_HOST:=db}"
: "${DB_PORT:=3306}"
: "${DB_DATABASE:?missing}"
: "${DB_USERNAME:?missing}"
: "${DB_PASSWORD:?missing}"
: "${EMU_GAME_PORT:=3000}"
: "${EMU_RCON_PORT:=3001}"
: "${EMU_WS_PORT:=2095}"
: "${EMU_XMX:=2048m}"
: "${EMU_TIMEZONE:=UTC}"

export DB_HOST DB_PORT DB_DATABASE DB_USERNAME DB_PASSWORD \
       EMU_GAME_PORT EMU_RCON_PORT EMU_WS_PORT EMU_TIMEZONE

envsubst < /emulator/config.ini.template > /emulator/config.ini

# Copy baked-in plugins into the volume-backed plugins/ dir.
# Always overwrite pixeltower-rp.jar so a rebuild of the emulator image
# propagates new plugin code on the next container restart (this plugin is
# ours and ships with the image). ms-websockets is only copied once so
# operator overrides aren't clobbered.
if [ ! -f /emulator/plugins/ms-websockets.jar ]; then
  cp /emulator/plugins-baked/ms-websockets.jar /emulator/plugins/ms-websockets.jar
fi
if [ -f /emulator/plugins-baked/pixeltower-rp.jar ]; then
  cp -f /emulator/plugins-baked/pixeltower-rp.jar /emulator/plugins/pixeltower-rp.jar
fi

exec java \
  -Dfile.encoding=UTF8 \
  -Xmx"${EMU_XMX}" \
  -jar /emulator/emulator.jar
