package org.pixeltower.rp.fight;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative in-memory registry of active {@code rp_fights} rows.
 * One row per {@link FightKey} — per-hit forensics stream into
 * {@code rp_fight_hits}.
 *
 * Lifecycle:
 * <ul>
 *   <li>{@link #openOrGet} — lazy-creates the row on the first hit.</li>
 *   <li>{@link #recordHit} — updates counters in memory and on disk.</li>
 *   <li>{@link #close} — sets {@code ended_at} + {@code ender_reason}
 *       and drops the cache entry. Reasons: {@code knockout},
 *       {@code timeout}, {@code logout}, {@code roomchange}, {@code staff}.</li>
 *   <li>{@link #reapTimeouts} — fixed-rate sweeper called by a
 *       scheduled task; closes any engagement idle longer than
 *       {@code rp.fight.engagement_timeout_s}.</li>
 * </ul>
 */
public final class EngagementRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngagementRegistry.class);

    private static final Map<FightKey, Engagement> ACTIVE = new ConcurrentHashMap<>();

    private EngagementRegistry() {}

    /**
     * Find-or-create the engagement for this hit. The caller supplies the
     * direction (who swung), which only matters when creating a new row —
     * subsequent hits from either direction land on the same key.
     */
    public static Engagement openOrGet(int attackerId, int defenderId, int roomId) {
        FightKey key = FightKey.of(attackerId, defenderId, roomId);
        return ACTIVE.computeIfAbsent(key, k -> openRow(attackerId, defenderId, roomId));
    }

    /**
     * Record a hit into the existing engagement + persist one
     * {@code rp_fight_hits} row + update the engagement counters on disk.
     */
    public static void recordHit(Engagement e, int actorId, int targetId,
                                 int rawDamage, int finalDamage) {
        long now = System.currentTimeMillis();
        if (actorId == e.getAttackerId()) {
            e.recordAttackerHit(finalDamage, now);
        } else {
            e.recordDefenderHit(finalDamage, now);
        }
        insertFightHit(e.getFightRowId(), actorId, targetId, rawDamage, finalDamage);
        updateFightCounters(e);
    }

    public static void close(Engagement e, String reason) {
        FightKey key = FightKey.of(e.getAttackerId(), e.getDefenderId(), e.getRoomId());
        ACTIVE.remove(key);
        finalizeRow(e.getFightRowId(), reason);
    }

    /**
     * Close every active engagement this habbo is a party to with
     * {@code reason}. Used by the room-exit / disconnect handlers.
     */
    public static void terminateAll(int habboId, String reason) {
        List<Engagement> toClose = new ArrayList<>();
        ACTIVE.forEach((key, eng) -> {
            if (key.involves(habboId)) toClose.add(eng);
        });
        for (Engagement e : toClose) {
            close(e, reason);
        }
    }

    /**
     * Sweep expired engagements. Uses the engagement's in-memory
     * {@code lastHitAtMs} — the DB counter row would require a read, and
     * this is the hotter code path of the two. Idempotent: if an engagement
     * is closed between the listing and the {@code close()} call, it's
     * already gone from {@link #ACTIVE}.
     */
    public static void reapTimeouts() {
        long timeoutMs = Emulator.getConfig().getInt("rp.fight.engagement_timeout_s", 30) * 1000L;
        long now = System.currentTimeMillis();
        List<Engagement> stale = new ArrayList<>();
        ACTIVE.forEach((key, eng) -> {
            if (now - eng.getLastHitAtMs() > timeoutMs) stale.add(eng);
        });
        for (Engagement e : stale) {
            close(e, "timeout");
        }
    }

    // ──────────── SQL ────────────

    private static Engagement openRow(int attackerId, int defenderId, int roomId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_fights (attacker_id, defender_id, room_id) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, attackerId);
            ps.setInt(2, defenderId);
            ps.setInt(3, roomId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("rp_fights insert returned no generated key");
                }
                long id = keys.getLong(1);
                return new Engagement(id, attackerId, defenderId, roomId,
                        System.currentTimeMillis());
            }
        } catch (SQLException e) {
            LOGGER.error("openRow failed att={} def={} room={}",
                    attackerId, defenderId, roomId, e);
            throw new RuntimeException(e);
        }
    }

    private static void insertFightHit(long fightId, int actorId, int targetId,
                                       int rawDamage, int finalDamage) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_fight_hits "
                             + "(fight_id, actor_id, target_id, raw_damage, final_damage) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, fightId);
            ps.setInt(2, actorId);
            ps.setInt(3, targetId);
            ps.setInt(4, rawDamage);
            ps.setInt(5, finalDamage);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("insert rp_fight_hits failed fight={} actor={} target={}",
                    fightId, actorId, targetId, e);
        }
    }

    private static void updateFightCounters(Engagement e) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_fights SET "
                             + "attacker_hits = ?, defender_hits = ?, "
                             + "total_damage_to_defender = ?, total_damage_to_attacker = ? "
                             + "WHERE id = ?")) {
            ps.setInt(1, e.getAttackerHits());
            ps.setInt(2, e.getDefenderHits());
            ps.setInt(3, e.getTotalDamageToDefender());
            ps.setInt(4, e.getTotalDamageToAttacker());
            ps.setLong(5, e.getFightRowId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.error("update rp_fights counters failed id={}", e.getFightRowId(), ex);
        }
    }

    private static void finalizeRow(long fightId, String reason) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_fights SET ended_at = CURRENT_TIMESTAMP, ender_reason = ? "
                             + "WHERE id = ? AND ended_at IS NULL")) {
            ps.setString(1, reason);
            ps.setLong(2, fightId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("finalize rp_fights failed id={} reason={}", fightId, reason, e);
        }
    }
}
