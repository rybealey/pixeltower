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

# Copy baked-in ms-websockets plugin into the volume-backed plugins/ dir
# (idempotent; operator can add more plugins alongside)
if [ ! -f /emulator/plugins/ms-websockets.jar ]; then
  cp /emulator/plugins-baked/ms-websockets.jar /emulator/plugins/ms-websockets.jar
fi

exec java \
  -Dfile.encoding=UTF8 \
  -Xmx"${EMU_XMX}" \
  -jar /emulator/emulator.jar
