# Pixeltower RP — manual smoke checklist

Co-located with the plugin source so it moves with the code. Each tier's
checks live under a dated header below. Run against a local
`docker compose up` unless otherwise noted.

## Tier 2 — Fight + Paramedic

### Phase 1 — Schema + damage math + `adjustHp` + `:fighttest`

Covers `V009__tier2_fight.sql`, `V010__tier2_room_flags.sql`,
`DamageResolver`, `StatsManager.adjustHp`, `StatsManager.adjustEnergy`,
`FightTestCommand`.

**Setup**

1. `docker compose build emulator && docker compose up -d` — bakes the
   plugin JAR into the image and starts the stack.
2. `./scripts/seed-db.sh` — applies V009 + V010 migrations (idempotent).
3. Register two accounts; promote one to admin rank ≥
   `rp.admin.min_rank` (default 5) via housekeeping or direct
   `users.rank` update.

**Schema**

- [ ] `DESCRIBE rp_fights;` shows the 11 columns (`id`, `attacker_id`,
      `defender_id`, `room_id`, `started_at`, `ended_at`, `ender_reason`,
      `attacker_hits`, `defender_hits`, `total_damage_to_defender`,
      `total_damage_to_attacker`) with expected types.
- [ ] `DESCRIBE rp_fight_hits;` shows the 7 columns with `hit_at
      DATETIME(3)`.
- [ ] `DESCRIBE rp_downed_players;` shows PK on `habbo_id`, nullable
      `downed_in_room` / `downed_by_id` / `fight_id`, NOT NULL
      `respawn_at`.
- [ ] `DESCRIBE rp_room_flags;` shows PK on `room_id`, `no_pvp
      TINYINT(1)` default 0.
- [ ] `scripts/seed-db.sh` re-run is a no-op (all CREATE TABLE IF NOT
      EXISTS).

**Damage formula (JUnit)**

- [ ] Inside the plugin source tree, `mvn test` runs 9 tests and passes:
      min-1 clamp, variance bounds at ±20%, endurance floor, zero-endurance,
      variance-bound sweep, monotonic hit / endurance.

**`:fighttest` pipeline**

- [ ] Non-staff runs `:fighttest <user> 10` → ALERT whisper "You don't
      have permission to run :fighttest."
- [ ] Staff runs `:fighttest` with no args → ALERT whisper "Usage:
      :fighttest \<user\|x\> \<damage\>".
- [ ] Staff runs `:fighttest nonexistentuser 10` → ALERT whisper with the
      usual "no such user" message.
- [ ] Staff runs `:fighttest x 10` without having clicked anyone → ALERT
      whisper ("You have no target selected." or similar, from
      `NoTargetException`).
