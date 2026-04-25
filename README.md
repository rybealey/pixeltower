# Pixeltower

[![Status](https://img.shields.io/badge/Status-Pre--Alpha-orange)]()
[![Current Tier](https://img.shields.io/badge/Roadmap-Tier%201%20of%203%20shipped-brightgreen)](docs/ROADMAP.md)
[![Stack](https://img.shields.io/badge/Built%20on-Arcturus%20%2B%20Nitro-informational)](DOCUMENTATION.md)
[![Client](https://img.shields.io/badge/Client-Nitro%20v2-blueviolet)](https://github.com/billsonnn/nitro-react)
[![Emulator](https://img.shields.io/badge/Emulator-Arcturus%20Morningstar-yellow)](https://git.krews.org/morningstar/Arcturus-Community)
[![Host](https://img.shields.io/badge/Host-pixeltower.digital-blue)]()

**Pixeltower** is a roleplay-first Habbo-inspired pixel world in active development. Jump into
the hotel, roll stats and skills, take a corporate job, earn coins — and, as the tiers land,
fight over turf, collect bounties, and pull off heists. This README is the living changelog:
what you can do today, what's in flight, and what's queued next.

> **Current stage:** Tier 1 foundation complete. Tier 2 (combat + paramedic) in design.
> **Live at:** `pixeltower.digital` — launch imminent, watch this page for go-live.

---

## The vision

A persistent roleplay sandbox where every player is more than a pixel avatar:

- You have **stats** — HP, energy, XP, and skills that level up over time.
- You carry **coins**, hold a **bank account**, and earn a **salary** at your job.
- You can be **hired**, **promoted**, **fired**, clocked in, clocked out.
- Eventually: you can **fight**, **arrest**, **be arrested**, post **bounties**, run
  **drugs**, join a **gang**, own **turf**, plan a **heist**, or cash in at the **casino**.

Pixeltower's long-term goal is a living in-world economy where every corporation, crime,
and coin is connected. Tier 1 laid the plumbing. Tier 2+ is where it gets loud.

---

## What you can do today

Everything in this section is **live** in the current server build.

### Your character

- **Register & log in** via the website; your last room, tile, and rotation are restored
  on every login.
- **Stats** — every player has HP, Energy, Level, XP, and three skills (Hit, Endurance,
  Stamina). Inspect yours with `:stats`.
- **Wallet + Bank** — separate coin wallet and bank balance. Daily interest ticks on idle
  bank balances.

### In-game UI

- **Player HUD** (top-left) — avatar frame, HP/energy bars, wanted-stars meter.
- **Status HUD** (top-right) — current room, hotel time, live online-user count, coin
  balance, diamond balance, and VIP club days remaining. Slate-blue pill stack that
  doubles as at-a-glance context for the whole session.
- **Target HUD** — click any player's profile to surface their live stats next to yours,
  so you know who you're dealing with before you roll up on them.
- **Toolbar HUD** (bottom) — chat field with a one-click chat-log shortcut, phone button
  that opens the friends panel, and a settings button. Staff additionally get a grouped
  chip of Navigator / Shop / Inventory / Mod Tools shortcuts.
- **Drawer HUD** (left edge) — slide-out panel for quick actions.
- **Action emotes** — when anyone wraps chat in asterisks (`*dusts off jacket*`), the
  client renders it as a proper RP emote with the speaker's name spliced in.
- **Bespoke chrome** — hotel-view lobby is bypassed (users drop straight into a home
  room), door tiles are entry-only so you can't accidentally walk yourself out of the
  world, and every dialog window carries the Pixel slate-blue palette instead of
  upstream teal. Staff-only `:roomsettings` and `:floorplan` commands re-expose the
  tools that used to live on the removed bottom-left widget.

### Commands

| Command | Available to | What it does |
|---|---|---|
| `:balance` / `:bal` | Everyone | Whispers your current bank balance |
| `:balance hide` | Everyone | Same, but shown only to you (info bubble) |
| `:give <user\|x> <amount>` | Everyone | Transfer coins from wallet to wallet |
| `:openaccount` | Everyone | Open a bank account |
| `:deposit <amount>` | Everyone | Move coins wallet → bank |
| `:withdraw <amount>` | Everyone | Move coins bank → wallet |
| `:transfer <user\|x> <amount>` | Everyone | Bank-to-bank coin transfer |
| `:stats` | Everyone | Shows your HP, energy, level, XP, skills |
| `:target <user>` | Everyone | Set your active `x` target for any command |
| `:hire <user\|x> <corp> <rank>` | Corp owner | Add a player to your corporation |
| `:fire <user\|x>` | Corp owner | Remove an employee |
| `:promote <user\|x> <rank>` | Corp owner | Change an employee's rank |
| `:startwork` / `:stopwork` | Employees | Clock in / clock out (auto-clockout on idle) |
| `:quitjob` | Employees | Leave your corporation voluntarily |
| `:superhire <user\|x> <corp_key> [rank]` | Staff (rank 5+) | Force-hire any user into any corp (rank defaults to entry) |
| `:superfire <user\|x>` | Staff (rank 5+) | Force-remove any user from their corp |
| `:award <user\|x> <currency> <amount>` | Staff (rank 5+) | Audited coin/bank adjustment |
| `:revive <user\|x>` | Staff (rank 5+) | Full HP + energy refill |
| `:roomsettings` | Staff | Opens the room settings window for the current room |
| `:floorplan` | Staff | Opens the floor-plan editor for the current room |
| `:commands` | Everyone | Opens a searchable reference of every command you can use |

> **Protip:** any command that takes a `<user>` accepts `x` as shorthand for your last
> clicked target. `:give x 500` is faster than typing a username every time.
>
> Forgot a command? `:commands` opens a searchable window filtered by tab
> (General / Job / VIP / Staff) — tabs only appear for statuses you actually have.

### Corporations — how the jobs work

- Corporations have named **ranks**, each with its own **salary** and **permissions**.
- A **paycheck task** ticks every minute as a heartbeat; when you've accumulated enough
  on-duty minutes, your rank's salary is auto-credited.
- If you go **idle** (AFK), you're automatically clocked out so you don't keep drawing
  a salary while away from the keyboard.

### Staff tooling

- `:award` broadcasts a public STAFF-bubble RP emote so every adjustment is visible to
  the room. Recipient also gets a discreet info-bubble confirmation. All adjustments
  write to an audit ledger.
- `:revive` pushes the target's HP + energy back to max, broadcasts a STAFF emote, and
  shows the target a private info-bubble.

---

## What's coming next

Pulled from [docs/ROADMAP.md](docs/ROADMAP.md). Order is roughly the build order.

### Tier 2 — Combat + Paramedic

- [ ] Tile-to-tile melee fight range detection
- [ ] 500ms server-authoritative hit window
- [ ] Fade (block/dodge) timing with directional inputs
- [ ] Damage resolver driven by skill + level + RNG variance
- [ ] Energy cost per hit; running low locks you out
- [ ] Downed state at HP 0 — movement + chat locked to whispers
- [ ] Paramedic `:revive <target>` restores 50% HP inside the window
- [ ] Hospital respawn + money penalty if no paramedic arrives in time
- [ ] Nitro UI: fight-range indicator overlay, hit/fade timing rings, downed overlay

### Tier 3 — Police vertical

- [ ] Uniform check — on-duty only when wearing badge + clothing set
- [ ] Arrest preconditions: suspect downed OR has bounty OR suspicion threshold
- [ ] `:arrest` → jail room teleport, sentence timer, auto-release + return
- [ ] In-client arrest confirmation widget + jail countdown overlay
- [ ] Audit pages on the website: police roster + arrest log

### Tier 4 — The economy wakes up

- [ ] **Bounties** — post coins on a player's head; killer claims
- [ ] **Gym** — time spent levelling → skill points to spend
- [ ] **Petty crime** — shoplift, pickpocket, each raises police suspicion

### Tier 5 — Groups and vice

- [ ] **Gangs** — group-backed turf ownership + member hierarchy
- [ ] **Drugs** — grow, guard, sell; temporary stat effects + heat
- [ ] **Farming corps** — stock-generating corps feed the market

### Tier 6 — Endgame content

- [ ] **Clothing economy** — DeFacto (clothing corp) with employee-gated shop
- [ ] **Heists** — multi-player coordinated jobs, gang-required, police-interceptable
- [ ] **Casino corp** — house-run dice, higher-lower, and card tables

Full architectural breakdown, DB schema previews, and per-tier smoke tests live in
[docs/ROADMAP.md](docs/ROADMAP.md).

---

## How to play

Pre-launch. The site at `https://pixeltower.digital/` is not yet accepting public
registrations — launch is imminent.

Want to get notified when the doors open? The easiest way right now is to watch this
repository (GitHub "Watch → Releases only") — a v0.1.0 release tag will drop the day
registrations open.

---

## Built on open source

Pixeltower stands on a stack of excellent open-source projects:

- [Arcturus Morningstar](https://git.krews.org/morningstar/Arcturus-Community) — the emulator
- [Nitro React](https://github.com/billsonnn/nitro-react) — the game client
- [AtomCMS](https://github.com/ObjectRetros/atomcms) — the website
- [ms-websockets](https://git.krews.org/nitro/ms-websockets) — WebSocket bridge
- [nitro-imager](https://github.com/billsonnn/nitro-imager) — avatar image service
- [nitro-converter](https://github.com/billsonnn/nitro-converter) — SWF → `.nitro` bundler

The custom roleplay layer (`plugins/pixeltower-rp/`) and Nitro client patches
(`nitro-patches/`) are built specifically for Pixeltower.

---

## For developers

Setup, bootstrap, Docker, production deploy, and Cloudflare notes are in
[DOCUMENTATION.md](DOCUMENTATION.md). The Tier 1–3 architectural plan (including
roadmap, DB schema, and WebSocket protocol) is in [docs/ROADMAP.md](docs/ROADMAP.md).
