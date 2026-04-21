package org.pixeltower.rp;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserEnterRoomEvent;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.plugin.events.users.UserProfileCardViewedEvent;
import org.pixeltower.rp.core.HomePositionStore;
import org.pixeltower.rp.core.TargetTracker;
import org.pixeltower.rp.core.commands.TargetCommand;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.ShiftManager;
import org.pixeltower.rp.corp.commands.FireCommand;
import org.pixeltower.rp.corp.commands.HireCommand;
import org.pixeltower.rp.corp.commands.PromoteCommand;
import org.pixeltower.rp.corp.commands.StartWorkCommand;
import org.pixeltower.rp.corp.commands.StopWorkCommand;
import org.pixeltower.rp.corp.tasks.PaycheckTask;
import org.pixeltower.rp.economy.commands.AwardCommand;
import org.pixeltower.rp.economy.commands.BalanceCommand;
import org.pixeltower.rp.economy.commands.DepositCommand;
import org.pixeltower.rp.economy.commands.GiveCommand;
import org.pixeltower.rp.economy.commands.OpenAccountCommand;
import org.pixeltower.rp.economy.commands.TransferCommand;
import org.pixeltower.rp.economy.commands.WithdrawCommand;
import org.pixeltower.rp.economy.tasks.BankInterestTask;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.commands.RestoreCommand;
import org.pixeltower.rp.stats.commands.StatsCommand;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;
import org.pixeltower.rp.stats.outgoing.UpdateTargetStatsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
        // (e.g. hot-swap via reload), kick the post-load hook manually.
        if (Emulator.isReady && !Emulator.isShuttingDown) {
            this.onEmulatorLoadedEvent(null);
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
        Emulator.getConfig().register("rp.stats.hp_regen_interval_s", "30");
        Emulator.getConfig().register("rp.stats.hp_regen_amount",     "2");
        Emulator.getConfig().register("rp.stats.default_hp",          "100");
        Emulator.getConfig().register("rp.stats.default_energy",      "100");
        Emulator.getConfig().register("rp.fight.hit_window_ms",       "500");
        Emulator.getConfig().register("rp.fight.fade_window_ms",      "500");
        Emulator.getConfig().register("rp.fight.energy_per_hit",      "10");
        Emulator.getConfig().register("rp.fight.damage_variance",     "0.2");
        Emulator.getConfig().register("rp.medical.respawn_timeout_s", "180");
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
        registerCommands();
        scheduleBankInterest();
        schedulePaycheck();

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
        CommandHandler.addCommand(new HireCommand());
        CommandHandler.addCommand(new FireCommand());
        CommandHandler.addCommand(new PromoteCommand());
        CommandHandler.addCommand(new StartWorkCommand());
        CommandHandler.addCommand(new StopWorkCommand());
        CommandHandler.addCommand(new StatsCommand());
        CommandHandler.addCommand(new RestoreCommand());
    }

    @EventHandler
    public void onUserProfileCardViewed(UserProfileCardViewedEvent event) {
        if (event.habbo == null || event.target == null) return;
        int callerId = event.habbo.getHabboInfo().getId();
        int targetId = event.target.getHabboInfo().getId();
        TargetTracker.set(callerId, targetId);

        // Never pipe the caller's own info into the target slot.
        if (callerId == targetId) return;
        if (event.habbo.getClient() == null) return;

        StatsManager.getOrFetch(targetId).ifPresent(stats ->
                event.habbo.getClient().sendResponse(new UpdateTargetStatsComposer(
                        targetId,
                        event.target.getHabboInfo().getLook(),
                        event.target.getHabboInfo().getUsername(),
                        stats)));
    }

    @EventHandler
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (event.habbo != null) {
            int habboId = event.habbo.getHabboInfo().getId();
            TargetTracker.clear(habboId);
            ShiftManager.stopWork(habboId);
            StatsManager.onDisconnect(habboId);
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

        // Login-triggered entry: drop the user back on the tile they left on.
        // UserEnterRoomEvent fires BEFORE Arcturus's room-ready state is
        // populated (RoomUnit.room + HabboInfo.currentRoom both come later).
        // Poll until ready, then teleport — see attemptTeleportAfterReady.
        if (RESTORE_POSITION_ON_ENTER.remove(habboId)) {
            HomePositionStore.load(habboId).ifPresent(pos ->
                    attemptTeleportAfterReady(event.habbo, event.room, pos, 0));
        }
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
     * Capture the user's tile + body rotation when they leave a room so the
     * next login can drop them back there. Uses UserExitRoomEvent because
     * RoomUnit is still populated at exit time (unlike UserDisconnectEvent
     * where Arcturus has already nulled it out).
     */
    @EventHandler
    public void onUserExitRoom(UserExitRoomEvent event) {
        if (event.habbo == null) return;
        RoomUnit unit = event.habbo.getRoomUnit();
        if (unit == null || !unit.isInRoom()) return;
        HomePositionStore.save(
                event.habbo.getHabboInfo().getId(),
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
}
