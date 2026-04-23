package org.pixeltower.rp.stats;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.TargetService;
import org.pixeltower.rp.death.DeathState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory read cache for {@code rp_player_stats}. Populated on
 * {@code UserLoginEvent}, dropped on {@code UserDisconnectEvent}. The
 * table is lazy-seeded on first login via {@code INSERT IGNORE} — every
 * column has a default, so no explicit column list is needed.
 *
 * Tier 1 scope: this manager is read-only from the outside. The DTO
 * exposes package-private setters so a future Tier 2 fight / XP commit
 * can add write-through mutation methods here.
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
        boolean wasDead = cached != null && cached.getHp() <= 0;
        if (cached != null) {
            maxHp = cached.getMaxHp();
            maxEnergy = cached.getMaxEnergy();
        } else {
            PlayerStats fetched = fetch(habboId);
            if (fetched == null) return false;
            maxHp = fetched.getMaxHp();
            maxEnergy = fetched.getMaxEnergy();
        }
        if (!persistHpEnergy(habboId, maxHp, maxEnergy)) return false;
        if (cached != null) {
            cached.setHp(maxHp);
            cached.setEnergy(maxEnergy);
        }
        if (wasDead) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            if (habbo != null) DeathState.exit(habbo);
        }
        TargetService.broadcastStatsUpdate(habboId);
        return true;
    }

    /**
     * Staff-invoked instant KO: drop {@code habboId}'s HP to 0 without
     * touching energy or max_hp. Offline users get the DB row updated;
     * online users also get a cache write-through. Returns {@code false}
     * iff the user has no stats row at all.
     */
    public static boolean killPlayer(int habboId) {
        PlayerStats cached = CACHE.get(habboId);
        if (cached == null && fetch(habboId) == null) return false;
        if (!persistHp(habboId, 0)) return false;
        if (cached != null) cached.setHp(0);
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        if (habbo != null) DeathState.enter(habbo);
        TargetService.broadcastStatsUpdate(habboId);
        return true;
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
}
