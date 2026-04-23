-- ─── V007: functional furniture dispatch table ───────────────────────────
-- Maps an items_base row to a server-side action that fires when the user
-- walks on (or clicks) a placed instance of that base item. Walk-on /
-- click detection is done by InteractionRpFunctional, which is registered
-- against the items_base.interaction_type string 'rp_functional' during
-- EmulatorLoadItemsManagerEvent. To make a furni functional, BOTH must
-- happen: items_base.interaction_type = 'rp_functional' AND a row exists
-- here keyed by item_base_id.
--
-- action_payload is intentionally opaque — the dispatcher interprets it
-- per action_type (e.g. for open_avatar_editor it's the link suffix
-- 'show' or 'toggle'; empty defaults to 'show').
--
-- Idempotent. No rows seeded — populated per-furni via :rpreload (future)
-- or direct SQL inserts.

CREATE TABLE IF NOT EXISTS `rp_functional_furniture` (
    `id`              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `item_base_id`    INT UNSIGNED  NOT NULL,
    `trigger_type`    ENUM('walk_on','click') NOT NULL DEFAULT 'walk_on',
    `action_type`     VARCHAR(64)   NOT NULL,
    `action_payload`  VARCHAR(255)  NOT NULL DEFAULT '',
    `cooldown_ms`     INT UNSIGNED  NOT NULL DEFAULT 1500,
    `enabled`         TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_item_trigger` (`item_base_id`, `trigger_type`),
    KEY `idx_enabled` (`enabled`),
    CONSTRAINT `fk_funcfurni_base` FOREIGN KEY (`item_base_id`)
        REFERENCES `items_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
