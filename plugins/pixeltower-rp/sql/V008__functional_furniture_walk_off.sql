-- ─── V008: extend rp_functional_furniture trigger_type ENUM ─────────────
-- Adds 'walk_off' so a single furni can pair an open-on-walk-on row with a
-- close-on-walk-off row (e.g. the Runway Dressing Room: enter → open Change
-- Looks; leave → close it). V007's CREATE TABLE IF NOT EXISTS is a no-op
-- once the table exists, so the new value has to come in via ALTER.
--
-- Idempotent at the schema level: re-running ALTER COLUMN to the same
-- definition is a no-op in MariaDB.

ALTER TABLE `rp_functional_furniture`
    MODIFY COLUMN `trigger_type` ENUM('walk_on','click','walk_off')
    NOT NULL DEFAULT 'walk_on';
