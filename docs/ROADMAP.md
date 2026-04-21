# Pixeltower RP roadmap (v1: Tier 1-3)

> Scope for the first major milestone: convert the base retro into a
> playable RP server with a shipping foundation (stats, economy, corps),
> a working fight system (with paramedic revive), and one fully-playable
> corp vertical (Police) proving the corp framework end-to-end. Subsequent
> milestones layer on bounties, gym, gangs, drugs, farming, clothing,
> heists, and the casino.

## 1. Architecture at a glance

One Maven plugin JAR — `pixeltower-rp-<version>.jar` — lives in the
monorepo at `plugins/pixeltower-rp/`. Built by the emulator image's
Maven stage (same pattern as `ms-websockets` today), baked in, copied
into `/emulator/plugins/` on container start.

Touches three layers:

| Layer | Lives in | Responsibilities |
|---|---|---|
| Emulator (Java) | `plugins/pixeltower-rp/` | Combat, HP/energy ticks, commands, corp paychecks, DB reads/writes, Nitro WS plumbing |
| DB (MariaDB) | `plugins/pixeltower-rp/sql/` → `scripts/seed-db.sh` | New `rp_*` tables + migrations |
| Website (Laravel) | `atomcms/` overlay in a new theme add-on | Corp rosters, money-ledger viewer, leaderboards, housekeeping |
| Game UI (React) | `nitro/` patches applied in `scripts/build-client.sh` | Stats HUD, fight-range indicator, corp panel, arrest UI |

Cross-layer contracts:

- **Plugin ↔ DB** — direct via Arcturus's HikariCP (`Emulator.getDatabase()`).
- **Plugin ↔ client** — custom incoming/outgoing message IDs registered with `Emulator.getGameServer().getPacketManager()`, consumed by Nitro widgets.
- **AtomCMS ↔ DB** — plain Eloquent models on the same `rp_*` tables. Use transactions + row-level locks where contended (money ledger).

## 2. Plugin skeleton (`plugins/pixeltower-rp/`)

```
pixeltower-rp/
├── pom.xml                          # groupId org.pixeltower, depends on com.eu.habbo:Habbo:3.0.0
├── plugin.json                      # name, version, author, main-class
├── sql/
│   ├── V001__tier1_stats.sql        # rp_player_stats, rp_money_ledger, rp_corporations, rp_corporation_ranks, rp_corporation_members, rp_corporation_stock
│   ├── V002__tier2_fight.sql        # rp_fights, rp_downed_players
│   └── V003__tier3_police.sql       # rp_arrests, rp_jail_sentences
├── src/main/java/org/pixeltower/rp/
│   ├── PixeltowerRP.java            # plugin entrypoint (init/dispose)
│   ├── core/
│   │   ├── config/                  # reads plugin-config rows from emulator_settings
│   │   ├── events/                  # @EventHandler listeners for Arcturus lifecycle events
│   │   └── tasks/                   # scheduled timers (paycheck tick, HP regen, jail release)
│   ├── stats/                       # Tier 1
│   │   ├── StatsManager.java
│   │   ├── StatsCache.java          # in-memory, write-through to rp_player_stats
│   │   └── commands/StatsCommand.java
│   ├── economy/                     # Tier 1
│   │   ├── MoneyLedger.java
│   │   └── commands/TransferCommand.java
│   ├── corp/                        # Tier 1
│   │   ├── CorporationManager.java
│   │   ├── RankPermission.java
│   │   ├── commands/{Hire,Fire,Promote,ClockIn,Paycheck}.java
│   │   └── tasks/PaycheckTask.java
│   ├── fight/                       # Tier 2
│   │   ├── FightRange.java          # tile-to-tile melee engine
│   │   ├── HitTiming.java           # 500ms window model
│   │   ├── FadeTiming.java
│   │   ├── DamageResolver.java
│   │   ├── DownState.java
│   │   └── commands/{Hit,Fade,Challenge}.java
│   ├── medical/                     # Tier 2
│   │   ├── ReviveManager.java
│   │   ├── RespawnTask.java         # if no paramedic in N minutes → hospital respawn + penalty
│   │   └── commands/ReviveCommand.java
│   └── police/                      # Tier 3
│       ├── UniformCheck.java        # on-duty iff wearing uniform (badge + clothing set)
│       ├── PatrolState.java
│       ├── ArrestManager.java       # preconditions: suspect downed OR bounty OR suspicion threshold
│       ├── JailManager.java         # jail-room teleport, sentence timer, release
│       └── commands/{Arrest,Jail,Release}.java
└── src/main/resources/
    └── pixeltower-config.yml.default  # player-visible tunables
```

