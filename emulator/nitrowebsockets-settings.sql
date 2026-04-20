-- ms-websockets plugin settings, applied post base-database.sql import.
-- DOMAIN is substituted by scripts/seed-db.sh via envsubst.

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('websockets.whitelist', '${DOMAIN}'),
  ('websockets.enabled',   '1'),
  ('websockets.ssl',       '0')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
