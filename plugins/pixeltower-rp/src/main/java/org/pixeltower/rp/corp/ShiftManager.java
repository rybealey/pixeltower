package org.pixeltower.rp.corp;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On-duty tracker with write-through persistence. A player's presence in
 * the in-memory map means they're currently clocked in; the value is the
 * minutes worked in the current pay cycle (0 … {@link #PAY_EVERY_MINUTES} - 1).
 * When the counter hits {@link #PAY_EVERY_MINUTES} the paycheck fires and
 * the counter resets to 0.
 *
 * <p>Progress is persisted to {@code rp_corporation_members.worked_minutes_in_cycle}
 * on every shift tick, {@code :stopwork}, and disconnect — so a player who
 * works 5 minutes and logs off resumes at minute 5 on their next
 * {@code :startwork}. Only the clocked-in flag is in-memory; the counter
 * itself survives restarts and logouts.</p>
 */
public final class ShiftManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShiftManager.class);

    /** Minutes in a pay cycle. User spec: paychecks every 10 worked minutes. */
    public static final int PAY_EVERY_MINUTES = 10;

    private static final Map<Integer, Integer> WORK_MINUTES = new ConcurrentHashMap<>();

    private ShiftManager() {}

    /**
     * Start a shift for the given habbo, resuming any persisted progress.
     * Returns false if already on duty.
     */
    public static boolean startWork(int habboId) {
        int resume = readStoredMinutes(habboId);
        return WORK_MINUTES.putIfAbsent(habboId, resume) == null;
    }

    /**
     * Stop a shift, persisting the current counter so it can be resumed
     * later. Returns false if wasn't on duty.
     */
    public static boolean stopWork(int habboId) {
        Integer finalMinutes = WORK_MINUTES.remove(habboId);
        if (finalMinutes == null) return false;
        persistMinutes(habboId, finalMinutes);
        return true;
    }

    public static boolean isOnDuty(int habboId) {
        return WORK_MINUTES.containsKey(habboId);
    }

    public static Iterable<Map.Entry<Integer, Integer>> shifts() {
        return WORK_MINUTES.entrySet();
    }

    /**
     * Increment the minute counter and return the new value. Returns -1 if
     * the habbo was clocked out between the caller's iteration and this
     * call (no mutation persisted).
     */
    public static int incrementAndGet(int habboId) {
        Integer result = WORK_MINUTES.computeIfPresent(habboId, (k, v) -> v + 1);
        if (result == null) return -1;
        persistMinutes(habboId, result);
        return result;
    }

    /** Reset the counter to 0 (used after a paycheck fires). No-op if not on duty. */
    public static void resetMinutes(int habboId) {
        Integer updated = WORK_MINUTES.computeIfPresent(habboId, (k, v) -> 0);
        if (updated != null) persistMinutes(habboId, 0);
    }

    // ──────────── persistence ────────────

    private static int readStoredMinutes(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT worked_minutes_in_cycle FROM rp_corporation_members WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("readStoredMinutes failed habbo={}", habboId, e);
        }
        return 0;
    }

    private static void persistMinutes(int habboId, int minutes) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_corporation_members SET worked_minutes_in_cycle = ? WHERE habbo_id = ?")) {
            ps.setInt(1, minutes);
            ps.setInt(2, habboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("persistMinutes failed habbo={} minutes={}", habboId, minutes, e);
        }
    }
}
