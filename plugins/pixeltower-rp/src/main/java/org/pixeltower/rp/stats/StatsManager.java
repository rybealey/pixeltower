package org.pixeltower.rp.stats;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.TargetService;
import org.pixeltower.rp.death.DeathState;
import org.pixeltower.rp.medical.RespawnScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory read cache for {@code rp_player_stats}. Populated on
 * {@code UserLoginEvent}, dropped on {@code UserDisconnectEvent}. The
 * table is lazy-seeded on first login via {@code INSERT IGNORE} — every
 * column has a default, so no explicit column list is needed.
 *
 * Mutation API:
 * <ul>
 *   <li>{@link #adjustHp} / {@link #adjustEnergy} — clamped signed-delta
 *       mutators; write through cache + DB + target broadcast. HP crossings
 *       of zero dispatch the death / revive hooks.</li>
 *   <li>{@link #killPlayer} — instant HP=0 (same death path as adjustHp).</li>
 *   <li>{@link #restoreStats} — full HP+energy refill (revive path).</li>
 * </ul>
 */
public final class StatsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsManager.class);

    private static final Map<Integer, PlayerStats> CACHE = new ConcurrentHashMap<>();

    private StatsManager() {}

    /**
     * Fetch-or-create the user's stats row and cache it. Idempotent —
     * safe to call on every UserLoginEvent.
     */
    public static PlayerStats onLogin(int habboId) {
        PlayerStats stats = fetch(habboId);
        if (stats == null) {
            insertDefaultRow(habboId);
            stats = fetch(habboId);
            if (stats == null) {
                LOGGER.error("StatsManager.onLogin failed to create row for habbo={}", habboId);
                return null;
            }
        }
        CACHE.put(habboId, stats);
        return stats;
    }

    public static void onDisconnect(int habboId) {
        CACHE.remove(habboId);
    }

    public static Optional<PlayerStats> get(int habboId) {
        return Optional.ofNullable(CACHE.get(habboId));
    }

    /**
     * Cache-first lookup with DB fallback. Used by Target HUD pushes — we
     * want live stats for any clicked-on player whether they're currently
     * cached (online) or not. DB hits are NOT promoted into the cache so
     * the contract "cache = online users only" is preserved.
     */
    public static Optional<PlayerStats> getOrFetch(int habboId) {
        PlayerStats cached = CACHE.get(habboId);
        if (cached != null) return Optional.of(cached);
        return Optional.ofNullable(fetch(habboId));
    }

    /**
     * Signed HP mutation clamped to {@code [0, max_hp]}. When the delta
     * crosses zero downward or upward, the death / revive side-effects
     * fire (DeathState posture change + {@code rp_downed_players} row).
     *
     * Returns {@code false} only if the target has no stats row; a no-op
     * delta (already at max / already at 0 + negative delta) still returns
     * {@code true} — the call succeeded, just had nothing to do.
     */
    public static boolean adjustHp(int habboId, int delta) {
        PlayerStats cached = CACHE.get(habboId);
        int before;
        int maxHp;
        if (cached != null) {
            before = cached.getHp();
            maxHp = cached.getMaxHp();
        } else {
            PlayerStats fetched = fetch(habboId);
            if (fetched == null) return false;
            before = fetched.getHp();
            maxHp = fetched.getMaxHp();
        }
        int after = Math.max(0, Math.min(maxHp, before + delta));
        if (after == before) return true;
        if (!persistHp(habboId, after)) return false;
        if (cached != null) cached.setHp(after);

        if (before > 0 && after == 0) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            onDeath(habboId, habbo, null, null);
        } else if (before == 0 && after > 0) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            onRevive(habboId, habbo);
        }
        TargetService.broadcastStatsUpdate(habboId);
        return true;
    }

    /**
     * Signed energy mutation clamped to {@code [0, max_energy]}. No
     * special side effects — energy doesn't have a "zero" game state
     * (running out just locks out the next :hit until regen ticks).
     */
    public static boolean adjustEnergy(int habboId, int delta) {
        PlayerStats cached = CACHE.get(habboId);
        int before;
        int maxEnergy;
        if (cached != null) {
            before = cached.getEnergy();
            maxEnergy = cached.getMaxEnergy();
        } else {
            PlayerStats fetched = fetch(habboId);
            if (fetched == null) return false;
            before = fetched.getEnergy();
            maxEnergy = fetched.getMaxEnergy();
        }
        int after = Math.max(0, Math.min(maxEnergy, before + delta));
        if (after == before) return true;
        if (!persistEnergy(habboId, after)) return false;
        if (cached != null) cached.setEnergy(after);
        TargetService.broadcastStatsUpdate(habboId);
        return true;
    }

    /**
     * Full HP + energy refill for {@code habboId}. Persists to
     * {@code rp_player_stats} and, if the user is cached (online), writes
     * through to the cache so subsequent {@link #get} reads are consistent.
     * Offline users get their DB row updated but the cache is untouched —
     * preserving the "cache = online users only" contract.
     *
     * Returns {@code false} iff the user has no stats row at all (missing
     * habbo id / never logged in post-schema). Callers are expected to push
     * the resulting {@link PlayerStats} to the client via
     * {@link org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer}.
     */
    public static boolean restoreStats(int habboId) {
        PlayerStats cached = CACHE.get(habboId);
        int maxHp;
        int maxEnergy;
        boolean wasDead;
        if (cached != null) {
            maxHp = cached.getMaxHp();
            maxEnergy = cached.getMaxEnergy();
            wasDead = cached.getHp() <= 0;
        } else {
            PlayerStats fetched = fetch(habboId);
            if (fetched == null) return false;
            maxHp = fetched.getMaxHp();
            maxEnergy = fetched.getMaxEnergy();
            wasDead = fetched.getHp() <= 0;
        }
        if (!persistHpEnergy(habboId, maxHp, maxEnergy)) return false;
        if (cached != null) {
            cached.setHp(maxHp);
            cached.setEnergy(maxEnergy);
        }
        if (wasDead) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            onRevive(habboId, habbo);
        }
        TargetService.broadcastStatsUpdate(habboId);
        return true;
    }

    /**
     * Staff-invoked instant KO: drop {@code habboId}'s HP to 0 without
     * touching energy or max_hp. Offline users get the DB row updated;
     * online users also get a cache write-through plus the full death
     * side-effects ({@link DeathState#enter} + {@code rp_downed_players}
     * row). Returns {@code false} iff the user has no stats row at all.
     */
    public static boolean killPlayer(int habboId) {
        PlayerStats cached = CACHE.get(habboId);
        int before;
        if (cached != null) {
            before = cached.getHp();
        } else {
            PlayerStats fetched = fetch(habboId);
            if (fetched == null) return false;
            before = fetched.getHp();
        }
        if (before <= 0) return true;
        if (!persistHp(habboId, 0)) return false;
        if (cached != null) cached.setHp(0);
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        onDeath(habboId, habbo, null, null);
        TargetService.broadcastStatsUpdate(habboId);
        return true;
    }

    // ──────────── Death / revive hooks ────────────

    /**
     * Fire the side-effects for an HP→0 transition: apply lay+freeze if
     * the habbo is in a room (DeathState is a no-op otherwise) and record
     * a {@code rp_downed_players} row anchoring the respawn clock.
     *
     * {@code downedById} / {@code fightId} are both nullable — staff
     * {@code :kill} / {@code :fighttest} have no attacker context;
     * {@code FightService} (Tier 2 P2+) will pass them populated.
     */
    private static void onDeath(int habboId, Habbo habbo,
                                Integer downedById, Long fightId) {
        Integer roomId = null;
        if (habbo != null) {
            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) {
                roomId = room.getId();
                DeathState.enter(habbo);
            }
        }
        insertDownedPlayer(habboId, roomId, downedById, fightId);
        long respawnDelayS = Emulator.getConfig().getInt("rp.medical.respawn_timeout_s", 180);
        RespawnScheduler.schedule(habboId, respawnDelayS);
    }

    private static void onRevive(int habboId, Habbo habbo) {
        RespawnScheduler.cancel(habboId);
        deleteDownedPlayer(habboId);
        if (habbo != null) DeathState.exit(habbo);
    }

    // ──────────── SQL ────────────

    private static PlayerStats fetch(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT hp, max_hp, energy, max_energy, level, xp, "
                             + "skill_points_unspent, skill_hit, skill_endurance, skill_stamina "
                             + "FROM rp_player_stats WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PlayerStats(
                        habboId,
                        rs.getInt("hp"),
                        rs.getInt("max_hp"),
                        rs.getInt("energy"),
                        rs.getInt("max_energy"),
                        rs.getInt("level"),
                        rs.getInt("xp"),
                        rs.getInt("skill_points_unspent"),
                        rs.getInt("skill_hit"),
                        rs.getInt("skill_endurance"),
                        rs.getInt("skill_stamina"));
            }
        } catch (SQLException e) {
            LOGGER.error("fetch stats failed habbo={}", habboId, e);
            throw new RuntimeException(e);
        }
    }

    private static boolean persistHp(int habboId, int hp) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_player_stats SET hp = ? WHERE habbo_id = ?")) {
            ps.setInt(1, hp);
            ps.setInt(2, habboId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("persist hp failed habbo={}", habboId, e);
            throw new RuntimeException(e);
        }
    }

    private static boolean persistEnergy(int habboId, int energy) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_player_stats SET energy = ? WHERE habbo_id = ?")) {
            ps.setInt(1, energy);
            ps.setInt(2, habboId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("persist energy failed habbo={}", habboId, e);
            throw new RuntimeException(e);
        }
    }

    private static boolean persistHpEnergy(int habboId, int hp, int energy) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_player_stats SET hp = ?, energy = ? WHERE habbo_id = ?")) {
            ps.setInt(1, hp);
            ps.setInt(2, energy);
            ps.setInt(3, habboId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("persist hp/energy failed habbo={}", habboId, e);
            throw new RuntimeException(e);
        }
    }

    private static void insertDefaultRow(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO rp_player_stats (habbo_id) VALUES (?)")) {
            ps.setInt(1, habboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("insert default stats row failed habbo={}", habboId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Upsert a {@code rp_downed_players} row for {@code habboId}. The
     * auxiliary-table insert is log-and-swallow rather than re-throw —
     * a secondary-table DB error must not brick the primary HP persist
     * that already succeeded.
     */
    private static void insertDownedPlayer(int habboId, Integer roomId,
                                           Integer downedById, Long fightId) {
        int timeoutS = Emulator.getConfig().getInt("rp.medical.respawn_timeout_s", 180);
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_downed_players "
                             + "(habbo_id, downed_in_room, downed_by_id, fight_id, respawn_at) "
                             + "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND)) "
                             + "ON DUPLICATE KEY UPDATE "
                             + "  downed_at = CURRENT_TIMESTAMP, "
                             + "  downed_in_room = VALUES(downed_in_room), "
                             + "  downed_by_id = VALUES(downed_by_id), "
                             + "  fight_id = VALUES(fight_id), "
                             + "  respawn_at = VALUES(respawn_at)")) {
            ps.setInt(1, habboId);
            if (roomId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, roomId);
            if (downedById == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, downedById);
            if (fightId == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, fightId);
            ps.setInt(5, timeoutS);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("insert rp_downed_players failed habbo={}", habboId, e);
        }
    }

    private static void deleteDownedPlayer(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM rp_downed_players WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("delete rp_downed_players failed habbo={}", habboId, e);
        }
    }
}
