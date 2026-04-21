-- ─── V005: persist last-room tile + rotation ─────────────────────────────
-- Pairs with users.home_room — when a user logs in, the plugin teleports
-- them to their home room AND restores the exact tile / facing they were
-- on when they last left.
--
-- Idempotent. No rows seeded — populated lazily on the first UserExitRoomEvent.

CREATE TABLE IF NOT EXISTS `rp_player_home_position` (
    `habbo_id`  INT NOT NULL,
    `x`         INT NOT NULL,
    `y`         INT NOT NULL,
    `rotation`  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`habbo_id`),
    CONSTRAINT `fk_home_pos_habbo` FOREIGN KEY (`habbo_id`)
        REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
