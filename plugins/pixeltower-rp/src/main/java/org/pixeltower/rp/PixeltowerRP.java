package org.pixeltower.rp;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadItemsManagerEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserEnterRoomEvent;
import com.eu.habbo.plugin.events.users.UserExecuteCommandEvent;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import com.eu.habbo.plugin.events.users.UserSavedMottoEvent;
import com.eu.habbo.plugin.events.users.UserTalkEvent;
import com.eu.habbo.plugin.events.users.UserTargetSelectedEvent;
import org.pixeltower.rp.core.HomePositionStore;
import org.pixeltower.rp.core.StaffGate;
import org.pixeltower.rp.core.TargetService;
import org.pixeltower.rp.core.TargetTracker;
import org.pixeltower.rp.core.commands.DanceCommand;
import org.pixeltower.rp.core.commands.TargetCommand;
import org.pixeltower.rp.core.commands.WardrobeCommand;
import org.pixeltower.rp.corp.CorpBadgeBroadcaster;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.ShiftManager;
import org.pixeltower.rp.corp.WorkingMotto;
import org.pixeltower.rp.corp.commands.FireCommand;
import org.pixeltower.rp.corp.commands.HireCommand;
import org.pixeltower.rp.corp.commands.PromoteCommand;
import org.pixeltower.rp.corp.commands.QuitJobCommand;
import org.pixeltower.rp.corp.commands.StartWorkCommand;
import org.pixeltower.rp.corp.commands.StopWorkCommand;
import org.pixeltower.rp.corp.commands.SuperFireCommand;
import org.pixeltower.rp.corp.commands.SuperHireCommand;
import org.pixeltower.rp.corp.tasks.PaycheckTask;
import org.pixeltower.rp.death.DeathState;
import org.pixeltower.rp.economy.commands.AwardCommand;
import org.pixeltower.rp.economy.commands.BalanceCommand;
import org.pixeltower.rp.economy.commands.DepositCommand;
import org.pixeltower.rp.economy.commands.GiveCommand;
import org.pixeltower.rp.economy.commands.OpenAccountCommand;
import org.pixeltower.rp.economy.commands.TransferCommand;
import org.pixeltower.rp.economy.commands.WithdrawCommand;
import org.pixeltower.rp.economy.tasks.BankInterestTask;
import org.pixeltower.rp.fight.EngagementRegistry;
import org.pixeltower.rp.fight.FightService;
import org.pixeltower.rp.fight.commands.FightTestCommand;
import org.pixeltower.rp.fight.commands.HitCommand;
import org.pixeltower.rp.fight.commands.SetZoneCommand;
import org.pixeltower.rp.functional.FunctionalFurnitureService;
import org.pixeltower.rp.medical.RespawnScheduler;
import org.pixeltower.rp.medical.commands.RespawnCommand;
import org.pixeltower.rp.functional.InteractionRpFunctional;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.commands.KillCommand;
import org.pixeltower.rp.stats.commands.RestoreCommand;
import org.pixeltower.rp.stats.commands.SetEnergyCommand;
import org.pixeltower.rp.stats.commands.SetHealthCommand;
import org.pixeltower.rp.stats.commands.StatsCommand;
import org.pixeltower.rp.stats.commands.SuicideCommand;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Pixeltower RP plugin entrypoint. Lifecycle is driven by Arcturus:
 *   onEnable()  — class loaded, event listeners registered.
 *   onEmulatorLoadedEvent() — everything else (DB managers, config, room
 *                              manager) is ready; safe to read/write state.
 *   onDisable() — graceful shutdown.
 *
 * Future tiers' subsystems (stats, economy, corps, fight, medical, police)
 * will be wired in from onEmulatorLoadedEvent so they can rely on a fully
 * initialized Emulator.
 */
public class PixeltowerRP extends HabboPlugin implements EventListener {

