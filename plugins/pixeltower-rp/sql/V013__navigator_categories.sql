-- ─── Tier 2: Pixeltower navigator categories ──────────────────────────────
-- Applied after V012 by scripts/seed-db.sh. Idempotent — deletes the full
-- row set and reinserts with stable IDs, then re-homes any orphaned rooms.
--
-- Replaces the nine legacy Habbo categories (BC/BUILDING/CHAT/FANSITE/
-- GAMES/HELP/LIFE/OFFICIAL/PARTY) with Pixeltower's five. Caption strings
-- reference navigator.flatcategory.global.* text keys added via
-- gamedata-overrides/pixeltower-texts.json so the labels render correctly
-- in the client. Staff (id 5) is min_rank=7 so the tab only appears for
-- users with rank ≥ 7 in the Navigator.

DELETE FROM `navigator_flatcats`;

INSERT INTO `navigator_flatcats`
  (`id`, `min_rank`, `caption_save`,          `caption`,                                       `can_trade`, `max_user_count`, `public`, `list_type`, `order_num`) VALUES
  (1,    0,          'caption_save_general',  '${navigator.flatcategory.global.GENERAL}',     '0',         100,              '0',      0,            1),
  (2,    0,          'caption_save_services', '${navigator.flatcategory.global.SERVICES}',    '0',         100,              '0',      0,            2),
  (3,    0,          'caption_save_commerce', '${navigator.flatcategory.global.COMMERCE}',    '0',         100,              '0',      0,            3),
  (4,    0,          'caption_save_resident', '${navigator.flatcategory.global.RESIDENTIAL}', '0',         100,              '0',      0,            4),
  (5,    7,          'caption_save_staff',    '${navigator.flatcategory.global.STAFF}',       '0',         100,              '0',      0,            5);

-- Re-home any rooms pointing at a now-deleted category so they aren't
-- orphaned against a missing FK.
UPDATE `rooms` SET `category` = 1 WHERE `category` NOT IN (1,2,3,4,5);
