package org.pixeltower.rp.medical;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The respawn side-effects: teleport to hospital, HP + energy refill,
 * credits penalty, {@code rp_downed_players} cleanup, {@code DeathState}
 * exit. Called from three entry points:
 *
 * <ul>
 *   <li>{@link RespawnScheduler} timeout fire</li>
 *   <li>{@link org.pixeltower.rp.medical.commands.RespawnCommand}
 *       (voluntary early-out)</li>
 *   <li>Login rehydration if {@code respawn_at} elapsed while offline</li>
 * </ul>
 *
 * Idempotent: re-executing on an already-respawned habbo is a no-op
 * (the {@code rp_downed_players} lookup short-circuits).
 */
public final class RespawnTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(RespawnTask.class);

    private RespawnTask() {}

    public static void execute(int habboId) {
        Long fightId = readFightIdFromDownedRow(habboId);
        if (fightId == null && !isStillDowned(habboId)) {
            // Row already gone — revived by a paramedic or restored by staff.
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        PlayerStats stats = StatsManager.get(habboId).orElse(null);
        // Offline path: skip teleport; StatsManager.restoreStats handles the
        // DB HP/energy + rp_downed_players cleanup even for offline users.
        if (habbo == null || stats == null) {
            applyOfflineRespawn(habboId, fightId);
            return;
        }

        teleportToHospital(habbo);

        // restoreStats fires onRevive → DeathState.exit + deleteDownedPlayer +
        // RespawnScheduler.cancel (no-op, already cancelled by the caller).
        StatsManager.restoreStats(habboId);
        if (habbo.getClient() != null) {
            StatsManager.get(habboId).ifPresent(fresh ->
                    habbo.getClient().sendResponse(new UpdatePlayerStatsComposer(fresh)));
        }

        long penalty = Emulator.getConfig().getInt("rp.medical.respawn_penalty_credits", 500);
        payRespawnPenalty(habbo, penalty, fightId);

        habbo.whisper("You wake up at the hospital. A respawn fee has been deducted.",
                RoomChatMessageBubbles.ALERT);
    }

    private static void teleportToHospital(Habbo habbo) {
        int hospitalRoomId = Emulator.getConfig().getInt("rp.medical.hospital_room_id", 0);
        if (hospitalRoomId <= 0) {
            LOGGER.warn("rp.medical.hospital_room_id is unset — skipping teleport for habbo={}",
                    habbo.getHabboInfo().getId());
            return;
        }
        Room current = habbo.getHabboInfo().getCurrentRoom();
        if (current != null && current.getId() == hospitalRoomId) return;
        Emulator.getGameEnvironment().getRoomManager().enterRoom(habbo, hospitalRoomId, "");
    }

    /**
     * Pay what the player can afford. Going bankrupt over a respawn is
     * a worse UX than the attacker-bountied endgame — log the shortfall
     * and let the player through.
     */
    private static void payRespawnPenalty(Habbo habbo, long penalty, Long fightId) {
        if (penalty <= 0) return;
        long wallet = habbo.getHabboInfo().getCredits();
        long owed = Math.min(wallet, penalty);
        if (owed <= 0) {
            LOGGER.info("respawn penalty skipped (zero-balance) habbo={}",
                    habbo.getHabboInfo().getId());
            return;
        }
        try {
            MoneyLedger.debit(habbo, owed, "respawn_penalty", fightId);
        } catch (InsufficientFundsException e) {
            // Raced with concurrent spend — log, swallow.
            LOGGER.warn("respawn penalty debit raced habbo={} owed={} balance={}",
                    habbo.getHabboInfo().getId(), owed, wallet);
        }
    }

    /**
     * Offline-user path: teleport skipped (no Habbo), but the DB state
     * still has to land. Restore HP/energy via fetch+persist by reusing
     * restoreStats (which handles offline via fetch fallback), then
     * debit the penalty via the offline ledger helper.
     */
    private static void applyOfflineRespawn(int habboId, Long fightId) {
        StatsManager.restoreStats(habboId);
        long penalty = Emulator.getConfig().getInt("rp.medical.respawn_penalty_credits", 500);
        if (penalty <= 0) return;
        try {
            MoneyLedger.debitOffline(habboId, penalty, "respawn_penalty", fightId);
        } catch (InsufficientFundsException e) {
            LOGGER.info("respawn penalty skipped (offline insufficient funds) habbo={}", habboId);
        }
    }

    private static boolean isStillDowned(int habboId) {
        return readFightIdFromDownedRow(habboId) != null || downedRowExists(habboId);
    }

    private static boolean downedRowExists(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM rp_downed_players WHERE habbo_id = ? LIMIT 1")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("downedRowExists check failed habbo={}", habboId, e);
            return false;
        }
    }

    private static Long readFightIdFromDownedRow(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT fight_id FROM rp_downed_players WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long v = rs.getLong("fight_id");
                return rs.wasNull() ? null : v;
            }
        } catch (SQLException e) {
            LOGGER.error("readFightIdFromDownedRow failed habbo={}", habboId, e);
            return null;
        }
    }
}
