-- ─── V022: Player macro sort order ────────────────────────────────────
-- Lets the Macros window persist drag-to-reorder. Sent over the wire by
-- ReorderMacrosComposer (header 6557) and assigned by MacrosManager.reorder
-- as 0..N-1 in the order the client sends. Existing rows default to 0
-- and tie-break on id (insert order) on the first load — same display
-- order as before, then reorderable from then on.

ALTER TABLE `rp_macros`
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 AFTER `category`,
    ADD INDEX `idx_habbo_sort` (`habbo_id`, `sort_order`);
