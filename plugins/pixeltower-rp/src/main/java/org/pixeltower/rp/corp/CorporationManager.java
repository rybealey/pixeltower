package org.pixeltower.rp.corp;

import com.eu.habbo.Emulator;
import org.pixeltower.rp.corp.exceptions.AlreadyInCorporationException;
import org.pixeltower.rp.corp.exceptions.CorpPermissionDeniedException;
import org.pixeltower.rp.corp.exceptions.CorpRankNotFoundException;
import org.pixeltower.rp.corp.exceptions.IllegalCorpRankException;
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory corp registry + mutation layer. Corps + ranks are loaded once
 * at plugin init (they change rarely and AtomCMS housekeeping isn't wired
 * yet). Membership is loaded at init and then maintained via write-through
 * on every {@link #hire}/{@link #fire}/{@link #promote} call.
 *
 * Tier 1 scope: plugin-only. Cross-process invalidation (e.g. another node
 * editing {@code rp_corporations}) isn't a concern yet — Pixeltower runs a
 * single emulator.
 */
public final class CorporationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorporationManager.class);

    private static final Map<Integer, Corporation> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Corporation> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Integer, CorporationMember> MEMBER_BY_HABBO = new ConcurrentHashMap<>();

    private CorporationManager() {}

    public static void init() {
        BY_ID.clear();
        BY_KEY.clear();
        MEMBER_BY_HABBO.clear();

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            loadCorps(conn);
            loadRanks(conn);
            loadMembers(conn);
        } catch (SQLException e) {
            LOGGER.error("CorporationManager init failed", e);
            throw new RuntimeException("CorporationManager init failed", e);
        }

        int rankCount = BY_ID.values().stream().mapToInt(c -> c.getRanks().size()).sum();
        LOGGER.info("CorporationManager initialized: {} corps, {} ranks, {} members",
                BY_ID.size(), rankCount, MEMBER_BY_HABBO.size());
    }

    private static void loadCorps(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, corp_key, name, hq_room_id, paycheck_interval_s, stock_capacity "
                        + "FROM rp_corporations");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer hqRoomId = rs.getObject("hq_room_id", Integer.class);
                Corporation corp = new Corporation(
                        rs.getInt("id"),
                        rs.getString("corp_key"),
                        rs.getString("name"),
                        hqRoomId,
                        rs.getInt("paycheck_interval_s"),
                        rs.getInt("stock_capacity"));
                BY_ID.put(corp.getId(), corp);
                BY_KEY.put(corp.getCorpKey(), corp);
            }
        }
    }

    private static void loadRanks(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT corp_id, rank_num, title, salary, permissions FROM rp_corporation_ranks");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int corpId = rs.getInt("corp_id");
                Corporation corp = BY_ID.get(corpId);
                if (corp == null) {
                    LOGGER.warn("orphaned rp_corporation_ranks row corp_id={} rank_num={}",
                            corpId, rs.getInt("rank_num"));
                    continue;
                }
                corp.addRank(new CorporationRank(
                        rs.getInt("rank_num"),
                        rs.getString("title"),
                        rs.getLong("salary"),
                        rs.getLong("permissions")));
            }
        }
    }

    private static void loadMembers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT corp_id, habbo_id, rank_num, hired_at FROM rp_corporation_members");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("hired_at");
                CorporationMember m = new CorporationMember(
                        rs.getInt("corp_id"),
                        rs.getInt("habbo_id"),
                        rs.getInt("rank_num"),
                        ts != null ? ts.toInstant() : Instant.now());
                MEMBER_BY_HABBO.put(m.getHabboId(), m);
            }
        }
    }

    // ──────────── reads ────────────

    public static Optional<Corporation> getByKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static Optional<Corporation> getById(int id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<CorporationMember> getMembership(int habboId) {
        return Optional.ofNullable(MEMBER_BY_HABBO.get(habboId));
    }

    public static boolean hasPermission(int habboId, RankPermission p) {
        CorporationMember m = MEMBER_BY_HABBO.get(habboId);
        if (m == null) return false;
        Corporation c = BY_ID.get(m.getCorpId());
        if (c == null) return false;
        CorporationRank r = c.getRanks().get(m.getRankNum());
        return r != null && r.has(p);
    }

    /** All cached members. Safe for read-only iteration (values view of a ConcurrentHashMap). */
    public static Iterable<CorporationMember> allMembers() {
        return MEMBER_BY_HABBO.values();
    }

    // ──────────── mutations ────────────

    /**
     * Add {@code target} to the caller's corp at {@code rankNum}. Caller
     * must have {@link RankPermission#CAN_HIRE}; {@code rankNum} must exist
     * and be strictly below the caller's rank.
     */
    public static void hire(int callerHabboId, int targetHabboId, int rankNum)
            throws NotInCorporationException, CorpPermissionDeniedException,
                   AlreadyInCorporationException, CorpRankNotFoundException,
                   IllegalCorpRankException {
        CorporationMember caller = MEMBER_BY_HABBO.get(callerHabboId);
        if (caller == null) throw new NotInCorporationException("caller");
        Corporation corp = BY_ID.get(caller.getCorpId());
        if (corp == null) throw new IllegalStateException("caller's corp missing from cache");
        CorporationRank callerRank = corp.getRanks().get(caller.getRankNum());
        if (callerRank == null || !callerRank.has(RankPermission.CAN_HIRE)) {
            throw new CorpPermissionDeniedException(RankPermission.CAN_HIRE);
        }
        if (MEMBER_BY_HABBO.containsKey(targetHabboId)) {
            throw new AlreadyInCorporationException();
        }
        if (corp.getRanks().get(rankNum) == null) {
            throw new CorpRankNotFoundException(rankNum, corp.getCorpKey());
        }
        if (rankNum >= caller.getRankNum()) {
            throw new IllegalCorpRankException("hire_at_or_above_caller");
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_corporation_members (corp_id, habbo_id, rank_num) "
                             + "VALUES (?, ?, ?)")) {
            ps.setInt(1, corp.getId());
            ps.setInt(2, targetHabboId);
            ps.setInt(3, rankNum);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("hire failed caller={} target={} corp={} rank={}",
                    callerHabboId, targetHabboId, corp.getId(), rankNum, e);
            throw new RuntimeException("hire DB write failed", e);
        }

        MEMBER_BY_HABBO.put(targetHabboId,
                new CorporationMember(corp.getId(), targetHabboId, rankNum, Instant.now()));
        LOGGER.info("hire caller={} target={} corp={} rank={}",
                callerHabboId, targetHabboId, corp.getId(), rankNum);
    }

    /**
     * Remove {@code target} from the caller's corp. Caller must have
     * {@link RankPermission#CAN_FIRE}; target must be in the same corp
     * and at a strictly lower rank than the caller.
     */
    public static void fire(int callerHabboId, int targetHabboId)
            throws NotInCorporationException, CorpPermissionDeniedException,
                   IllegalCorpRankException {
        CorporationMember caller = MEMBER_BY_HABBO.get(callerHabboId);
        if (caller == null) throw new NotInCorporationException("caller");
        CorporationMember target = MEMBER_BY_HABBO.get(targetHabboId);
        if (target == null) throw new NotInCorporationException("target");
        if (caller.getCorpId() != target.getCorpId()) {
            throw new NotInCorporationException("target");
        }
        Corporation corp = BY_ID.get(caller.getCorpId());
        CorporationRank callerRank = corp.getRanks().get(caller.getRankNum());
        if (callerRank == null || !callerRank.has(RankPermission.CAN_FIRE)) {
            throw new CorpPermissionDeniedException(RankPermission.CAN_FIRE);
        }
        if (target.getRankNum() >= caller.getRankNum()) {
            throw new IllegalCorpRankException("fire_at_or_above_caller");
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM rp_corporation_members WHERE corp_id = ? AND habbo_id = ?")) {
            ps.setInt(1, corp.getId());
            ps.setInt(2, targetHabboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("fire failed caller={} target={} corp={}",
                    callerHabboId, targetHabboId, corp.getId(), e);
            throw new RuntimeException("fire DB write failed", e);
        }

        MEMBER_BY_HABBO.remove(targetHabboId);
        ShiftManager.stopWork(targetHabboId);
        LOGGER.info("fire caller={} target={} corp={}",
                callerHabboId, targetHabboId, corp.getId());
    }

    /**
     * Move {@code target} to {@code newRank} within the caller's corp.
     * Caller must have {@link RankPermission#CAN_PROMOTE}; new rank must
     * exist, must be strictly above the target's current rank, and
     * strictly below the caller's rank.
     */
    public static void promote(int callerHabboId, int targetHabboId, int newRank)
            throws NotInCorporationException, CorpPermissionDeniedException,
                   CorpRankNotFoundException, IllegalCorpRankException {
        CorporationMember caller = MEMBER_BY_HABBO.get(callerHabboId);
        if (caller == null) throw new NotInCorporationException("caller");
        CorporationMember target = MEMBER_BY_HABBO.get(targetHabboId);
        if (target == null) throw new NotInCorporationException("target");
        if (caller.getCorpId() != target.getCorpId()) {
            throw new NotInCorporationException("target");
        }
        Corporation corp = BY_ID.get(caller.getCorpId());
        CorporationRank callerRank = corp.getRanks().get(caller.getRankNum());
        if (callerRank == null || !callerRank.has(RankPermission.CAN_PROMOTE)) {
            throw new CorpPermissionDeniedException(RankPermission.CAN_PROMOTE);
        }
        if (corp.getRanks().get(newRank) == null) {
            throw new CorpRankNotFoundException(newRank, corp.getCorpKey());
        }
        if (newRank <= target.getRankNum()) {
            throw new IllegalCorpRankException("promote_not_upward");
        }
        if (newRank >= caller.getRankNum()) {
            throw new IllegalCorpRankException("promote_at_or_above_caller");
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_corporation_members SET rank_num = ? "
                             + "WHERE corp_id = ? AND habbo_id = ?")) {
            ps.setInt(1, newRank);
            ps.setInt(2, corp.getId());
            ps.setInt(3, targetHabboId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("promote failed caller={} target={} corp={} newRank={}",
                    callerHabboId, targetHabboId, corp.getId(), newRank, e);
            throw new RuntimeException("promote DB write failed", e);
        }

        target.setRankNum(newRank);
        LOGGER.info("promote caller={} target={} corp={} newRank={}",
                callerHabboId, targetHabboId, corp.getId(), newRank);
    }
}
