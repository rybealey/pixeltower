-- ─── Tier 2: reset stale rp.fight.energy_per_hit ──────────────────────────
-- Applied after V010 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Context: rp.fight.energy_per_hit shipped as an int ("10") in the initial
-- Tier 2 combat commit, then moved to a fractional double ("0.2") once
-- per-swing cost proved too steep. PluginConfig.register() only writes the
-- default value on a MISSING row, so any environment that booted under the
-- old "10" kept it in emulator_settings and continued draining ~10 energy
-- per swing against the new formula.
--
-- This migration resets the value IF AND ONLY IF it still matches the old
-- "10" default — any staff-intentional override is preserved.

UPDATE `emulator_settings`
   SET `value` = '0.2'
 WHERE `key`  = 'rp.fight.energy_per_hit'
   AND `value` = '10';
