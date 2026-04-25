package org.pixeltower.rp.corp;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Display-only motto override worn while a player is clocked in. Sets
 * {@code "[WORKING] <rank title>"} on {@link com.eu.habbo.habbohotel.users.HabboInfo}
 * in memory and broadcasts via {@link RoomUserDataComposer} — the DB row
 * {@code users.motto} is never touched, so the player's chosen motto
 * survives crashes and is restored on clock-out (or fire / quit / disconnect).
 *
 * <p>The cached "original" motto in {@link #SAVED} only exists to avoid a
 * DB read on restore. On disconnect we drop the cache rather than restore,
 * because {@code HabboInfo} is being torn down and the next login reloads
 * motto fresh from the DB.</p>
 */
public final class WorkingMotto {

    private static final String PREFIX = "[WORKING] ";

    private static final Map<Integer, String> SAVED = new ConcurrentHashMap<>();

    private WorkingMotto() {}

    /** Cache the current motto and overwrite it with {@code [WORKING] rankTitle}. */
    public static void apply(Habbo habbo, String rankTitle) {
        if (habbo == null) return;
        int habboId = habbo.getHabboInfo().getId();
        String original = habbo.getHabboInfo().getMotto();
        // Idempotent: re-apply during an active override keeps the original cache.
        SAVED.putIfAbsent(habboId, original == null ? "" : original);

        habbo.getHabboInfo().setMotto(PREFIX + rankTitle);
        broadcast(habbo);
    }

    /** Restore the cached motto for an online habbo. No-op if no override is active. */
    public static void restore(Habbo habbo) {
        if (habbo == null) return;
        int habboId = habbo.getHabboInfo().getId();
        String original = SAVED.remove(habboId);
        if (original == null) return;

        habbo.getHabboInfo().setMotto(original);
        broadcast(habbo);
    }

    /**
     * Restore by id — looks the habbo up in the online cache. If they're
     * offline we just drop the SAVED entry; their next login reloads motto
     * from the DB (which still holds the original).
     */
    public static void restoreById(int habboId) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        if (habbo != null) {
            restore(habbo);
        } else {
            SAVED.remove(habboId);
        }
    }

    /** Drop cache without broadcasting. For disconnect paths where {@code HabboInfo} is gone. */
    public static void clear(int habboId) {
        SAVED.remove(habboId);
    }

    /**
     * Whether an override is currently active for this habbo (i.e. they're
     * mid-shift and the {@code [WORKING] X} motto is being displayed).
     */
    public static boolean isActive(int habboId) {
        return SAVED.containsKey(habboId);
    }

    /**
     * Update the cached "original" so a motto change attempted mid-shift is
     * applied at clock-out instead of clobbering the working badge. No-op if
     * no override is active for this habbo (so we don't pollute the cache for
     * users who aren't on duty).
     */
    public static void updateIntent(int habboId, String newOriginal) {
        SAVED.computeIfPresent(habboId, (k, v) -> newOriginal == null ? "" : newOriginal);
    }

    private static void broadcast(Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room != null) {
            room.sendComposer(new RoomUserDataComposer(habbo).compose());
        }
    }
}