## 3. DB schema (Tier 1-3, abridged)

Canonical IF-NOT-EXISTS, idempotent. Applied after `base-database.sql`
by extending `scripts/seed-db.sh` to loop over `plugins/pixeltower-rp/sql/*.sql`.

```sql
-- Tier 1
CREATE TABLE rp_player_stats (
  habbo_id              INT PRIMARY KEY,
  hp                    INT NOT NULL DEFAULT 100,
  max_hp                INT NOT NULL DEFAULT 100,
  energy                INT NOT NULL DEFAULT 100,
  max_energy            INT NOT NULL DEFAULT 100,
  level                 INT NOT NULL DEFAULT 1,
  xp                    INT NOT NULL DEFAULT 0,
  skill_points_unspent  INT NOT NULL DEFAULT 0,
  skill_hit             INT NOT NULL DEFAULT 1,
  skill_endurance       INT NOT NULL DEFAULT 1,
  skill_stamina         INT NOT NULL DEFAULT 1,
  on_duty_corp_id       INT NULL,
  FOREIGN KEY (habbo_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE rp_money_ledger (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  habbo_id      INT NOT NULL,
  delta         BIGINT NOT NULL,                 -- signed, in currency base units
  balance_after BIGINT NOT NULL,                 -- snapshot for audit
  reason        VARCHAR(64) NOT NULL,            -- 'paycheck', 'bounty_claim', 'casino_win', ...
  ref_id        BIGINT NULL,                     -- opaque FK (fight_id, arrest_id, etc.)
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_habbo_created (habbo_id, created_at)
);

CREATE TABLE rp_corporations (
  id                  INT PRIMARY KEY AUTO_INCREMENT,
  corp_key            VARCHAR(32) UNIQUE NOT NULL,   -- 'police', 'defacto', 'paramedic', 'casino'
  name                VARCHAR(64) NOT NULL,
  hq_room_id          INT NULL,                       -- nav click → HQ
  paycheck_interval_s INT NOT NULL DEFAULT 1800,
  stock_capacity      INT NOT NULL DEFAULT 1000
);

CREATE TABLE rp_corporation_ranks (
  corp_id      INT NOT NULL,
  rank_num     INT NOT NULL,
  title        VARCHAR(48) NOT NULL,
  salary       BIGINT NOT NULL DEFAULT 0,
  permissions  BIGINT NOT NULL DEFAULT 0,        -- bitmap; see RankPermission.java
  PRIMARY KEY (corp_id, rank_num)
);

CREATE TABLE rp_corporation_members (
  corp_id    INT NOT NULL,
  habbo_id   INT NOT NULL,
  rank_num   INT NOT NULL,
  hired_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (corp_id, habbo_id),
  INDEX idx_habbo (habbo_id)
);

CREATE TABLE rp_corporation_stock (
  corp_id   INT NOT NULL,
  item_key  VARCHAR(64) NOT NULL,
  quantity  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (corp_id, item_key)
);
```

Tier 2-3 tables (`rp_fights`, `rp_downed_players`, `rp_arrests`,
`rp_jail_sentences`) follow the same pattern and are defined in their
respective `V00N__*.sql` files.

## 4. Nitro UI strategy

Every in-client UI surface (stats HUD, fight indicators, corp panel,
arrest menu) is a patched Nitro widget. `scripts/build-client.sh`
already has the pattern: idempotent `sed` against `nitro/` + a yarn
rebuild. Extend it for bigger patches:

- `nitro-patches/` directory in the monorepo containing either:
  - **Small diffs**: `*.patch` files applied by `git apply --whitespace=nowarn` in `build-client.sh` before `yarn build:prod`. Idempotent via `git apply --check` short-circuit.
  - **New widgets**: plain TSX files dropped into `nitro/src/components/pixeltower-rp/` before build.

Tier 1 starts minimal — one new widget `StatsHUD.tsx`, rendered
alongside the existing `AvatarInfo` widget, driven by a new incoming
packet `UpdatePlayerStatsMessageComposer` the plugin sends on login
and on every stat change.

