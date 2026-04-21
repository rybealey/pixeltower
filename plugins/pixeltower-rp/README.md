# Pixeltower RP Plugin

Arcturus Morningstar plugin hosting all gameplay for the Pixeltower
roleplay server. See [`docs/ROADMAP.md`](../../docs/ROADMAP.md) for the
full Tier 1-6 design.

## Layout

```
pom.xml                      # org.pixeltower:pixeltower-rp, depends on com.eu.habbo:Habbo:3.0.0
src/main/resources/
  plugin.json                # Arcturus plugin manifest
src/main/java/org/pixeltower/rp/
  PixeltowerRP.java          # entry class; registers rp.* config keys
sql/
  V001__tier1_stats.sql      # Tier 1 tables + Police seed
```

## Build

Built alongside the emulator image by `docker/emulator/Dockerfile`'s
`pixeltower-rp-build` stage. The resulting JAR is baked into
`/emulator/plugins-baked/pixeltower-rp.jar` and copied into the live
plugins volume by `docker/emulator/entrypoint.sh` on container start.

To iterate locally without rebuilding the whole emulator image:

```bash
# one-off build inside a throwaway maven container
docker run --rm -v "$PWD/plugins/pixeltower-rp:/src" -w /src \
  maven:3.9-eclipse-temurin-17 mvn -B -DskipTests package
# copy into the running emulator + restart
docker compose cp plugins/pixeltower-rp/target/pixeltower-rp.jar \
  emulator:/emulator/plugins/pixeltower-rp.jar
docker compose restart emulator
```

## SQL

`scripts/seed-db.sh` applies everything under `sql/*.sql` in filename
order after the base dump. Migrations are `CREATE TABLE IF NOT EXISTS`
and `INSERT IGNORE` so re-running is safe.

## Config keys

All tunables live in Arcturus's `emulator_settings` table under `rp.*`.
`PixeltowerRP.java` registers defaults on first boot via
`Emulator.getConfig().register(...)`. Edit via AtomCMS housekeeping or
directly in the DB; run `:rpreload` (once implemented) to hot-refresh.

## Status

**v0.1.0** — Scaffold only. Plugin loads, config keys register, Tier 1
schema + Police seed present. No commands / managers yet. See the
Tier 1 checklist in `docs/ROADMAP.md`.
