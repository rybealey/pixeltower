-- ─── Tier 2: tune rp.fight.energy_per_hit to 0.33 ────────────────────────
-- Applied after V011 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Context: V011 moved the default from "10" to "0.2" (1 energy per 5 hits).
-- Per-swing drain at 0.2 felt imperceptible in playtesting, and some boxes
-- still carried "10" from before V011 ran (V011 only matched "10" exactly,
-- so anything mutated to e.g. "10.0" by staff tooling was left stale).
-- New target is 0.33 — 1 energy per ~3 hits, inside the 2–4 hit range.
--
-- Overwrites the value IFF it currently matches a known prior default
-- ("10", "10.0", "0.2"); any intentional staff override outside that set
-- is preserved.

UPDATE `emulator_settings`
   SET `value` = '0.33'
 WHERE `key`  = 'rp.fight.energy_per_hit'
   AND `value` IN ('10', '10.0', '0.2');
