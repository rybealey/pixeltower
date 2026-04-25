package org.pixeltower.rp.stats;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Per-habbo bleed-out timer for {@code :suicide}. Ticks every
 * {@link #TICK_INTERVAL_S}s, decrementing HP by {@link #HP_PER_TICK}
 * via {@link StatsManager#adjustHp}. Self-cancels once the target hits
 * zero (the death side effects fire from inside adjustHp).
 *
 * State is in-memory only — a process restart drops any pending bleed-
 * outs, which is acceptable: the player's HP at restart is whatever the
 * last persisted tick wrote, and they can re-issue {@code :suicide} if
 * they still want to die.
 */
public final class SuicideService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuicideService.class);

    private static final int HP_PER_TICK = 2;
    private static final long TICK_INTERVAL_S = 1L;

    private static final Map<Integer, ScheduledFuture<?>> FUTURES = new ConcurrentHashMap<>();

    private SuicideService() {}

    public static boolean isRunning(int habboId) {
        return FUTURES.containsKey(habboId);
    }

    /**
     * Arm the bleed timer for {@code habboId}. Returns {@code false} if
     * a timer is already running — caller should treat as a no-op.
     */
    public static boolean start(int habboId) {
        if (FUTURES.containsKey(habboId)) return false;
        ScheduledFuture<?> f = Emulator.getThreading().getService().scheduleAtFixedRate(
                () -> tick(habboId),
                TICK_INTERVAL_S,
                TICK_INTERVAL_S,
                TimeUnit.SECONDS);
        FUTURES.put(habboId, f);
        return true;
    }

    /** Cancel any pending bleed-out for {@code habboId}. No-op if absent. */
    public static void cancel(int habboId) {
        ScheduledFuture<?> f = FUTURES.remove(habboId);
        if (f != null) f.cancel(false);
    }

    private static void tick(int habboId) {
        try {
            PlayerStats before = StatsManager.get(habboId).orElse(null);
            if (before == null || before.getHp() <= 0) {
                cancel(habboId);
                return;
            }
            StatsManager.adjustHp(habboId, -HP_PER_TICK);

            // Push the owner's Stats HUD so the bar drains live. adjustHp
            // already broadcasts to viewers via TargetService; the owner
            // composer is separate.
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            if (habbo != null && habbo.getClient() != null) {
                StatsManager.get(habboId).ifPresent(stats ->
                        habbo.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
            }

            PlayerStats after = StatsManager.get(habboId).orElse(null);
            if (after == null || after.getHp() <= 0) {
                cancel(habboId);
            }
        } catch (RuntimeException e) {
            LOGGER.error("SuicideService tick failed habbo={}", habboId, e);
            cancel(habboId);
        }
    }
}
