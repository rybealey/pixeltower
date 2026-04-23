-- ─── V006: free up `:kill` for the pixeltower KillCommand ────────────────
-- Arcturus ships `:kill` as an alias for `:disconnect` via the emulator_texts
-- row below. That takes priority over our plugin command and turns `:kill`
-- into a kick. We already have `:disconnect` / `:dc` for the kick behaviour,
-- so strip the `kill` alias here.
--
-- Idempotent: REPLACE is a no-op once the alias is gone.

UPDATE `emulator_texts`
SET `value` = REPLACE(REPLACE(`value`, ';kill', ''), 'kill;', '')
WHERE `key` = 'commands.keys.cmd_disconnect';
