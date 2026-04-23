-- ─── Tier 2: Per-room flags (safe zones) ──────────────────────────────────
-- Applied after V009 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Pixeltower defaults to free-PvP everywhere; a row here with no_pvp=1
-- opts a specific room out (hotel lobby, hospital, corp HQs). FightRules
-- .canEngage() reads this via a cached RoomFlags lookup and refuses :hit
-- when no_pvp is set.
--
-- Keyed by room_id with an FK to the emulator's rooms table so the row
-- evaporates when a room is deleted.

CREATE TABLE IF NOT EXISTS `rp_room_flags` (
    `room_id` INT        NOT NULL,
    `no_pvp`  TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`room_id`),
    CONSTRAINT `fk_roomflags_room` FOREIGN KEY (`room_id`)
        REFERENCES `rooms` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
