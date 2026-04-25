-- ─── V017: Corporation favorite-group badge override ─────────────────────
-- Adds a per-corp hotel badge code shown in the infostand "favorite group"
-- slot for any employee, regardless of clock-in or their actual chosen
-- Habbo Group. Resolved client-side to /c_images/album1584/<code>.gif via
-- a new packet (header 6504) the Pixeltower-patched Nitro client listens
-- for. NULL means "no override" — clients fall through to the player's
-- real favorite group, so the column is staged-rollout-safe.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS (MariaDB 10.0+); UPDATE re-applies.

ALTER TABLE `rp_corporations`
    ADD COLUMN IF NOT EXISTS `badge_code` VARCHAR(16) NULL AFTER `stock_capacity`;

UPDATE `rp_corporations` SET `badge_code` = 'NB412' WHERE `corp_key` = 'hospital';
