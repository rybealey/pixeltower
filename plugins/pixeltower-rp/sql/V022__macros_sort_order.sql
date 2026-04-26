-- ─── V022: Player macro sort order ────────────────────────────────────
-- Lets the Macros window persist drag-to-reorder. Sent over the wire by
-- ReorderMacrosComposer (header 6557) and assigned by MacrosManager.reorder
-- as 0..N-1 in the order the client sends. Existing rows default to 0
-- and tie-break on id (insert order) on the first load — same display
-- order as before, then reorderable from then on.
--
-- Split into two ALTERs with IF NOT EXISTS so seed-db.sh's idempotent
-- re-run on every need_sql=1 deploy doesn't fail with "Duplicate column".
-- A combined ALTER would roll back atomically on partial overlap and
-- abort the migration loop before later V*.sql files run.

ALTER TABLE `rp_macros`
    ADD COLUMN IF NOT EXISTS `sort_order` INT NOT NULL DEFAULT 0 AFTER `category`;
ALTER TABLE `rp_macros`
    ADD INDEX IF NOT EXISTS `idx_habbo_sort` (`habbo_id`, `sort_order`);
