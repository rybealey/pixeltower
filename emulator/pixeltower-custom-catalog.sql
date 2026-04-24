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
   'teleport', 6,
   '0', '0', '',
   0, 0, '');

-- Ensure the row's interaction_type stays 'teleport' even if a prior
-- deploy set it to something else (e.g. the retired 'rp_teleport_walkon'
-- experiment). Stock Arcturus InteractionTeleport + the arcturus-patch
-- teleport-walk-on.patch gives us walk-on dispatch without a custom class.
UPDATE `items_base`
   SET `interaction_type` = 'teleport'
 WHERE `id` = 99001
   AND `interaction_type` <> 'teleport';

-- Catalog items — links items_base row to the PixelRP page (9001).
INSERT IGNORE INTO `catalog_items`
  (`id`, `item_ids`, `page_id`, `catalog_name`, `cost_credits`, `cost_points`,
   `points_type`, `amount`, `limited_stack`, `limited_sells`, `order_number`,
   `offer_id`, `song_id`, `extradata`, `have_offer`, `club_only`)
VALUES
  (99001, '99001', 9001, 'room_switcher2', 0, 0,
   0, 1, 0, 0, 1,
   -1, 0, '', '1', '0');
