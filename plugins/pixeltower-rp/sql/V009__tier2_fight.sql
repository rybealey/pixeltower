-- ─── Tier 2: Fight + Downed state ─────────────────────────────────────────
-- Applied after V001-V008 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Model: an "engagement" is one (attacker, defender, room) pairing that
-- accumulates all hits until 30s of no-hits close it out (or knockout /
-- logout / roomchange / staff force-close). Per-hit forensics live in
-- rp_fight_hits for debugging + future leaderboards. Downed players get a
-- single-row presence in rp_downed_players with a precomputed respawn_at
-- so the RespawnScheduler can rehydrate timers on reconnect.
--
-- downed_in_room nullable: an offline or out-of-room transition (staff
-- :fighttest / :kill while the target isn't in a room) still records the
-- downed state so next-login reapply logic works, but has no room to anchor.

-- ── Fight engagements (one row per attacker/defender/room pairing) ─────────
CREATE TABLE IF NOT EXISTS `rp_fights` (
    `id`                         BIGINT        NOT NULL AUTO_INCREMENT,
    `attacker_id`                INT           NOT NULL,
    `defender_id`                INT           NOT NULL,
    `room_id`                    INT           NOT NULL,
    `started_at`                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ended_at`                   DATETIME      DEFAULT NULL,
    `ender_reason`               VARCHAR(32)   DEFAULT NULL,
    `attacker_hits`              INT           NOT NULL DEFAULT 0,
    `defender_hits`              INT           NOT NULL DEFAULT 0,
    `total_damage_to_defender`   INT           NOT NULL DEFAULT 0,
    `total_damage_to_attacker`   INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_attacker` (`attacker_id`, `started_at`),
    KEY `idx_defender` (`defender_id`, `started_at`),
    KEY `idx_active`   (`ended_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Per-hit forensic log ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `rp_fight_hits` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `fight_id`     BIGINT        NOT NULL,
    `actor_id`     INT           NOT NULL,
    `target_id`    INT           NOT NULL,
    `raw_damage`   INT           NOT NULL,
    `final_damage` INT           NOT NULL,
    `hit_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_fight` (`fight_id`, `hit_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Downed-player presence ────────────────────────────────────────────────
-- habbo_id as PK enforces "one downed row per user". downed_in_room and
-- fight_id are nullable so staff force-KO paths (:kill from a non-room
-- context, future fall damage) can still record the downed transition.
CREATE TABLE IF NOT EXISTS `rp_downed_players` (
    `habbo_id`       INT           NOT NULL,
    `downed_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `downed_in_room` INT           DEFAULT NULL,
    `downed_by_id`   INT           DEFAULT NULL,
    `fight_id`       BIGINT        DEFAULT NULL,
    `respawn_at`     DATETIME      NOT NULL,
    PRIMARY KEY (`habbo_id`),
    KEY `idx_respawn_at` (`respawn_at`),
    CONSTRAINT `fk_downed_habbo` FOREIGN KEY (`habbo_id`)
        REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
