package org.pixeltower.rp.core;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped "who's my current target?" cache for chat-command
 * substitution. Populated when a player opens a profile card (via the
 * Arcturus {@code UserProfileCardViewedEvent} hook added by our patch) or
 * runs {@code :target <user>} explicitly. Cleared on disconnect.
 *
 * Pure in-memory — no DB persistence. A restart/relogin starts with an
 * empty target; clicking a user re-populates it.
 */
public final class TargetTracker {

    private static final ConcurrentHashMap<Integer, Integer> TARGETS = new ConcurrentHashMap<>();

    private TargetTracker() {}

    /**
     * Set {@code viewer}'s current target to {@code target}. Self-targeting
     * is a no-op — we treat it as a clear so that clicking your own profile
     * card doesn't trap you into commands that refuse self-target.
     */
    public static void set(int viewerHabboId, int targetHabboId) {
        if (viewerHabboId == targetHabboId) {
            TARGETS.remove(viewerHabboId);
            return;
        }
        TARGETS.put(viewerHabboId, targetHabboId);
    }

    public static Optional<Integer> get(int viewerHabboId) {
        return Optional.ofNullable(TARGETS.get(viewerHabboId));
    }

    public static void clear(int viewerHabboId) {
        TARGETS.remove(viewerHabboId);
    }

    /**
     * Reverse lookup: viewer ids whose current target == {@code targetHabboId}.
     * Scan-based (O(n)) — fine at retro-server player counts; revisit if the
     * forward map ever grows past a few thousand entries.
     */
    public static Set<Integer> viewersOf(int targetHabboId) {
        Set<Integer> result = new HashSet<>();
        TARGETS.forEach((viewer, target) -> {
            if (target == targetHabboId) result.add(viewer);
        });
        return result;
    }
}
