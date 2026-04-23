package org.pixeltower.rp.fight;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates one swing end-to-end: precondition check → damage roll →
 * HP/energy mutation → fight-row + hit-log persistence → knockout-close
 * side-effects. {@link FightRules} owns the "can this happen?" answer;
 * this class owns the "make it happen" sequence.
 *
 * Per-attacker cooldowns live here because they're a rate-limit on the
 * swing action itself, not on any particular engagement (A hitting B
 * then immediately hitting C should still trip cooldown).
 */
public final class FightService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FightService.class);

    private static final Map<Integer, Long> LAST_SWING_AT_MS = new ConcurrentHashMap<>();

    /**
     * Per-attacker fractional energy debt. {@code rp.fight.energy_per_hit}
     * is a double (e.g. 0.2 = one point every five swings); each hit adds
     * that cost into this accumulator, and only the integer portion
     * debits real energy. Lets fatigue drain slowly enough to be a
     * session-level concern instead of a per-fight gate.
     *
     * Session-only — cleared on disconnect alongside the cooldown map.
     */
    private static final Map<Integer, Double> ENERGY_DEBT = new ConcurrentHashMap<>();

    private FightService() {}

    /**
     * Outcome of a {@link #hit} attempt. Either landed (with the final
     * damage applied and a knockout flag) or denied (with a reason the
     * command layer should whisper to the attacker).
     */
    public record HitResult(String denyReason, int damage, boolean knockout) {
        public boolean denied() { return denyReason != null; }
        public static HitResult deny(String reason) { return new HitResult(reason, 0, false); }
        public static HitResult land(int damage, boolean knockout) {
            return new HitResult(null, damage, knockout);
        }
    }

    /**
     * Attempt an attacker→defender hit. Returns a {@link HitResult}
     * carrying either the denial reason or the damage applied. Non-
     * range preconditions (energy, cooldown, safe-room, alive, etc.)
     * are checked inside {@link FightRules#canEngage}.
     */
    public static HitResult hit(Habbo attacker, Habbo defender) {
        Optional<String> deny = FightRules.canEngage(attacker, defender);
        if (deny.isPresent()) return HitResult.deny(deny.get());

        int attackerId = attacker.getHabboInfo().getId();
        int defenderId = defender.getHabboInfo().getId();
        int roomId = attacker.getHabboInfo().getCurrentRoom().getId();

        PlayerStats attStats = StatsManager.get(attackerId).orElseThrow();
        PlayerStats defStats = StatsManager.get(defenderId).orElseThrow();

        DamageResolver.Result dmg = DamageResolver.compute(
                attStats.getSkillHit(), defStats.getSkillEndurance());

        Engagement engagement = EngagementRegistry.openOrGet(attackerId, defenderId, roomId);
        EngagementRegistry.recordHit(engagement, attackerId, defenderId,
                dmg.rawDamage(), dmg.finalDamage());

        // Mutate stats: defender takes damage, attacker spends fractional energy.
        // adjustHp handles the HP→0 side effects (DeathState + rp_downed_players).
        int hpBefore = defStats.getHp();
        StatsManager.adjustHp(defenderId, -dmg.finalDamage());
        debitFractionalEnergy(attackerId);

        LAST_SWING_AT_MS.put(attackerId, System.currentTimeMillis());

        pushStatsToOwner(attacker);
        pushStatsToOwner(defender);

        // Knockout close: link the downed row to this fight so P5 revive +
        // future bounty attribution have the engagement id.
        boolean knockout = hpBefore > 0 && defStats.getHp() <= 0;
        if (knockout) {
            linkDownedToFight(defenderId, attackerId, engagement.getFightRowId());
            EngagementRegistry.close(engagement, "knockout");
        }

        return HitResult.land(dmg.finalDamage(), knockout);
    }

    /** Last-swing timestamp for {@code habboId}; 0 if never swung this session. */
    public static long getLastSwingAtMs(int habboId) {
        Long ts = LAST_SWING_AT_MS.get(habboId);
        return ts == null ? 0L : ts;
    }

    /** Evict cooldown entries for a disconnecting user. Called from the disconnect handler. */
    public static void onDisconnect(int habboId) {
        LAST_SWING_AT_MS.remove(habboId);
        ENERGY_DEBT.remove(habboId);
    }

    /**
     * Accumulate the attacker's per-swing fractional cost and debit only
     * the integer carry. With {@code energy_per_hit=0.2}, that's 1 energy
     * every 5 swings; with {@code 0.4}, 2 every 5; a setting of {@code 0}
     * disables drain entirely.
     */
    private static void debitFractionalEnergy(int attackerId) {
        double costPerHit = Emulator.getConfig().getDouble("rp.fight.energy_per_hit", 0.2);
        if (costPerHit <= 0) return;
        double newDebt = ENERGY_DEBT.getOrDefault(attackerId, 0.0) + costPerHit;
        int toDebit = (int) newDebt;
        if (toDebit > 0) {
            StatsManager.adjustEnergy(attackerId, -toDebit);
            newDebt -= toDebit;
        }
        ENERGY_DEBT.put(attackerId, newDebt);
    }

    private static void pushStatsToOwner(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) return;
        StatsManager.get(habbo.getHabboInfo().getId()).ifPresent(stats ->
                habbo.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
    }

    /**
     * Populate {@code rp_downed_players.fight_id} + {@code downed_by_id}
     * for the knockout that just happened. {@link StatsManager#adjustHp}
     * inserted the row with both fields NULL (no fight context at that
     * layer); we backfill here now that we have the engagement id.
     */
    private static void linkDownedToFight(int defenderId, int attackerId, long fightId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE rp_downed_players SET fight_id = ?, downed_by_id = ? "
                             + "WHERE habbo_id = ?")) {
            ps.setLong(1, fightId);
            ps.setInt(2, attackerId);
            ps.setInt(3, defenderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("link rp_downed_players fight_id failed defender={} fight={}",
                    defenderId, fightId, e);
        }
    }
}
