-- ─── Tier 2: enable hotel-wide tile stacking ─────────────────────────────
-- Applied after V014 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Lifts the Arcturus one-occupant-per-tile rule hotel-wide. The flag is
-- consumed by arcturus-patches/allow-tile-stacking.patch at two gates:
--   1. TileValidator.isTileWalkable — pathfinder's goal-tile exclusion
--   2. RoomUnit.cycle               — the final-step stacking reject
--
-- Upsert rather than plain UPDATE: upstream base-database.sql seeds this
-- key with value '0' on fresh installs, but prod was seeded from an older
-- snapshot that predates the key, so a plain UPDATE would affect zero rows
-- and leave the flag undefined (getBoolean default = false).

INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('custom.stacking.enabled', '1')
ON DUPLICATE KEY UPDATE `value` = '1';
