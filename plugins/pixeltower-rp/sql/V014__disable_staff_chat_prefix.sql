-- ─── Tier 2: disable staff chat prefix ───────────────────────────────────
-- Applied after V013 by scripts/seed-db.sh. Idempotent — safe to re-run.
--
-- Arcturus prepends `[<font color="%color%">%prefix%</font>] ` (the value
-- of emulator_settings['room.chat.prefix.format']) to every chat message
-- from a rank whose permissions.prefix column is non-empty. For pixeltower
-- we don't want staff speaking with `[ADM]`/`[MOD]`/etc. tags in-character
-- — it breaks the RP flavor.
--
-- Two-pronged clear, because only touching the format template isn't
-- sufficient: Arcturus's PluginConfig re-registers the format default on
-- every emulator boot and clobbers our empty override. Clearing the
-- per-rank prefix columns ALSO skips the prepend entirely regardless of
-- template state, so this survives future emulator restarts.
--
-- deploy.sh restarts the emulator whenever need_sql=1 (see V013 commit).

UPDATE `emulator_settings`
   SET `value` = ''
 WHERE `key`  = 'room.chat.prefix.format';

UPDATE `permissions`
   SET `prefix` = '', `prefix_color` = ''
 WHERE `prefix` <> '' OR `prefix_color` <> '';
