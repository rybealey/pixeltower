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