## 5. WS message protocol (plugin ↔ client)

Custom packet IDs registered in `PixeltowerRP.init()`. Convention:
`0x1000`-`0x1FFF` range, well above Arcturus's built-in space.

**Tier 1:**
- `0x1001 UpdatePlayerStatsMessageComposer` (S→C): `{hp, maxHp, energy, maxEnergy, level, xp, skillPoints, hitSkill, enduranceSkill, staminaSkill}`
- `0x1002 OpenCorpPanelMessageComposer` (S→C): `{corpKey, members[], ranks[], balance}`
- `0x1003 CorpActionMessageEvent` (C→S): `{action: "hire"|"fire"|"promote", targetHabboId, rankNum?}`

**Tier 2:**
- `0x1010 FightRangeEnterMessageComposer` (S→C): `{opponentHabboId, range: "in"|"out"}`
- `0x1011 FightHitEventMessageEvent` (C→S): `{timestamp}` — timing judged server-side
- `0x1012 FightFadeEventMessageEvent` (C→S): `{timestamp, direction}`
- `0x1013 PlayerDownedMessageComposer` (S→C): `{habboId, downedAt}`

**Tier 3:**
- `0x1020 ArrestInitiateMessageEvent` (C→S): `{suspectHabboId}`
- `0x1021 JailScreenMessageComposer` (S→C): `{releasedAt, reason}`

## 6. AtomCMS surfaces

Add a sub-theme overlay rather than patching atom or dusk directly —
keeps our changes contained and reapplicable when AtomCMS updates:

- `atomcms/resources/themes/pixeltower/` — new theme; set `setting('theme')='pixeltower'` post-install.
- `atomcms/routes/pixeltower.php` — additional routes: `/corporation/{key}`, `/leaderboard`, `/bounties`, `/my/stats`.
- `atomcms/app/Models/Rp/{PlayerStats,Corporation,CorporationMember,MoneyLedger}.php` — thin Eloquent models over `rp_*`.

Tier 1 scope inside AtomCMS is just:
- `/my/stats` — player's own stat sheet.
- `/corporation/{key}` — read-only roster with rank + hired-at.
- `/housekeeping/corporations` — staff-only CRUD for corps, ranks, members. Reuses AtomCMS's housekeeping permission system.

## 7. Configuration + tunables

Game-design constants live in `emulator_settings` (existing table) under `rp.*` keys:

```
rp.fight.hit_window_ms=500
rp.fight.fade_window_ms=500
rp.fight.energy_per_hit=10
rp.fight.damage_variance=0.2
rp.medical.respawn_timeout_s=180
rp.medical.hospital_room_id=0
rp.police.jail_room_id=0
rp.corp.paycheck_tick_s=1800
rp.stats.hp_regen_interval_s=30
rp.stats.hp_regen_amount=2
```

Read once at plugin init, cached, refresh on `:rpreload` admin
command. AtomCMS housekeeping exposes the same rows for edit.

## 8. Build + deploy integration

`docker/emulator/Dockerfile` already has a stage that builds
`ms-websockets`. Add a parallel stage for `pixeltower-rp` (same
`mvn -DskipTests package` pattern), copy the resulting JAR into the
runtime image's `/emulator/plugins-baked/`, and let the existing
entrypoint copy it into the volume-backed plugins dir on container
start. Zero changes to scripts — just add the stage.

For SQL: extend `scripts/seed-db.sh` to loop over `plugins/pixeltower-rp/sql/*.sql`
right after `missing-tables.sql`.

For Nitro widgets + AtomCMS overlay: patched into the existing
`build-client.sh` / `atom-builder` flows.

## 9. Testing approach

- **Unit tests** (JUnit 5 in the plugin Maven module) — damage formula, rank permission bits, money ledger invariants (sum of deltas == balance_after), fight-timing windows.
- **Integration tests** against the live local stack — shell scripts in `plugins/pixeltower-rp/test/` that run against `docker compose up`, use RCON to inject test users + assert DB state.
- **Manual smoke checklist** per tier — kept in `plugins/pixeltower-rp/TESTING.md`. Examples: "T1-01: register user → expect `rp_player_stats` row with default HP=100"; "T2-05: two players in fight range, attacker hits within 500ms of defender-stop → defender HP drops by `hit_skill * 1.0 ± 20%`".

## 10. Sequencing (concrete per-tier milestones)

