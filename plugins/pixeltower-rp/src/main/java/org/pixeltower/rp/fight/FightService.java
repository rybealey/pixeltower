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

    private FightService() {}

    /**
     * Attempt an attacker→defender hit. Returns {@link Optional#empty()}
     * on success; otherwise a reason string the command layer can
     * whisper. Non-range preconditions (energy, cooldown, safe-room,
     * alive, etc.) are checked inside {@link FightRules#canEngage} — the
     * caller can also call {@code canEngage} first for pre-flight
     * validation and skip this method entirely on deny.
     */
    public static Optional<String> hit(Habbo attacker, Habbo defender) {
        Optional<String> deny = FightRules.canEngage(attacker, defender);
        if (deny.isPresent()) return deny;

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

        // Mutate stats: defender takes damage, attacker spends energy.
        // adjustHp handles the HP→0 side effects (DeathState + rp_downed_players).
        int hpBefore = defStats.getHp();
        StatsManager.adjustHp(defenderId, -dmg.finalDamage());
        int energyPerHit = Emulator.getConfig().getInt("rp.fight.energy_per_hit", 10);
        StatsManager.adjustEnergy(attackerId, -energyPerHit);

        LAST_SWING_AT_MS.put(attackerId, System.currentTimeMillis());

        pushStatsToOwner(attacker);
        pushStatsToOwner(defender);

        // Knockout close: link the downed row to this fight so P5 revive +
        // future bounty attribution have the engagement id.
        if (hpBefore > 0 && defStats.getHp() <= 0) {
            linkDownedToFight(defenderId, attackerId, engagement.getFightRowId());
            EngagementRegistry.close(engagement, "knockout");
        }

        return Optional.empty();
    }

    /** Last-swing timestamp for {@code habboId}; 0 if never swung this session. */
    public static long getLastSwingAtMs(int habboId) {
        Long ts = LAST_SWING_AT_MS.get(habboId);
        return ts == null ? 0L : ts;
    }

    /** Evict cooldown entries for a disconnecting user. Called from the disconnect handler. */
    public static void onDisconnect(int habboId) {
        LAST_SWING_AT_MS.remove(habboId);
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
