-- ─── V020: Armory badge override ─────────────────────────────────────────
-- The FLV09 code seeded in V018 didn't render in the corp badge rail; swap
-- to US613 which is a stable hotel album1584 entry.
--
-- Idempotent: UPDATE re-applies the same value.

UPDATE `rp_corporations` SET `badge_code` = 'US613' WHERE `corp_key` = 'armory';
