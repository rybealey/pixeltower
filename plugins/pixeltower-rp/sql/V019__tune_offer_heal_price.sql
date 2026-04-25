-- ─── Tune rp.offer.heal.price down from 100 → 5 ─────────────────────────
-- Applied after V018 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Context: original seed in PixeltowerRP.onEmulatorLoaded set the default
-- to "100" coins, which felt punishing during early playtesting. New
-- target is "5" — a nominal fee that still keeps the corp-treasury flow
-- exercised without pricing low-tier players out of healing.
--
-- Overwrites the value IFF it currently matches the prior default ("100");
-- any intentional staff override is preserved.

UPDATE `emulator_settings`
   SET `value` = '5'
 WHERE `key`   = 'rp.offer.heal.price'
   AND `value` = '100';
