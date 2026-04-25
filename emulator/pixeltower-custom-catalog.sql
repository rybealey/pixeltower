-- ─── Pixeltower custom catalog pages ──────────────────────────────────────
-- Imported by scripts/seed-db.sh AFTER emulator/catalog-sqls/*.sql. The
-- upstream Morningstar pack does DROP TABLE + recreate on catalog_pages,
-- so any custom page has to land here (post-catalog) to survive. Tracked
-- file — unlike emulator/catalog-sqls/ which is gitignored and refreshed
-- by pull-default-pack.sh.
--
-- INSERT IGNORE keeps this idempotent; re-runs are a no-op once the id
-- is present. Use IDs in the 9000+ range to avoid colliding with future
-- upstream Morningstar additions.

INSERT IGNORE INTO `catalog_pages`
  (`id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
   `icon_color`, `icon_image`, `min_rank`, `order_num`,
   `visible`, `enabled`, `club_only`, `vip_only`,
   `page_headline`, `page_teaser`, `page_special`,
   `page_text1`, `page_text2`, `page_text_details`, `page_text_teaser`,
   `room_id`, `includes`)
VALUES
  (9001, 7, 'pixelrp', 'PixelRP', 'default_3x3',
   1, 65, 4, 0,
   '1', '1', '0', '0',
   '', '', '',
   '', '', '', '',
   0, '');

-- Items. sprite_id must match the classname's FurnitureData.json id (see
-- custom-furni/<classname>/metadata.json). Using explicit high IDs (99001+)
-- to avoid collisions with upstream items_base.sql's AUTO_INCREMENT=50554.
INSERT IGNORE INTO `items_base`
  (`id`, `sprite_id`, `public_name`, `item_name`, `type`,
   `width`, `length`, `stack_height`,
   `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`,
   `allow_gift`, `allow_trade`, `allow_recycle`, `allow_marketplace_sell`,
   `allow_inventory_stack`,
   `interaction_type`, `interaction_modes_count`,
   `vending_ids`, `multiheight`, `customparams`,
   `effect_id_male`, `effect_id_female`, `clothing_on_walk`)
VALUES
  (99001, 99001, 'PixelRP Room Switcher', 'room_switcher2', 's',
   1, 1, 0.00,
   0, 0, 0, 1,
   1, 0, 0, 0,
   1,
   'teleporttile', 6,
   '0', '0', '',
   0, 0, '');

-- Force interaction_type=teleporttile on any pre-existing row. Stock Arcturus
-- InteractionTeleportTile (bound to 'teleporttile') already has walk-on
-- semantics AND a fast-emerge animation (500ms stages collapsed to 0ms for
-- InteractionTeleportTile instances), so we get instant walk-on teleport
-- without a custom interaction class. arcturus-patches/teleporttile-snap.patch
-- additionally trims the single remaining 1000ms startup delay.
UPDATE `items_base`
   SET `interaction_type` = 'teleporttile'
 WHERE `id` = 99001
   AND `interaction_type` <> 'teleporttile';

-- Catalog items — links items_base row to the PixelRP page (9001).
INSERT IGNORE INTO `catalog_items`
  (`id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`,
   `points_type`, `amount`, `limited_stack`, `limited_sells`, `order_number`,
   `offer_id`, `song_id`, `extradata`, `have_offer`, `club_only`)
VALUES
  (99001, '99001', 9001, 'room_switcher2', 0, 0,
   0, 1, 0, 0, 1,
   -1, 0, '', '1', '0');

-- ─── PixelRP > Farming sub-page ─────────────────────────────────────────
INSERT IGNORE INTO `catalog_pages`
  (`id`, `parent_id`, `caption_save`, `caption`, `page_layout`,
   `icon_color`, `icon_image`, `min_rank`, `order_num`,
   `visible`, `enabled`, `club_only`, `vip_only`,
   `page_headline`, `page_teaser`, `page_special`,
   `page_text1`, `page_text2`, `page_text_details`, `page_text_teaser`,
   `room_id`, `includes`)
VALUES
  (9002, 9001, 'farming', 'Farming', 'default_3x3',
   1, 65, 4, 1,
   '1', '1', '0', '0',
   '', '', '',
   '', '', '', '',
   0, '');

-- ─── Farming items (kasja_farm pack) ────────────────────────────────────
-- 33 rows, IDs 99002–99034.
-- Walkable overrides (allow_walk=1, stack_height adjusted) on paths + stairs.
INSERT IGNORE INTO `items_base`
  (`id`, `sprite_id`, `public_name`, `item_name`, `type`,
   `width`, `length`, `stack_height`,
   `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`,
   `allow_gift`, `allow_trade`, `allow_recycle`, `allow_marketplace_sell`,
   `allow_inventory_stack`,
   `interaction_type`, `interaction_modes_count`,
   `vending_ids`, `multiheight`, `customparams`,
   `effect_id_male`, `effect_id_female`, `clothing_on_walk`)
VALUES
  (99002, 99002, 'Book', 'kasja_g2_book', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99003, 99003, 'Books', 'kasja_g2_books', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99004, 99004, 'Big Rock', 'kasja_g2_brock', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99005, 99005, 'Broom', 'kasja_g2_broom', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99006, 99006, 'Big Stone Path', 'kasja_g2_bstonepath', 's', 1,1,0.00, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99007, 99007, 'Bucket', 'kasja_g2_bucket', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99008, 99008, 'Bucket of Leaves', 'kasja_g2_bucketleaves', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99009, 99009, 'Corner', 'kasja_g2_corner', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99010, 99010, 'Divider', 'kasja_g2_divider', 's', 2,1,0.50, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99011, 99011, 'Double Stair', 'kasja_g2_doublestair', 's', 2,1,0.50, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99012, 99012, 'Fence', 'kasja_g2_fence', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99013, 99013, 'Flower Pot', 'kasja_g2_flowerpot', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99014, 99014, 'Flower Tree', 'kasja_g2_flowertree', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99015, 99015, 'Glowy Flower', 'kasja_g2_glowyflower', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99016, 99016, 'Grass', 'kasja_g2_grass', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99017, 99017, 'Plant', 'kasja_g2_plant', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99018, 99018, 'Plant 1', 'kasja_g2_plant1', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99019, 99019, 'Plant 2', 'kasja_g2_plant2', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99020, 99020, 'Rock', 'kasja_g2_rock', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99021, 99021, 'Rose Bush', 'kasja_g2_rosebush', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99022, 99022, 'Seed Bag', 'kasja_g2_seedbag', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99023, 99023, 'Seed Book', 'kasja_g2_seedbook', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99024, 99024, 'Shovel', 'kasja_g2_shovel', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99025, 99025, 'Shrub', 'kasja_g2_shrub', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99026, 99026, 'Small Stone Path', 'kasja_g2_sstonepath', 's', 1,1,0.00, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99027, 99027, 'Stairs', 'kasja_g2_stairs', 's', 1,1,0.50, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99028, 99028, 'Stone', 'kasja_g2_stone', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99029, 99029, 'Stone 1', 'kasja_g2_stone1', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99030, 99030, 'Tree', 'kasja_g2_tree', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99031, 99031, 'Tree Pot', 'kasja_g2_treepot', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99032, 99032, 'Triple Stair', 'kasja_g2_triplestair', 's', 2,1,0.50, 1,0,0,1, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99033, 99033, 'Trolley', 'kasja_g2_trolley', 's', 1,2,1.20, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,''),
  (99034, 99034, 'Watering Can', 'kasja_g2_wateringcan', 's', 1,1,1.00, 1,0,0,0, 1,0,0,0, 1, 'default',1, '0','0','', 0,0,'');

INSERT IGNORE INTO `catalog_items`
  (`id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`,
   `points_type`, `amount`, `limited_stack`, `limited_sells`, `order_number`,
   `offer_id`, `song_id`, `extradata`, `have_offer`, `club_only`)
VALUES
  (99002, '99002', 9002, 'kasja_g2_book', 0,0, 0, 1, 0,0,  1, -1, 0,'', '1','0'),
  (99003, '99003', 9002, 'kasja_g2_books', 0,0, 0, 1, 0,0,  2, -1, 0,'', '1','0'),
  (99004, '99004', 9002, 'kasja_g2_brock', 0,0, 0, 1, 0,0,  3, -1, 0,'', '1','0'),
  (99005, '99005', 9002, 'kasja_g2_broom', 0,0, 0, 1, 0,0,  4, -1, 0,'', '1','0'),
  (99006, '99006', 9002, 'kasja_g2_bstonepath', 0,0, 0, 1, 0,0,  5, -1, 0,'', '1','0'),
  (99007, '99007', 9002, 'kasja_g2_bucket', 0,0, 0, 1, 0,0,  6, -1, 0,'', '1','0'),
  (99008, '99008', 9002, 'kasja_g2_bucketleaves', 0,0, 0, 1, 0,0,  7, -1, 0,'', '1','0'),
  (99009, '99009', 9002, 'kasja_g2_corner', 0,0, 0, 1, 0,0,  8, -1, 0,'', '1','0'),
  (99010, '99010', 9002, 'kasja_g2_divider', 0,0, 0, 1, 0,0,  9, -1, 0,'', '1','0'),
  (99011, '99011', 9002, 'kasja_g2_doublestair', 0,0, 0, 1, 0,0, 10, -1, 0,'', '1','0'),
  (99012, '99012', 9002, 'kasja_g2_fence', 0,0, 0, 1, 0,0, 11, -1, 0,'', '1','0'),
  (99013, '99013', 9002, 'kasja_g2_flowerpot', 0,0, 0, 1, 0,0, 12, -1, 0,'', '1','0'),
  (99014, '99014', 9002, 'kasja_g2_flowertree', 0,0, 0, 1, 0,0, 13, -1, 0,'', '1','0'),
  (99015, '99015', 9002, 'kasja_g2_glowyflower', 0,0, 0, 1, 0,0, 14, -1, 0,'', '1','0'),
  (99016, '99016', 9002, 'kasja_g2_grass', 0,0, 0, 1, 0,0, 15, -1, 0,'', '1','0'),
  (99017, '99017', 9002, 'kasja_g2_plant', 0,0, 0, 1, 0,0, 16, -1, 0,'', '1','0'),
  (99018, '99018', 9002, 'kasja_g2_plant1', 0,0, 0, 1, 0,0, 17, -1, 0,'', '1','0'),
  (99019, '99019', 9002, 'kasja_g2_plant2', 0,0, 0, 1, 0,0, 18, -1, 0,'', '1','0'),
  (99020, '99020', 9002, 'kasja_g2_rock', 0,0, 0, 1, 0,0, 19, -1, 0,'', '1','0'),
  (99021, '99021', 9002, 'kasja_g2_rosebush', 0,0, 0, 1, 0,0, 20, -1, 0,'', '1','0'),
  (99022, '99022', 9002, 'kasja_g2_seedbag', 0,0, 0, 1, 0,0, 21, -1, 0,'', '1','0'),
  (99023, '99023', 9002, 'kasja_g2_seedbook', 0,0, 0, 1, 0,0, 22, -1, 0,'', '1','0'),
  (99024, '99024', 9002, 'kasja_g2_shovel', 0,0, 0, 1, 0,0, 23, -1, 0,'', '1','0'),
  (99025, '99025', 9002, 'kasja_g2_shrub', 0,0, 0, 1, 0,0, 24, -1, 0,'', '1','0'),
  (99026, '99026', 9002, 'kasja_g2_sstonepath', 0,0, 0, 1, 0,0, 25, -1, 0,'', '1','0'),
  (99027, '99027', 9002, 'kasja_g2_stairs', 0,0, 0, 1, 0,0, 26, -1, 0,'', '1','0'),
  (99028, '99028', 9002, 'kasja_g2_stone', 0,0, 0, 1, 0,0, 27, -1, 0,'', '1','0'),
  (99029, '99029', 9002, 'kasja_g2_stone1', 0,0, 0, 1, 0,0, 28, -1, 0,'', '1','0'),
  (99030, '99030', 9002, 'kasja_g2_tree', 0,0, 0, 1, 0,0, 29, -1, 0,'', '1','0'),
  (99031, '99031', 9002, 'kasja_g2_treepot', 0,0, 0, 1, 0,0, 30, -1, 0,'', '1','0'),
  (99032, '99032', 9002, 'kasja_g2_triplestair', 0,0, 0, 1, 0,0, 31, -1, 0,'', '1','0'),
  (99033, '99033', 9002, 'kasja_g2_trolley', 0,0, 0, 1, 0,0, 32, -1, 0,'', '1','0'),
  (99034, '99034', 9002, 'kasja_g2_wateringcan', 0,0, 0, 1, 0,0, 33, -1, 0,'', '1','0');
