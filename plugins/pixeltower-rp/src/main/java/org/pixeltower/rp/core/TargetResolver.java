package org.pixeltower.rp.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;

import java.util.Optional;

/**
 * Resolves a command-arg username to a concrete habbo identity, with
 * special handling for the literal {@code "x"} (case-insensitive) that
 * substitutes the caller's current {@link TargetTracker} entry.
 *
 * Commands that accept a username argument should call this helper
 * instead of lookups of their own — single source of truth for how
 * target substitution works and what error messages look like.
 *
 * Typical usage:
 * <pre>{@code
 *   try {
 *       ResolvedTarget t = TargetResolver.resolve(sender, params[1]);
 *       // t.habboId  — always present
 *       // t.username — always present
 *       // t.online   — nullable; null means offline
 *   } catch (NoTargetException e) {
 *       sender.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
 *       return true;
 *   } catch (NoSuchUserException e) {
 *       sender.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
 *       return true;
 *   }
 * }</pre>
 */
public final class TargetResolver {

    private TargetResolver() {}

    public static final class ResolvedTarget {
        public final int habboId;
        public final String username;
        /** Non-null when the resolved habbo has an active gameclient. */
        public final Habbo online;

        ResolvedTarget(int habboId, String username, Habbo online) {
            this.habboId = habboId;
            this.username = username;
            this.online = online;
        }

        public boolean isOnline() {
            return online != null;
        }
    }

    public static ResolvedTarget resolve(Habbo caller, String nameOrX)
            throws NoTargetException, NoSuchUserException {
        if (nameOrX == null || nameOrX.isEmpty()) {
            throw new NoSuchUserException("");
        }

        if ("x".equalsIgnoreCase(nameOrX)) {
            Optional<Integer> targetId = TargetTracker.get(caller.getHabboInfo().getId());
            if (targetId.isEmpty()) throw new NoTargetException();
            return resolveById(targetId.get());
        }
        return resolveByName(nameOrX);
    }

    private static ResolvedTarget resolveById(int habboId) throws NoSuchUserException {
        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        if (online != null) {
            return new ResolvedTarget(
                    online.getHabboInfo().getId(),
                    online.getHabboInfo().getUsername(),
                    online);
        }
        HabboInfo offline = HabboManager.getOfflineHabboInfo(habboId);
        if (offline == null) throw new NoSuchUserException(String.valueOf(habboId));
        return new ResolvedTarget(offline.getId(), offline.getUsername(), null);
    }

    private static ResolvedTarget resolveByName(String name) throws NoSuchUserException {
        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(name);
        if (online != null) {
            return new ResolvedTarget(
                    online.getHabboInfo().getId(),
                    online.getHabboInfo().getUsername(),
                    online);
        }
        HabboInfo offline = HabboManager.getOfflineHabboInfo(name);
        if (offline == null) throw new NoSuchUserException(name);
        return new ResolvedTarget(offline.getId(), offline.getUsername(), null);
    }
}