- [ ] Staff runs `:fighttest <alive user> 10` in a room → target's HP
      drops by 10 in `rp_player_stats`; `UpdatePlayerStatsComposer` fires
      (target's StatsHUD bar shrinks); staff shouts a STAFF-bubble RP
      emote "\* \<staff\> hits \<user\> for 10 damage (test)\*" visible
      to everyone in the room.
- [ ] Target HUD of any viewer watching this target updates its HP bar
      in real time.
- [ ] Staff runs `:fighttest x 999` on a full-HP target → HP goes to 0
      (clamped), target enters lay + freeze posture (DeathState.enter);
      `rp_downed_players` has a row for them with `downed_in_room =
      <current room>`, `respawn_at ≈ now + 180s`.
- [ ] Staff tries `:lay`, `:sit`, `:stand` while HP=0 → ALERT whisper
      "You are dead." (pre-existing behavior, verify still works).
- [ ] Staff runs `:restore x` on the downed target → target HP back to
      max, `DeathState.exit` fires (gets up), `rp_downed_players` row
      deleted.
- [ ] Staff runs `:fighttest x 10` again on the restored target → HP
      drops again; confirms the revive path cleanly reset state.
- [ ] Staff runs `:fighttest x 0` → ALERT whisper "Damage must be
      positive."
- [ ] Staff runs `:fighttest x abc` → ALERT whisper "Damage must be an
      integer."
- [ ] Staff runs `:fighttest x 10` twice in rapid succession → both hits
      land, cumulative HP drop (no cooldown yet — P2 adds it).

**`adjustHp` / `adjustEnergy` clamping**

- [ ] `:fighttest x 10` on a target at 1 HP → HP clamps to 0, downed
      path fires.
- [ ] `:fighttest x 10` on a target already at 0 HP → no-op (no extra
      `rp_downed_players` row; ON DUPLICATE KEY UPDATE refreshes the
      existing row's respawn_at).
- [ ] `:kill x` on an alive target → HP=0, `rp_downed_players` row
      inserted (this behavior is NEW in Phase 1 — `:kill` previously
      only set HP and entered DeathState).

**Offline edge cases**

- [ ] Staff runs `:fighttest <offline user> 10` → DB HP drops, no
      `DeathState.enter` (user offline), `rp_downed_players` row
      inserted with `downed_in_room = NULL` if their HP crossed to 0.
- [ ] Offline user logs in after being killed while offline → lands in
      lay+freeze pose (`DeathState.reapplyIfDead` via
      `attemptReapplyAfterReady`); `rp_downed_players` row still
      present (P4 will rehydrate the respawn timer on login).

### Phase 2 — `:hit` + engagement + safe-room gate

Covers `FightRange`, `RoomFlags`, `FightRules.canEngage`, `Engagement`,
`EngagementRegistry`, `FightService.hit`, `HitCommand`.

**Setup**

1. Two accounts: `A` (attacker) + `B` (defender). Both in the same room
   adjacent to each other.
2. Staff account `S` for :fighttest-driven knockouts + admin resets.

**Preconditions (`FightRules.canEngage`)**

- [ ] `A:` `:hit A` → ALERT whisper "You can't hit yourself."
- [ ] `A:` `:hit <offline user>` → ALERT whisper "… isn't online."
- [ ] `A:` `:hit B` when in different rooms → ALERT whisper "B isn't in
      this room."
- [ ] `A:` `:hit B` when ≥ 2 tiles apart (same room) → ALERT whisper "B
      is out of range."
- [ ] Insert `rp_room_flags (room_id, no_pvp) VALUES (<room>, 1)`; both
      in range → `A: :hit B` → ALERT whisper "This is a safe zone — you
      can't fight here." Delete the row or set no_pvp=0 to re-enable.
- [ ] `S: :kill A` → A downed. `A: :hit B` while A is downed → ALERT
      whisper "You're downed — you can't fight." (A can't type `:hit`
      anyway once the chat-lock from P3 lands; for P2 we just refuse the
      command.)
- [ ] `S: :kill B` → B downed. `A: :hit B` → ALERT whisper "B is already
      downed."
- [ ] Set `rp.fight.allow_corp_fratricide=false` (default). Hire A and B
      into the same corp. `A: :hit B` → ALERT whisper "You can't fight a
      member of your own corp." Flip to `true`, repeat → hit goes
      through.
- [ ] Zero out A's energy (`UPDATE rp_player_stats SET energy=0 WHERE habbo_id=A`)
      and `A: :hit B` → ALERT whisper "You're out of energy — rest a
      moment." Restore energy and the command goes through.
- [ ] Default `rp.fight.energy_per_hit=0.2`: `A: :hit B` 5 times (spaced
      ≥ 1s) — A's energy drops by exactly 1 between the 4th and 5th
      swing (fractional debt accumulator in FightService).
- [ ] `A: :hit B` twice within 1 second → second attempt → ALERT whisper
      "Too soon — swing cooldown NNNms left." (default 1000ms).

**Damage + engagement row lifecycle**

- [ ] `A: :hit B` (first swing, both full HP) → B's HP drops per formula,
      A's fractional energy debt advances (integer energy unchanged on
      the first swing at the default 0.2 rate). A shouts
      "\*hits B, causing N damage\*" (YELLOW emote, client splices A's
      name in so viewers see "A hits B, causing N damage"). A new
      `rp_fights` row exists with `attacker_id=A`, `defender_id=B`,
      `room_id=<current>`, `started_at≈now`, `ended_at IS NULL`,
      `attacker_hits=1`, `defender_hits=0`,
      `total_damage_to_defender=<final>`, `total_damage_to_attacker=0`.
      A matching `rp_fight_hits` row has `actor_id=A`, `target_id=B`,
      `raw_damage` + `final_damage` populated.
- [ ] `A: :hit B` again → same `rp_fights` row updated (attacker_hits=2,
      totals accumulated); another `rp_fight_hits` row.
- [ ] `B: :hit A` (retaliate) → same `rp_fights` row — this is the
      unordered-pair key — with `defender_hits=1`,
      `total_damage_to_attacker=<final>`.
- [ ] Wait 30s of no-hits → engagement reaped; `rp_fights.ended_at` set,
      `ender_reason='timeout'`. Verify no lingering active row for the
      pair in the DB: `SELECT id FROM rp_fights WHERE ended_at IS NULL
      AND (attacker_id=A OR defender_id=A);` empty.
- [ ] `A: :hit B` after a reaped engagement → a NEW `rp_fights` row is
      created (engagement identity is timeout-reset).
- [ ] `A` leaves the room mid-fight → active engagement terminated with
      `ender_reason='roomchange'`.
- [ ] `A` disconnects mid-fight → active engagement terminated with
      `ender_reason='logout'`.

**Knockout path**

- [ ] `A` sets skill_hit high via direct DB update
      (`UPDATE rp_player_stats SET skill_hit=50 WHERE habbo_id=A;`) so a
      single swing will KO B. `A: :hit B` → B's HP drops to 0, B enters
      lay+freeze (DeathState.enter), `rp_downed_players` row inserted
      with `downed_in_room=<current>`. The `rp_fights` row is finalized
      with `ender_reason='knockout'`. `rp_downed_players.fight_id` and
      `downed_by_id` are populated (linked post-hoc by
      `FightService.linkDownedToFight`).
- [ ] Post-KO, A: `:hit B` → ALERT whisper "B is already downed."
      (FightRules' downed check.)

**Restore clears state end-to-end**

- [ ] `S: :restore B` after a KO → B back to full HP, `rp_downed_players`
      row gone, `DeathState.exit` fires. New `A: :hit B` starts a
      fresh engagement row.

**Unordered-pair key sanity**

- [ ] A hits B first, engagement opens with attacker_id=A. B then hits A.
      Same engagement row — attacker/defender fields don't flip; B's hit
      is recorded as a `defender_hits` increment and the `rp_fight_hits`
      row records `actor_id=B, target_id=A`.

### Phase 3 — Downed-state whisper-only chat

Covers `DeathState.isDead`, the new `UserTalkEvent` listener in
`PixeltowerRP.onUserTalk`.

**Setup**

- Downed test subject `D` (force HP to 0 via `S: :kill D` or
  `S: :fighttest D 999`).
- A bystander `B` in the same room for whisper targeting.

**Chat gating**

- [ ] `D: hello world` (plain talk) → vanilla chat broadcast is
      cancelled; room bystanders see nothing from `D`; `D` gets an
      ALERT whisper back "You can only whisper while downed."
- [ ] `D: !hello` (shout, default prefix in Habbo chat or via client
      shortcut) → same outcome as talk: cancelled + ALERT whisper.
- [ ] `D:` whisper to `B` ("hello" via whisper input / `@B hello`
      depending on client convention) → whisper goes through normally;
      `B` sees it, no ALERT to `D`.
- [ ] `D: :stats` (command) → command executes normally (stats whisper
      back to `D`). Exempt from the chat lock because `isCommand=true`.
- [ ] `D: :balance` (command) → runs normally.
- [ ] `D: :hit <user>` (command) → the chat listener does NOT block it,
      but `FightRules.canEngage` refuses with "You're downed — you
      can't fight." (Tested in Phase 2.)

**Revive clears the lock**

- [ ] `S: :restore D` → `D` is back to full HP, `DeathState.exit` fires,
      `rp_downed_players` row cleared. `D: hello world` now broadcasts
      normally — the `isDead` check returns false.

**Alive players unaffected**

- [ ] An alive user `A` typing plain talk / shout → fires normally, no
      interception. Confirms the listener's `isDead` short-circuit
      doesn't leak.

**Offline death resurrection**

- [ ] `S: :fighttest <offline user> 999` (crosses to 0 while offline) →
      that user logs in, lands in lay+freeze (existing reapply), then
      tries to `talk` → cancelled + ALERT whisper. (`StatsManager.onLogin`
      populates cache before any UserTalkEvent can fire, so
      `isDead` reads cleanly.)

### Phase 4 — Respawn timer + `:respawn` escape

Covers `RespawnScheduler`, `RespawnTask`, `RespawnCommand`, the
`StatsManager.onDeath` scheduling hook, and the login rehydrate path in
`PixeltowerRP.rehydrateRespawnTimer`.

**Setup**

- Two rooms: a "field" room where players get KO'd, and a hospital
  room. Set `rp.medical.hospital_room_id = <hospital>` via
  `emulator_settings` (or the emulator CLI).
- For timer-window tests, flip `rp.medical.respawn_timeout_s` down to
  30 so iterations don't require 3-minute waits:
  `UPDATE emulator_settings SET value='30' WHERE key='rp.medical.respawn_timeout_s';`
- Ensure the test victim has enough credits to pay
  `rp.medical.respawn_penalty_credits` (default 500); also run a
  zero-balance case.

**Auto-respawn on timeout (online)**

- [ ] `S: :kill D` → `rp_downed_players` row has
      `respawn_at ≈ now + 30s`. Wait 30s → `D` teleported to the
      hospital room (`UserExitRoomEvent` on the field room,
      `UserEnterRoomEvent` on the hospital). `D`'s HP + energy at max.
      `rp_downed_players` row deleted. `rp_money_ledger` has a row
      `(habbo_id=D, delta=-500, reason='respawn_penalty')`. `D`'s
      DeathState cleared (stands, walkable). ALERT whisper to `D`: "You
      wake up at the hospital. A respawn fee has been deducted."

**`:respawn` voluntary early-out**

- [ ] `S: :kill D` then immediately `D: :respawn` → identical outcome
      to the timeout path, fires immediately. No remaining scheduled
      task afterwards (test indirectly: `D: :respawn` again → ALERT
      whisper "You're not downed.").
- [ ] Alive user: `A: :respawn` → ALERT whisper "You're not downed."

**Zero / low balance**

- [ ] Zero out `D`'s credits (`UPDATE users SET credits=0 WHERE id=D`),
      then `S: :kill D` + wait for respawn → `D` still respawns (HP
      max, teleport succeeds), `rp_money_ledger` has NO row for
      respawn_penalty (zero-balance short-circuit), a WARN line in
      emulator.log notes the skip.
- [ ] Set `D`'s credits to 200, penalty at 500 → debit is 200, balance
      ends at 0, ledger row has `delta=-200`.

**Hospital room unset**

- [ ] Set `rp.medical.hospital_room_id = 0` and rebuild the
      `emulator_settings` cache (restart emulator). `S: :kill D` +
      wait → `D` respawns with HP max + penalty debit, but stays in
      the field room (no teleport); a WARN line in emulator.log says
      "hospital_room_id is unset — skipping teleport".

**Revive cancels the scheduler**

- [ ] `S: :kill D`, then `S: :restore D` within the timeout window →
      `D` back to max HP immediately, `rp_downed_players` row gone,
      `RespawnScheduler` entry cleared. Wait out the original timer
      window → no teleport happens (cancelled future), no ledger
      penalty row.

**Disconnect during downed (timer keeps counting)**

- [ ] `S: :kill D` at T=0, `D` disconnects at T=5. Observation: the
      in-memory `ScheduledFuture` eventually fires at T=30 but with
      `D` offline — `RespawnTask.execute` falls into the offline path:
      `rp_player_stats.hp` + `energy` set to max, `rp_downed_players`
      row deleted, `rp_money_ledger` debit row written via
      `debitOffline`. `D` logs in → alive at max HP in the room they
      last exited.
- [ ] `S: :kill D` at T=0, `D` disconnects at T=5, `D` reconnects at
      T=15 (still inside the window). Expectation: login-rehydrate
      computes remaining ≈ 15s, re-arms the scheduler. After another
      ~15s, full respawn fires (teleport to hospital + penalty).
- [ ] `S: :kill D` at T=0, `D` disconnects at T=5, reconnects at T=60
      (past the window). Expectation: login-rehydrate computes
      remaining ≈ -30s, clamps to 2s floor, fires the respawn shortly
      after `D` lands in their home room.

**Emulator restart survives downed state**

- [ ] `S: :kill D` at T=0, `docker compose restart emulator` at T=10.
      The in-memory `ScheduledFuture` is lost but `rp_downed_players`
      persists. `D` is kicked off by the restart; on reconnect,
      login-rehydrate re-arms the timer. Respawn fires on the new
      schedule.

**Fight-attributed penalty ref_id**

- [ ] `A` hits `B` in an engagement until KO → `rp_downed_players.fight_id`
      populated (Phase 2 test). Wait for timeout → `rp_money_ledger.ref_id`
      equals the fight_id (carried through by
      `RespawnTask.readFightIdFromDownedRow`).

**Reverse — `:kill` without a fight**

- [ ] `S: :kill D` (no fight context, adjustHp route via killPlayer) →
      `rp_downed_players.fight_id = NULL`, respawn still fires, ledger
      row has `ref_id = NULL`.
