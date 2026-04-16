-- Pre-seeds the NitroWebsockets plugin's emulator_settings rows so the first
-- emulator boot already knows what origins to accept, what port to listen on,
-- and which proxy header carries the real client IP.
--
-- Safe to re-run: each row is inserted with ON DUPLICATE KEY UPDATE.
-- Run AFTER importing emulator/base-database.sql and BEFORE starting
-- the emulator service for the first time.
--
-- Adjust websockets.whitelist to your actual domain(s). Wildcards OK
-- (e.g. '*.pixelworld.digital'), '*' means allow-all (not recommended).

INSERT INTO emulator_settings (`key`, `value`) VALUES
  ('websockets.whitelist', 'pixelworld.digital,www.pixelworld.digital,localhost'),
  ('ws.nitro.host',        '0.0.0.0'),
  ('ws.nitro.port',        '2095'),
  ('ws.nitro.ip.header',   'X-Forwarded-For')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
