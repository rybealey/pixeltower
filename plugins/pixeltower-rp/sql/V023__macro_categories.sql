-- ─── V023: First-class macro categories ──────────────────────────────
-- Pairs with the .pen design's category dropdown + "Add New Macro
-- Category" modal (frame mY6Az). Categories are rendered in the
-- MacrosWindow dropdown and act as the active filter for the macro
-- table; the active row also drives which set fires for the global
-- keybind dispatcher.
--
-- One Default row is auto-seeded per habbo on first MacrosManager.load
-- if no categories rows exist; pre-existing rp_macros rows have
-- category='Default' so they bind to the seeded row by name.
--
-- is_active is a single-active boolean (server enforces "at most one
-- per habbo" on UPDATE). Persisting it server-side means the active
-- category survives reloads + follows the user across devices.

CREATE TABLE IF NOT EXISTS `rp_macro_categories` (
    `id`         INT          NOT NULL AUTO_INCREMENT,
    `habbo_id`   INT          NOT NULL,
    `name`       VARCHAR(32)  NOT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `is_active`  TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_habbo_name` (`habbo_id`, `name`),
    KEY `idx_habbo` (`habbo_id`),
    CONSTRAINT `fk_rp_macro_categories_habbo`
        FOREIGN KEY (`habbo_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
