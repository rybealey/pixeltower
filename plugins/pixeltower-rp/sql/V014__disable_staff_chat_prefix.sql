-- ─── Tier 2: disable staff chat prefix ───────────────────────────────────
-- Applied after V013 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Arcturus prepends `[<font color="%color%">%prefix%</font>] ` (the value
-- of emulator_settings['room.chat.prefix.format']) to every chat message
-- from a rank whose permissions.prefix column is non-empty. For pixeltower
-- we don't want staff speaking with `[ADM]`/`[MOD]`/etc. tags in-character
-- — it breaks the RP flavor. Clearing the format template disables the
-- prepend globally without touching permissions.prefix, so re-enabling is
-- a one-line revert if ever wanted.
--
-- deploy.sh restarts the emulator whenever need_sql=1 (see V013 commit),
-- so the format change picks up on the next deploy — no manual bounce.

UPDATE `emulator_settings`
   SET `value` = ''
 WHERE `key`  = 'room.chat.prefix.format';
