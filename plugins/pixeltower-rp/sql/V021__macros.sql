-- ─── V021: Player macros ──────────────────────────────────────────────
-- Per-player keybind → command bindings, persisted server-side so they
-- follow the user across browsers/devices. Surfaces in the Macros
-- window (.designs/client_ui.pen frame 59QGF) and is consumed by the
-- client-side MacrosKeybindListener which fires the matching command
-- through the chat input pipeline when the bound key/mouse button is
-- pressed outside any text input.
--
-- `keybind` uses the canonical encoding from
-- nitro/src/api/pixeltower/MacroBindingFormat.ts:
--     `Ctrl+Shift+Alt+Meta+` (any subset, in this order) followed by
--     `Key:<code>` | `Mouse4` | `Mouse5` | `MouseMiddle`.
--
-- One row per (habbo_id, keybind). Saving the same keybind a second time
-- replaces the bound command (handled by the SaveMacroHandler upsert).
--
-- `category` is rendered as the tab header in the UI. Phase 1 only ships
-- a "Default" tab — the column is here so introducing more tabs later
-- doesn't require a migration.

CREATE TABLE IF NOT EXISTS `rp_macros` (
    `id`         INT          NOT NULL AUTO_INCREMENT,
    `habbo_id`   INT          NOT NULL,
    `keybind`    VARCHAR(64)  NOT NULL,
    `command`    VARCHAR(128) NOT NULL,
    `category`   VARCHAR(32)  NOT NULL DEFAULT 'Default',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_habbo_keybind` (`habbo_id`, `keybind`),
    KEY `idx_habbo` (`habbo_id`),
    CONSTRAINT `fk_rp_macros_habbo_id`
        FOREIGN KEY (`habbo_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
