package org.pixeltower.rp.functional;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory index of {@code rp_functional_furniture} keyed by
 * {@code item_base_id}, plus the per-(player, placed-furni) cooldown cache
 * that gates re-fires.
 *
 * Loaded once at plugin start (after the DB pool is up) and refreshed via
 * {@link #reload()} — exposed for a future {@code :rpreload} staff command.
 *
 * Cold-boot order matters: the rp_functional interaction class is registered
 * in PixeltowerRP.onEmulatorLoadItemsManagerEvent (BEFORE Item rows bind to
 * interaction classes), but {@link #loadAll()} runs later in
 * onEmulatorLoadedEvent so the DB pool is ready. Walk-on lookups during the
 * gap return {@link Optional#empty()} and silently no-op.
 */
public final class FunctionalFurnitureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionalFurnitureService.class);

    private static final int COOLDOWN_LRU_CAP = 4096;

    private static volatile Map<Integer, Map<TriggerType, FunctionalAction>> ACTIONS_BY_BASE_ID =
            Collections.emptyMap();

    /** Bounded LRU; access-order eviction once we hit the cap. */
    private static final Map<String, Long> COOLDOWNS =
            Collections.synchronizedMap(new LinkedHashMap<>(COOLDOWN_LRU_CAP * 4 / 3, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return this.size() > COOLDOWN_LRU_CAP;
                }
            });

    private FunctionalFurnitureService() {}

    public static void loadAll() {
        Map<Integer, Map<TriggerType, FunctionalAction>> next = new HashMap<>();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT item_base_id, trigger_type, action_type, action_payload, cooldown_ms "
                             + "FROM rp_functional_furniture WHERE enabled = 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int itemBaseId = rs.getInt("item_base_id");
                TriggerType trigger = TriggerType.fromDb(rs.getString("trigger_type"));
                FunctionalAction action = new FunctionalAction(
                        itemBaseId,
                        trigger,
                        rs.getString("action_type"),
                        rs.getString("action_payload"),
                        rs.getInt("cooldown_ms"));
                next.computeIfAbsent(itemBaseId, k -> new EnumMap<>(TriggerType.class))
                        .put(trigger, action);
            }
        } catch (SQLException e) {
            LOGGER.error("FunctionalFurnitureService.loadAll failed", e);
            return;
        }
        ACTIONS_BY_BASE_ID = next;
        LOGGER.info("FunctionalFurnitureService loaded {} furni base(s)", next.size());
    }

    public static Optional<FunctionalAction> lookup(int itemBaseId, TriggerType trigger) {
        Map<TriggerType, FunctionalAction> byTrigger = ACTIONS_BY_BASE_ID.get(itemBaseId);
        if (byTrigger == null) return Optional.empty();
        return Optional.ofNullable(byTrigger.get(trigger));
    }

    /**
     * Returns true and stamps now if the cooldown has elapsed (or there's no
     * prior fire); returns false otherwise. Cooldown ≤ 0 means no gating —
     * always fires.
     *
     * Keyed by (habbo, placed-furni, trigger) so walk-on / walk-off / click
     * have independent clocks — a dressing room's walk-off "close" must not
     * be blocked by the walk-on "open" that fired a moment earlier.
     */
    public static boolean tryFire(int habboId, int placedFurniId,
                                  TriggerType trigger, int cooldownMs) {
        if (cooldownMs <= 0) return true;
        long now = System.currentTimeMillis();
        String key = habboId + ":" + placedFurniId + ":" + trigger.name();
        synchronized (COOLDOWNS) {
            Long last = COOLDOWNS.get(key);
            if (last != null && (now - last) < cooldownMs) return false;
            COOLDOWNS.put(key, now);
            return true;
        }
    }

    public static void reload() {
        loadAll();
    }
}
