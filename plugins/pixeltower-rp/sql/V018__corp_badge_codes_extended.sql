-- ─── V018: Corporation badge codes (extended) ────────────────────────────
-- Backfills badge_code for the five corps V017 left at NULL. Hospital was
-- seeded in V017 (NB412); this migration rounds out the canonical roster.
--
-- Idempotent: UPDATE re-applies the same value.

UPDATE `rp_corporations` SET `badge_code` = 'FRF96' WHERE `corp_key` = 'police';
UPDATE `rp_corporations` SET `badge_code` = 'FRH42' WHERE `corp_key` = 'bank';
UPDATE `rp_corporations` SET `badge_code` = 'NYC14' WHERE `corp_key` = 'cafe';
UPDATE `rp_corporations` SET `badge_code` = 'TOE20' WHERE `corp_key` = 'casino';
UPDATE `rp_corporations` SET `badge_code` = 'FLV09' WHERE `corp_key` = 'armory';
