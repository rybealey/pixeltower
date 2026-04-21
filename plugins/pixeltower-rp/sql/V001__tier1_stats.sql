-- ─── Tier 1: Stats, Economy, Corporations ──────────────────────────────────
-- Applied after emulator/base-database.sql and emulator/missing-tables.sql
-- by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Monetary units: Arcturus's existing `users.credits` column (INT). RP money
-- flows through that wallet; rp_money_ledger is an append-only audit trail
-- with a post-transaction balance snapshot per row.
--
-- Permission bitmap (rp_corporation_ranks.permissions):
--   bit 0  CAN_HIRE                (1)
--   bit 1  CAN_FIRE                (2)
--   bit 2  CAN_PROMOTE             (4)
--   bit 3  CAN_DEMOTE              (8)
--   bit 4  CAN_VIEW_ROSTER         (16)
--   bit 5  CAN_VIEW_STOCK          (32)
--   bit 6  CAN_VIEW_LEDGER         (64)
--   bit 7  CAN_ADJUST_SALARIES     (128)
--   bit 8  CAN_BROADCAST           (256)
--   bit 9  CAN_WITHDRAW_STOCK      (512)

-- ── Player stats ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `rp_player_stats` (
    `habbo_id`              INT           NOT NULL,
    `hp`                    INT           NOT NULL DEFAULT 100,
    `max_hp`                INT           NOT NULL DEFAULT 100,
    `energy`                INT           NOT NULL DEFAULT 100,
    `max_energy`            INT           NOT NULL DEFAULT 100,
    `level`                 INT           NOT NULL DEFAULT 1,
    `xp`                    INT           NOT NULL DEFAULT 0,
    `skill_points_unspent`  INT           NOT NULL DEFAULT 0,
    `skill_hit`             INT           NOT NULL DEFAULT 1,
    `skill_endurance`       INT           NOT NULL DEFAULT 1,
    `skill_stamina`         INT           NOT NULL DEFAULT 1,
    `on_duty_corp_id`       INT           DEFAULT NULL,
    `is_on_duty`            TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`habbo_id`),
    KEY `idx_on_duty_corp` (`on_duty_corp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Money ledger (audit log; not a balance — balance lives on users.credits) ──
CREATE TABLE IF NOT EXISTS `rp_money_ledger` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `habbo_id`      INT           NOT NULL,
    `delta`         BIGINT        NOT NULL,
    `balance_after` BIGINT        NOT NULL,
    `reason`        VARCHAR(64)   NOT NULL,
    `ref_id`        BIGINT        DEFAULT NULL,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_habbo_created` (`habbo_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Corporations ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `rp_corporations` (
    `id`                  INT           NOT NULL AUTO_INCREMENT,
    `corp_key`            VARCHAR(32)   NOT NULL,
    `name`                VARCHAR(64)   NOT NULL,
    `hq_room_id`          INT           DEFAULT NULL,
    `paycheck_interval_s` INT           NOT NULL DEFAULT 600,
    `stock_capacity`      INT           NOT NULL DEFAULT 1000,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_corp_key` (`corp_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rp_corporation_ranks` (
    `corp_id`      INT           NOT NULL,
    `rank_num`     INT           NOT NULL,
    `title`        VARCHAR(48)   NOT NULL,
    `salary`       BIGINT        NOT NULL DEFAULT 0,
    `permissions`  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (`corp_id`, `rank_num`),
    CONSTRAINT `fk_rank_corp` FOREIGN KEY (`corp_id`)
        REFERENCES `rp_corporations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One-at-a-time corp membership: UNIQUE on habbo_id enforces it.
CREATE TABLE IF NOT EXISTS `rp_corporation_members` (
    `corp_id`    INT       NOT NULL,
    `habbo_id`   INT       NOT NULL,
    `rank_num`   INT       NOT NULL,
    `hired_at`   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`corp_id`, `habbo_id`),
    UNIQUE KEY `uk_member_habbo` (`habbo_id`),
    KEY `idx_member_rank` (`corp_id`, `rank_num`),
    CONSTRAINT `fk_member_corp` FOREIGN KEY (`corp_id`)
        REFERENCES `rp_corporations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rp_corporation_stock` (
    `corp_id`   INT           NOT NULL,
    `item_key`  VARCHAR(64)   NOT NULL,
    `quantity`  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`corp_id`, `item_key`),
    CONSTRAINT `fk_stock_corp` FOREIGN KEY (`corp_id`)
        REFERENCES `rp_corporations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Police corp seed ──────────────────────────────────────────────────────
-- Tier 3 will flesh out HQ / jail room IDs via AtomCMS housekeeping.
INSERT IGNORE INTO `rp_corporations`
    (`id`, `corp_key`, `name`, `hq_room_id`, `paycheck_interval_s`, `stock_capacity`)
VALUES
    (1, 'police', 'Police Department', NULL, 600, 1000);

-- 5-rank ladder. Permission bitmaps:
--   Cadet      (rank 1): VIEW_ROSTER                                       = 16
--   Officer    (rank 2): VIEW_ROSTER, BROADCAST                            = 272
--   Sergeant   (rank 3): + HIRE, FIRE                                      = 275
--   Lieutenant (rank 4): + PROMOTE, DEMOTE, VIEW_STOCK, VIEW_LEDGER        = 383
--   Chief      (rank 5): + ADJUST_SALARIES, WITHDRAW_STOCK (all bits)      = 1023
INSERT IGNORE INTO `rp_corporation_ranks`
    (`corp_id`, `rank_num`, `title`,           `salary`, `permissions`)
VALUES
    (1, 1, 'Cadet',             50,   16),
    (1, 2, 'Officer',            100,  272),
    (1, 3, 'Sergeant',           200,  275),
    (1, 4, 'Lieutenant',         400,  383),
    (1, 5, 'Chief of Police',    750,  1023);
