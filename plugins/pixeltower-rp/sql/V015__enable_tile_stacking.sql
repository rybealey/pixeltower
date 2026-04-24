-- ─── Tier 2: enable hotel-wide tile stacking ─────────────────────────────
-- Applied after V014 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Lifts the Arcturus one-occupant-per-tile rule hotel-wide. The flag is
-- consumed by arcturus-patches/allow-tile-stacking.patch at two gates:
--   1. TileValidator.isTileWalkable — pathfinder's goal-tile exclusion
--   2. RoomUnit.cycle            — the final-step stacking reject
-- The key is already seeded with value '0' by emulator/base-database.sql;
-- this migration flips it to '1'. Requires an emulator restart to take
-- effect (the value is cached `static final` at class-load in both patched
-- classes — intentional, both sites run on the hot pathfinding/cycle path).

UPDATE `emulator_settings` SET `value` = '1'
 WHERE `key` = 'custom.stacking.enabled';