    public static final String VERSION = "0.1.0";
    public static final String FUNCTIONAL_INTERACTION_KEY = "rp_functional";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixeltowerRP.class);

    /**
     * Habbo IDs whose next UserEnterRoomEvent should restore their saved
     * tile + rotation. Populated in onUserLogin before enterRoom fires,
     * drained in onUserEnterRoom. Using a set keeps the handler idempotent
     * across any mid-session re-enters.
     */
    private static final Set<Integer> RESTORE_POSITION_ON_ENTER = ConcurrentHashMap.newKeySet();

    /**
     * Poll interval + max attempts for the login-triggered position restore.
     * Arcturus's UserEnterRoomEvent fires BEFORE the RoomUnit exists and the
     * HabboInfo.currentRoom reference is populated — those come later during
     * the client's room load, with variable latency. We retry every
     * {@value #POSITION_RESTORE_POLL_MS}ms up to {@value #POSITION_RESTORE_MAX_ATTEMPTS}
     * times (≈ 3s total) until the habbo's room state is wired, then teleport.
     */
    private static final long POSITION_RESTORE_POLL_MS = 100L;
    private static final int POSITION_RESTORE_MAX_ATTEMPTS = 30;

    @Override
    public void onEnable() throws Exception {
        Emulator.getPluginManager().registerEvents(this, this);
        LOGGER.info("Pixeltower RP v{} — onEnable", VERSION);

        // If the emulator is already fully loaded when this plugin enables
        // (e.g. hot-swap via reload), kick the post-load hook manually. Also
        // try to register the rp_functional interaction class — at hot-reload
        // time EmulatorLoadItemsManagerEvent has long since fired, so the
        // event-handler path won't be taken. Existing Item rows keep their
        // bound class until full restart, but newly-loaded items will pick up
        // InteractionRpFunctional.
        if (Emulator.isReady && !Emulator.isShuttingDown) {
            registerFunctionalInteraction();
            this.onEmulatorLoadedEvent(null);
        }
    }

    /**
     * Cold-boot path. Fires before {@code ItemManager.loadItems()} binds each
     * items_base row to its interaction class, which is the only window in
     * which we can introduce a new {@code interaction_type} string and have
     * existing rows pick it up without a restart.
     */
    @EventHandler
    public void onEmulatorLoadItemsManager(EmulatorLoadItemsManagerEvent event) {
        registerFunctionalInteraction();
    }

    private static void registerFunctionalInteraction() {
        try {
            Emulator.getGameEnvironment().getItemManager().addItemInteraction(
                    new ItemInteraction(FUNCTIONAL_INTERACTION_KEY, InteractionRpFunctional.class));
            LOGGER.info("Registered rp_functional interaction → InteractionRpFunctional");
        } catch (RuntimeException dup) {
            // addItemInteraction throws on duplicate name OR class — safe to
            // swallow on hot-reload, where we're re-asserting an existing
            // registration the previous load already made.
            LOGGER.debug("rp_functional interaction already registered ({})", dup.getMessage());
        }
    }

    @Override
    public void onDisable() throws Exception {
        LOGGER.info("Pixeltower RP v{} — onDisable", VERSION);
    }

    @Override
    public boolean hasPermission(Habbo habbo, String s) {
        return false;
    }

    @EventHandler
    public void onEmulatorLoadedEvent(EmulatorLoadedEvent event) {
        // Register the rp.* config keys so emulator_settings rows populate
        // with defaults on first boot. Per-subsystem managers will read them
        // on init in later Tier 1 commits.
        Emulator.getConfig().register("rp.corp.paycheck_tick_s",  "600");
        Emulator.getConfig().register("rp.corp.unemployed_badge", "ES03N");
        Emulator.getConfig().register("rp.stats.hp_regen_interval_s", "30");
        Emulator.getConfig().register("rp.stats.hp_regen_amount",     "2");
        Emulator.getConfig().register("rp.stats.default_hp",          "100");
        Emulator.getConfig().register("rp.stats.default_energy",      "100");
        Emulator.getConfig().register("rp.fight.hit_window_ms",       "500");
        Emulator.getConfig().register("rp.fight.fade_window_ms",      "500");
        Emulator.getConfig().register("rp.fight.energy_per_hit",      "0.33");
        Emulator.getConfig().register("rp.fight.damage_variance",     "0.2");
        Emulator.getConfig().register("rp.fight.base_damage",         "6");
        Emulator.getConfig().register("rp.fight.endurance_per_point", "0.04");
        Emulator.getConfig().register("rp.fight.endurance_floor",     "0.40");
        Emulator.getConfig().register("rp.fight.engagement_timeout_s","30");
        Emulator.getConfig().register("rp.fight.cooldown_ms",         "3000");
        Emulator.getConfig().register("rp.fight.range_tiles",         "1");
        Emulator.getConfig().register("rp.fight.allow_corp_fratricide","false");
        Emulator.getConfig().register("rp.medical.respawn_timeout_s", "180");
        Emulator.getConfig().register("rp.medical.respawn_penalty_credits", "500");
        Emulator.getConfig().register("rp.medical.hospital_room_id",  "0");
        Emulator.getConfig().register("rp.police.jail_room_id",       "0");

        Emulator.getConfig().register("rp.bank.fee_rate",             "0.01");
        Emulator.getConfig().register("rp.bank.fee_corp_key",         "bank");
        Emulator.getConfig().register("rp.bank.interest_rate",        "0.001");
        Emulator.getConfig().register("rp.bank.interest_tick_s",      "86400");
        Emulator.getConfig().register("rp.bank.interest_min_balance", "100");
        Emulator.getConfig().register("rp.bank.atm_room_ids",         "");

        Emulator.getConfig().register("rp.admin.min_rank",            "5");
        Emulator.getConfig().register("rp.spawn.default_room_id",     "58");

        CorporationManager.init();
        FunctionalFurnitureService.loadAll();
        registerCommands();
        scheduleBankInterest();
        schedulePaycheck();
        scheduleEngagementReaper();

        LOGGER.info("Pixeltower RP v{} — loaded (rp.* config keys registered, commands registered)", VERSION);
    }

    private void registerCommands() {
        CommandHandler.addCommand(new BalanceCommand());
        CommandHandler.addCommand(new GiveCommand());
        CommandHandler.addCommand(new OpenAccountCommand());
        CommandHandler.addCommand(new DepositCommand());
        CommandHandler.addCommand(new WithdrawCommand());
        CommandHandler.addCommand(new TransferCommand());
        CommandHandler.addCommand(new AwardCommand());
        CommandHandler.addCommand(new TargetCommand());
        CommandHandler.addCommand(new DanceCommand());
        CommandHandler.addCommand(new WardrobeCommand());
        CommandHandler.addCommand(new HireCommand());
        CommandHandler.addCommand(new FireCommand());
        CommandHandler.addCommand(new PromoteCommand());
        CommandHandler.addCommand(new StartWorkCommand());
        CommandHandler.addCommand(new StopWorkCommand());
        CommandHandler.addCommand(new QuitJobCommand());
        CommandHandler.addCommand(new SuperHireCommand());
        CommandHandler.addCommand(new SuperFireCommand());
        CommandHandler.addCommand(new StatsCommand());
        CommandHandler.addCommand(new RestoreCommand());
        CommandHandler.addCommand(new KillCommand());
        CommandHandler.addCommand(new SuicideCommand());
        CommandHandler.addCommand(new SetHealthCommand());
        CommandHandler.addCommand(new SetEnergyCommand());
        CommandHandler.addCommand(new FightTestCommand());
        CommandHandler.addCommand(new HitCommand());
        CommandHandler.addCommand(new SetZoneCommand());
        CommandHandler.addCommand(new RespawnCommand());
    }

    /**
     * Avatar-click targeting. Fired from our Arcturus patch whenever the
     * Nitro client sends the pixeltower "set target" packet (header 6550),
     * which the patched InfoStand widget emits on every user-sprite click.
     * Profile opening is intentionally not wired here — TargetHUD follows
     * the last avatar the player clicked, never the last profile opened.
     */
    @EventHandler
    public void onUserTargetSelected(UserTargetSelectedEvent event) {
        if (event.habbo == null) return;
        TargetService.setAndPush(event.habbo, event.targetHabboId);
    }

    /**
     * When a user saves a new figure (Change Looks), push a fresh
     * UpdateTargetStatsComposer to every viewer currently targeting them so
     * their TargetHUD avatar swaps in realtime instead of only on re-click.
     *
     * UserSavedLookEvent fires BEFORE Arcturus commits the new look to
     * HabboInfo, so we pass {@code event.newLook} explicitly — reading from
     * HabboInfo here would broadcast the stale figure.
     */
    @EventHandler
    public void onUserSavedLook(UserSavedLookEvent event) {
        if (event.habbo == null || event.isCancelled()) return;
        TargetService.broadcastStatsUpdate(
                event.habbo.getHabboInfo().getId(), event.newLook);
    }

    /**
     * Downed players speak only in whispers. TALK / SHOUT are cancelled
     * with an ALERT whisper back to the speaker; command chat
     * ({@code isCommand=true}) passes regardless of type so a downed
     * user can still run {@code :respawn} or similar.
     *
     * Cancelling UserTalkEvent short-circuits the vanilla broadcast so
     * the downed player's talk/shout never reaches the room.
     */
    @EventHandler
    public void onUserTalk(UserTalkEvent event) {
        if (event.habbo == null || event.chatMessage == null) return;
        if (event.chatMessage.isCommand) return;
        if (event.chatType == RoomChatType.WHISPER) return;
        if (StaffGate.isStaff(event.habbo)) return;
        if (!DeathState.isDead(event.habbo.getHabboInfo().getId())) return;
        event.setCancelled(true);
        event.habbo.whisper("You can only whisper while downed.",
                RoomChatMessageBubbles.ALERT);
    }

    /**
     * Block {@code :lay}, {@code :sit}, {@code :stand} while a player's HP
     * is 0 — otherwise a dead player could stand themselves back up out of
     * the freeze+lay pose {@link DeathState#enter} applied. The event is
     * cancelable and fired before Command.handle, so cancelling short-
     * circuits the vanilla posture change entirely.
     */
    @EventHandler
    public void onUserExecuteCommand(UserExecuteCommandEvent event) {
        if (event.habbo == null || event.command == null) return;
        if (StaffGate.isStaff(event.habbo)) return;
        String[] keys = event.command.keys;
        if (keys == null) return;
        boolean isPosture = false;
        for (String k : keys) {
            if ("lay".equalsIgnoreCase(k) || "sit".equalsIgnoreCase(k)
                    || "stand".equalsIgnoreCase(k)) {
                isPosture = true;
                break;
            }
        }
        if (!isPosture) return;

        int habboId = event.habbo.getHabboInfo().getId();
        StatsManager.get(habboId).ifPresent(stats -> {
            if (stats.getHp() <= 0) {
                event.habbo.whisper("You are dead.", RoomChatMessageBubbles.ALERT);
                event.setCancelled(true);
            }
        });
    }

    /**
     * While a player is clocked in their displayed motto is locked to
     * {@code [WORKING] <rank>}. If a motto-save packet arrives mid-shift —
     * e.g. from a custom client — capture the user's intent so it's applied
     * on {@code :stopwork}, then force {@code event.newMotto} over the
     * length cap so {@link com.eu.habbo.messages.incoming.users.SaveMottoEvent}
     * skips its {@code setMotto} + DB write. The handler's broadcast still
     * fires but reads the unchanged {@code [WORKING] X} from in-memory state,
     * so the working badge stays visible to everyone in the room.
     */
    @EventHandler
    public void onUserSavedMotto(UserSavedMottoEvent event) {
        if (event.habbo == null) return;
        int habboId = event.habbo.getHabboInfo().getId();
        if (!WorkingMotto.isActive(habboId)) return;

        WorkingMotto.updateIntent(habboId, event.newMotto);
        // Sentinel longer than the 38-char default motto.max_length — chosen
        // to fail the upstream length check in SaveMottoEvent.handle() so the
        // setMotto/run pair is bypassed. The exact contents don't matter; only
        // the length does. Don't shrink this without auditing motto.max_length.
        event.newMotto = "_PIXELTOWER_WORKING_MOTTO_OVERRIDE_BLOCKED_PERSIST";
    }

    @EventHandler
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (event.habbo != null) {
            int habboId = event.habbo.getHabboInfo().getId();
            TargetTracker.clear(habboId);
            ShiftManager.stopWork(habboId);
            WorkingMotto.clear(habboId);
            StatsManager.onDisconnect(habboId);
            EngagementRegistry.terminateAll(habboId, "logout");
            FightService.onDisconnect(habboId);
        }
    }

    /**
     * Pin the user's profile home_room to the room they just entered. Writing
     * on every entry (rather than at disconnect) means the value survives
     * unclean exits and Arcturus's already-cleared getCurrentRoom() at
     * disconnect-time. The last room they entered is, by definition, the one
     * they were in when they logged off.
     *
     * Both the cached HabboInfo.homeRoom AND the users.home_room DB row are
     * updated — if we only update the DB, Arcturus's save-on-disconnect
     * overwrites our value with its stale in-memory cache (0 for never-set
     * users) and the home shortcut breaks.
     */
    @EventHandler
    public void onUserEnterRoom(UserEnterRoomEvent event) {
        if (event.habbo == null || event.room == null) return;
        int habboId = event.habbo.getHabboInfo().getId();
        int roomId = event.room.getId();
        event.habbo.getHabboInfo().setHomeRoom(roomId);
        updateHomeRoom(habboId, roomId);

        // Refresh the room's corp-favorite-group-badge override map for
        // every client in the room (including the entering user). Polls
        // for room-readiness so the entering user is included in the map.
        attemptPushCorpBadgesAfterReady(event.habbo, event.room, 0);

        // Login-triggered entry: drop the user back on the tile they left on.
        // UserEnterRoomEvent fires BEFORE Arcturus's room-ready state is
        // populated (RoomUnit.room + HabboInfo.currentRoom both come later).
        // Poll until ready, then teleport — see attemptTeleportAfterReady. The
        // teleport chains DeathState.reapplyIfDead at the end, so a player who
        // logged off dead lands back in the lay+freeze pose.
        if (RESTORE_POSITION_ON_ENTER.remove(habboId)) {
            Optional<HomePositionStore.Position> pos = HomePositionStore.load(habboId);
            if (pos.isPresent()) {
                attemptTeleportAfterReady(event.habbo, event.room, pos.get(), 0);
                return;
            }
        }
        // No teleport queued (first-time login, mid-session room change). We
        // still need to re-apply death state if HP is 0 — the RoomUnit flags
        // (LAY status, canWalk) don't persist across sessions or rooms.
        attemptReapplyAfterReady(event.habbo, event.room, 0);
    }

    /**
     * Polls HabboInfo.currentRoom every {@value #POSITION_RESTORE_POLL_MS}ms
     * up to {@value #POSITION_RESTORE_MAX_ATTEMPTS} times. On the first poll
     * where the habbo's current room matches the target room, fires the
     * teleport. Gives up silently (with a log line) if the habbo never
     * reaches a ready state inside the window.
     */
    private static void attemptTeleportAfterReady(Habbo habbo,
                                                  com.eu.habbo.habbohotel.rooms.Room room,
                                                  HomePositionStore.Position pos,
                                                  int attempt) {
        Emulator.getThreading().run(() -> {
            if (habbo == null || room == null) return;
            if (attempt >= POSITION_RESTORE_MAX_ATTEMPTS) return;
            com.eu.habbo.habbohotel.rooms.Room current =
                    habbo.getHabboInfo().getCurrentRoom();
            RoomUnit unit = habbo.getRoomUnit();
            boolean ready = current != null
                    && current.getId() == room.getId()
                    && unit != null
                    && unit.isInRoom();
            if (!ready) {
                attemptTeleportAfterReady(habbo, room, pos, attempt + 1);
                return;
            }
            teleportToHomePosition(habbo, room, pos);
        }, POSITION_RESTORE_POLL_MS);
    }

    /**
     * Same readiness poll as {@link #attemptTeleportAfterReady}, but runs
     * {@link DeathState#reapplyIfDead} once the RoomUnit is placed in the
     * room. Scheduled from onUserEnterRoom on every room-enter that isn't
     * accompanied by a home-position teleport (the teleport chain handles
     * reapply itself so the teleport's status broadcast doesn't clobber
     * the lay status).
     */
    private static void attemptReapplyAfterReady(Habbo habbo,
                                                 com.eu.habbo.habbohotel.rooms.Room room,
                                                 int attempt) {
        Emulator.getThreading().run(() -> {
            if (habbo == null || room == null) return;
            if (attempt >= POSITION_RESTORE_MAX_ATTEMPTS) return;
            com.eu.habbo.habbohotel.rooms.Room current =
                    habbo.getHabboInfo().getCurrentRoom();
            RoomUnit unit = habbo.getRoomUnit();
            boolean ready = current != null
                    && current.getId() == room.getId()
                    && unit != null
                    && unit.isInRoom();
            if (!ready) {
                attemptReapplyAfterReady(habbo, room, attempt + 1);
                return;
            }
            DeathState.reapplyIfDead(habbo);
        }, POSITION_RESTORE_POLL_MS);
    }

    /**
     * Same readiness poll as {@link #attemptReapplyAfterReady}, but pushes
     * the room's corp-badge override map once the entering RoomUnit has
     * been placed in the room. Without the poll the entering user wouldn't
     * yet appear in {@code room.getHabbos()}, so their corp badge would
     * be missing from the broadcast they themselves receive.
     */
    private static void attemptPushCorpBadgesAfterReady(Habbo habbo,
                                                        com.eu.habbo.habbohotel.rooms.Room room,
                                                        int attempt) {
        Emulator.getThreading().run(() -> {
            if (habbo == null || room == null) return;
            if (attempt >= POSITION_RESTORE_MAX_ATTEMPTS) return;
            com.eu.habbo.habbohotel.rooms.Room current =
                    habbo.getHabboInfo().getCurrentRoom();
            RoomUnit unit = habbo.getRoomUnit();
            boolean ready = current != null
                    && current.getId() == room.getId()
                    && unit != null
                    && unit.isInRoom();
            if (!ready) {
                attemptPushCorpBadgesAfterReady(habbo, room, attempt + 1);
                return;
            }
            CorpBadgeBroadcaster.pushFullToRoom(room);
        }, POSITION_RESTORE_POLL_MS);
    }

    /**
     * Capture the user's tile + body rotation when they leave a room so the
     * next login can drop them back there. Uses UserExitRoomEvent because
     * RoomUnit is still populated at exit time (unlike UserDisconnectEvent
     * where Arcturus has already nulled it out).
     */
    @EventHandler
    public void onUserExitRoom(UserExitRoomEvent event) {
        if (event.habbo == null) return;
        int habboId = event.habbo.getHabboInfo().getId();
        EngagementRegistry.terminateAll(habboId, "roomchange");
        RoomUnit unit = event.habbo.getRoomUnit();
        if (unit == null || !unit.isInRoom()) return;
        HomePositionStore.save(
                habboId,
                unit.getX(),
                unit.getY(),
                unit.getBodyRotation().getValue());
    }

    /**
     * On login, if the user has a persisted home_room, server-initiate a
     * room join so they land back where they last were instead of on the
     * hotel view. The tile+rotation restore is staged via
     * {@link #RESTORE_POSITION_ON_ENTER}; the actual teleport runs from
     * onUserEnterRoom once Arcturus has placed the RoomUnit in the room.
     */
    @EventHandler
    public void onUserLogin(UserLoginEvent event) {
        if (event.habbo == null) return;
        int habboId = event.habbo.getHabboInfo().getId();
        PlayerStats stats = StatsManager.onLogin(habboId);
        if (stats != null && event.habbo.getClient() != null) {
            event.habbo.getClient().sendResponse(new UpdatePlayerStatsComposer(stats));
        }
        rehydrateRespawnTimer(habboId);
        int homeRoom = event.habbo.getHabboInfo().getHomeRoom();
        // Fall back to the configured default spawn so new users (home_room=0)
        // never hit the hotel view. The Nitro patch strips the HotelView
        // widget entirely, so unrouted users would see a blank screen.
        if (homeRoom <= 0) {
            homeRoom = Emulator.getConfig().getInt("rp.spawn.default_room_id", 0);
        }
        if (homeRoom <= 0) return;
        RESTORE_POSITION_ON_ENTER.add(habboId);
        Emulator.getGameEnvironment().getRoomManager()
                .enterRoom(event.habbo, homeRoom, "");
    }

    private static void teleportToHomePosition(Habbo habbo,
                                               com.eu.habbo.habbohotel.rooms.Room room,
                                               HomePositionStore.Position pos) {
        if (habbo == null || room == null) return;
        RoomUnit unit = habbo.getRoomUnit();
        if (unit == null || !unit.isInRoom()) return;
        com.eu.habbo.habbohotel.rooms.Room current = habbo.getHabboInfo().getCurrentRoom();
        if (current == null || current.getId() != room.getId()) return;
        RoomTile tile = room.getLayout().getTile((short) pos.x(), (short) pos.y());
        if (tile == null) return;
        unit.setLocation(tile);
        unit.setZ(tile.getStackHeight());
        unit.setRotation(RoomUserRotation.fromValue(pos.rotation()));
        room.sendComposer(new RoomUserStatusComposer(unit).compose());
        // Dead players land in lay+freeze — re-applied after the teleport's
        // status broadcast so the LAY status wins the last-write-wins race.
        DeathState.reapplyIfDead(habbo);
    }

    private static void updateHomeRoom(int habboId, int roomId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET home_room = ? WHERE id = ?")) {
            ps.setInt(1, roomId);
            ps.setInt(2, habboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("updateHomeRoom failed habbo={} room={}", habboId, roomId, e);
        }
    }

    /**
     * Auto-clockout when an on-duty habbo falls asleep (enters idle). Their
     * persisted minute counter is preserved (ShiftManager.stopWork writes it
     * through). The public emote is asterisk-wrapped so the action-emote
     * Nitro patch prepends the username — we only need to ship the verb.
     */
    @EventHandler
    public void onUserIdle(UserIdleEvent event) {
        if (event.habbo == null || !event.idle) return;
        int habboId = event.habbo.getHabboInfo().getId();
        if (!ShiftManager.stopWork(habboId)) return;
        if (event.habbo.getRoomUnit() != null && event.habbo.getRoomUnit().isInRoom()) {
            event.habbo.shout("*has stopped working as they've fallen asleep.*",
                    RoomChatMessageBubbles.BLACK);
        }
    }

    private void scheduleBankInterest() {
        long tickS = Emulator.getConfig().getInt("rp.bank.interest_tick_s", 86400);
        // First run is tickS seconds from plugin-load so we don't double-pay on
        // rapid emulator restarts. Ops can trigger a manual tick via :rpreload
        // (future) or by flipping interest_tick_s briefly.
        Emulator.getThreading().getService().scheduleAtFixedRate(
                new BankInterestTask(), tickS, tickS, TimeUnit.SECONDS);
        LOGGER.info("BankInterestTask scheduled every {}s", tickS);
    }

    private void schedulePaycheck() {
        // Shift heartbeat fires every 60s; PaycheckTask itself accumulates
        // each clocked-in player's worked minutes and fires the actual
        // paycheck after ShiftManager.PAY_EVERY_MINUTES.
        Emulator.getThreading().getService().scheduleAtFixedRate(
                new PaycheckTask(), 60L, 60L, TimeUnit.SECONDS);
        LOGGER.info("PaycheckTask scheduled every 60s (shift heartbeat)");
    }

    /**
     * Engagement timeout sweeper. Runs every 10s, finalizes any
     * engagement whose lastHitAt is older than rp.fight.engagement_timeout_s
     * with {@code ender_reason='timeout'}. The fixed 10s cadence is fast
     * enough that a 30s timeout expires within 30-40s of real time;
     * tuning down tightens that window if needed.
     */
    private void scheduleEngagementReaper() {
        Emulator.getThreading().getService().scheduleAtFixedRate(
                EngagementRegistry::reapTimeouts, 10L, 10L, TimeUnit.SECONDS);
        LOGGER.info("EngagementRegistry reaper scheduled every 10s");
    }

    /**
     * On login, if the user is in {@code rp_downed_players} (i.e. was
     * downed when they logged off and never revived), re-arm the
     * respawn scheduler with whatever time is left on the clock. The
     * in-memory {@code ScheduledFuture} from the original schedule was
     * lost on disconnect (and possibly emulator restart) — {@code
     * respawn_at} is the DB-backed source of truth.
     *
     * A 2s floor on the delay lets the user's room-enter flow settle
     * before we teleport them to the hospital — otherwise enterRoom
     * can race with the login's own enterRoom placement.
     */
    private static void rehydrateRespawnTimer(int habboId) {
        Long remaining = RespawnScheduler.computeRemainingSeconds(habboId);
        if (remaining == null) return;
        long delay = Math.max(2L, remaining);
        RespawnScheduler.schedule(habboId, delay);
    }
}
