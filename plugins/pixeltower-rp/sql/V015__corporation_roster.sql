-- ─── V015: Corporation roster ────────────────────────────────────────────
-- Renames the V001 / V002 placeholder names to their canonical Pixeltower
-- forms and seeds the four remaining tier-1 corps. corp_keys are kept
-- stable: 'police' and 'bank' are referenced elsewhere (BankManager reads
-- rp.bank.fee_corp_key='bank' to route deposit fees), so only the display
-- name changes.
--
-- Ranks, members, HQ room ids, and per-corp tunables (paycheck interval,
-- stock capacity) are intentionally left at defaults — each vertical
-- ships its own follow-up migration as the corp comes online.
--
-- Idempotent: UPDATE re-applies the same value; INSERT IGNORE skips
-- existing ids.

UPDATE `rp_corporations` SET `name` = 'San Francisco Police Department'
WHERE `corp_key` = 'police';

UPDATE `rp_corporations` SET `name` = 'Mercury'
WHERE `corp_key` = 'bank';

INSERT IGNORE INTO `rp_corporations`
    (`id`, `corp_key`, `name`,                            `hq_room_id`, `paycheck_interval_s`, `stock_capacity`)
VALUES
    (3, 'hospital', 'San Francisco General Hospital',     NULL, 600, 1000),
    (4, 'cafe',     'Caffe Trieste',                      NULL, 600, 1000),
    (5, 'casino',   'Lucky Chances Casino',               NULL, 600, 1000),
    (6, 'armory',   'The Armory',                         NULL, 600, 1000);
