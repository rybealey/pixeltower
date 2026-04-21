-- ─── Tier 1: Bank accounts ─────────────────────────────────────────────────
-- Applied after V001 by scripts/seed-db.sh (alphabetical filename order).
-- Idempotent — safe to re-run.
--
-- Wallet split:
--   users.credits       = cash on hand (Arcturus native, top-bar coins,
--                         at risk in Tier 4+ muggings/bounties)
--   rp_player_bank.balance = bank balance (safe, offline-accessible,
--                            accrues interest, reached via ATM rooms)
--
-- Fees: every deposit pays 1% of gross to the Bank corp's treasury.
-- Interest: 0.1% per day on balances ≥ $100, credited by BankInterestTask.
--           Audited as ledger rows with reason='bank_interest'.

-- Bank accounts — one row per user, lazy-created on :openaccount.
CREATE TABLE IF NOT EXISTS `rp_player_bank` (
    `habbo_id`     INT           NOT NULL,
    `balance`      BIGINT        NOT NULL DEFAULT 0,
    `opened_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_txn_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`habbo_id`),
    CONSTRAINT `fk_bank_user` FOREIGN KEY (`habbo_id`)
        REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Treasury column on rp_corporations. IF NOT EXISTS is MariaDB 10.0+.
-- Added here rather than editing V001 so existing deployments pick it up
-- via the next seed-db run.
ALTER TABLE `rp_corporations`
    ADD COLUMN IF NOT EXISTS `treasury` BIGINT NOT NULL DEFAULT 0;

-- Bank corp — owner of the treasury where fees are routed. Ranks/members
-- are intentionally empty; Bank jobs (teller, manager) ship in Tier 6.
INSERT IGNORE INTO `rp_corporations`
    (`id`, `corp_key`, `name`,              `hq_room_id`, `paycheck_interval_s`, `stock_capacity`)
VALUES
    (2,    'bank',     'Pixeltower Bank',   NULL,         600,                   0);

-- Corp treasury audit (parallel to rp_money_ledger's player-centric view).
-- Every change to rp_corporations.treasury writes a row here with the
-- post-transaction balance snapshot and the player who caused it.
CREATE TABLE IF NOT EXISTS `rp_corp_treasury_ledger` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `corp_id`       INT           NOT NULL,
    `delta`         BIGINT        NOT NULL,
    `balance_after` BIGINT        NOT NULL,
    `reason`        VARCHAR(64)   NOT NULL,
    `ref_habbo_id`  INT           DEFAULT NULL,
    `ref_id`        BIGINT        DEFAULT NULL,
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_corp_created` (`corp_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
