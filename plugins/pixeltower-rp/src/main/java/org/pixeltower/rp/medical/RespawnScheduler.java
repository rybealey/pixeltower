package org.pixeltower.rp.medical;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Per-habbo one-shot {@code ScheduledFuture} for the auto-respawn
 * timeout. Owner of the "timer" side of the downed state; the "DB row"
 * side lives in {@code rp_downed_players}.
 *
 * Canonical state after a knockout:
 * <ul>
 *   <li>HP=0 in {@code rp_player_stats}</li>
 *   <li>row in {@code rp_downed_players} with {@code respawn_at}</li>
 *   <li>entry in {@link #FUTURES} firing {@link RespawnTask#execute}
 *       at {@code respawn_at}</li>
 * </ul>
 *
 * The {@link #FUTURES} map is in-memory only — lost on emulator
 * restart. On re-login, {@link #computeRemainingSeconds} is used to
 * rehydrate the schedule from the DB row. That means the DB is the
 * source of truth; the future is an optimization for in-session
 * downed-then-never-left users.
 */
public final class RespawnScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RespawnScheduler.class);

    private static final Map<Integer, ScheduledFuture<?>> FUTURES = new ConcurrentHashMap<>();

    private RespawnScheduler() {}

    /**
     * Arm the auto-respawn timer for {@code habboId}. Replaces any
     * previously-pending future (defensive — callers shouldn't double-
     * schedule, but onDeath may fire twice on races with :kill).
     */
    public static void schedule(int habboId, long delaySeconds) {
        cancel(habboId);
        long safeDelay = Math.max(0L, delaySeconds);
        ScheduledFuture<?> f = Emulator.getThreading().getService().schedule(
                () -> RespawnTask.execute(habboId),
                safeDelay,
                TimeUnit.SECONDS);
        FUTURES.put(habboId, f);
    }

    /**
     * Cancel any pending future for {@code habboId}. No-op if absent
     * or already fired. {@code cancel(false)} leaves a running task
     * alone — so calling this from within the task itself (via the
     * revive → onRevive → cancel chain) is safe.
     */
    public static void cancel(int habboId) {
        ScheduledFuture<?> f = FUTURES.remove(habboId);
        if (f != null) f.cancel(false);
    }

    /**
     * Execute the respawn path immediately. Used by {@code :respawn}
     * (voluntary early-out) and by the login-rehydrate path when
     * {@code respawn_at} has already elapsed during the user's offline
     * window.
     */
    public static void runNow(int habboId) {
        cancel(habboId);
        RespawnTask.execute(habboId);
    }

    /**
     * Seconds until {@code rp_downed_players.respawn_at} for
     * {@code habboId}, or {@code null} if the habbo isn't currently
     * downed. Negative return means the timer elapsed during an offline
     * window — caller should run the respawn immediately.
     */
    public static Long computeRemainingSeconds(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT UNIX_TIMESTAMP(respawn_at) - UNIX_TIMESTAMP() AS remaining "
                             + "FROM rp_downed_players WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getLong("remaining");
            }
        } catch (SQLException e) {
            LOGGER.error("computeRemainingSeconds failed habbo={}", habboId, e);
            return null;
        }
    }
}