### Tier 1 — Foundation (target: 5-8 work sessions)

- [ ] `plugins/pixeltower-rp/` Maven skeleton, wired into `docker/emulator/Dockerfile`
- [ ] `V001__tier1_stats.sql` created, `seed-db.sh` applies it
- [ ] `StatsManager` — on-login fetch/create row, in-memory cache, write-through on change
- [ ] `:stats` command (server-rendered text for v0, HUD later)
- [ ] `MoneyLedger.credit(habboId, delta, reason, refId)` / `debit(...)` with row-level lock
- [ ] `:balance` + `:transfer` commands
- [ ] `CorporationManager` — load corps + ranks at boot, member cache
- [ ] `:hire` / `:fire` / `:promote` commands, permissions enforced via `RankPermission` bits
- [ ] `PaycheckTask` — scheduled every `rp.corp.paycheck_tick_s`, credits all active members their rank salary
- [ ] AtomCMS: `/my/stats`, `/corporation/{key}`, housekeeping CRUD for corps
- [ ] Nitro: `StatsHUD.tsx` widget + `UpdatePlayerStatsMessageComposer` plumbing
- [ ] Smoke: register 2 users, hire one to a corp, wait a tick, verify paycheck row in `rp_money_ledger`

### Tier 2 — Fight + Paramedic (target: 6-10 sessions)

- [ ] `V002__tier2_fight.sql`
- [ ] `FightRange` — detect "in range" / "out of range" transitions, emit `0x1010`
- [ ] `HitTiming` + `FadeTiming` — server-authoritative clocks, tolerant to 30-80ms client latency
- [ ] `DamageResolver` — HP deltas, energy deduction, log to `rp_fights`
- [ ] `DownState` — at HP=0, lock movement/chat to whisper, write `rp_downed_players`
- [ ] `ReviveManager` — paramedic `:revive <target>` within N minutes restores HP to 50%
- [ ] `RespawnTask` — timeout → teleport to `rp.medical.hospital_room_id`, money penalty
- [ ] Nitro: fight-range indicator overlay, hit/fade timing rings, downed overlay
- [ ] Smoke: A attacks B, times hit, B's HP drops; B hits 0, paramedic revives within window

### Tier 3 — Police vertical (target: 6-10 sessions)

- [ ] `V003__tier3_police.sql`
- [ ] `UniformCheck` — on-duty iff wearing badge `POLICE` + clothing set N; `:clockin`/`:clockout` toggle
- [ ] `ArrestManager` — preconditions: suspect downed OR has bounty OR crime_suspicion ≥ threshold
- [ ] `JailManager` — teleport to jail room, sentence timer, release event
- [ ] Nitro: arrest confirmation widget, jail countdown overlay
- [ ] AtomCMS: `/police/roster`, `/police/arrests` (audit)
- [ ] Smoke: officer clocks in → suspect downed → `:arrest` succeeds → suspect in jail room for sentence → auto-release + return to last location

## 11. Beyond v1 (Tier 4+)

These tiers build on the Tier 1-3 foundation. Rough order:

- **Tier 4**: Bounty system; Gym levelling (spend time → XP → skill points); Basic crime (shoplift, pickpocket) with police suspicion scoring.
- **Tier 5**: Gangs (group-backed, turf ownership, member hierarchy); Drug growing / guarding / effects; Farming corps (stock → corp).
- **Tier 6**: Clothing economy end-to-end (DeFacto corp + employee-gated sales); Heists (multi-player coordinated, gang-required, police-interceptable); Casino corp (dice, higher/lower card game).

Each of these depends on decisions made in Tier 1-3. They get their
own planning pass before code, following the same
Context → Scope → DB → Plugin code → UI → Testing structure.

## 12. Decisions locked for v1

From the planning session that produced this roadmap:

- **Plugin layout**: single Maven plugin, sub-packages per system.
- **Starting point**: clean build from scratch against Arcturus dev + Nitro v2.
- **v1 scope**: Tier 1-3 (foundation + fight + police vertical).
- **Repo layout**: monorepo — `plugins/` under `pixeltower`.
- **Currency**: single balance for v1 (column in `users` or new table TBD per Tier 1 question 1). Dual-currency deferred to Tier 4 when bounties/black-market land.
- **Corp membership**: one-at-a-time via `UNIQUE(habbo_id)` on `rp_corporation_members` (relaxable later).
