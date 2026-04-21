-- Tables the Arcturus dev branch code expects but ms4-base-database does not
-- provide. Extracted verbatim from the official sqlupdates/*.sql migration
-- files. Applied after emulator/base-database.sql in scripts/seed-db.sh.

CREATE TABLE IF NOT EXISTS `chat_bubbles` (
    type INT(11) PRIMARY KEY AUTO_INCREMENT COMMENT "Only 46 and higher will work",
    name VARCHAR(255) NOT NULL DEFAULT '',
    permission VARCHAR(255) NOT NULL DEFAULT '',
    overridable BOOLEAN NOT NULL DEFAULT TRUE,
    triggers_talking_furniture BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS `catalog_items_limited` (
    `catalog_item_id` INT NOT NULL,
    `number` INT NOT NULL,
    `user_id` INT NOT NULL DEFAULT '0',
    `timestamp` INT NOT NULL DEFAULT '0',
    `item_id` INT NOT NULL DEFAULT '0',
    UNIQUE (`catalog_item_id`, `number`)
);

CREATE TABLE IF NOT EXISTS `calendar_rewards` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `campaign_id` INT NOT NULL DEFAULT '0',
    `product_name` VARCHAR(128) NOT NULL DEFAULT '',
    `custom_image` VARCHAR(128) NOT NULL DEFAULT '',
    `credits` INT NOT NULL DEFAULT '0',
    `pixels` INT NOT NULL DEFAULT '0',
    `points` INT NOT NULL DEFAULT '0',
    `points_type` INT NOT NULL DEFAULT '0',
    `badge` VARCHAR(25) NOT NULL DEFAULT '',
    `item_id` INT NOT NULL DEFAULT '0',
    `subscription_type` VARCHAR(128) DEFAULT '',
    `subscription_days` INT NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`)
);
