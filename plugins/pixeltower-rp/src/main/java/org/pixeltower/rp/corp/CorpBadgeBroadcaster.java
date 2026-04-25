package org.pixeltower.rp.corp;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.outgoing.UpdateCorpBadgesComposer;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds and broadcasts the per-room {@code {habboId -> corp badge code}}
 * map that the Pixeltower-patched Nitro client uses to override the
 * favorite-Habbo-Group badge slot in the user infostand. Always sends the
 * full refreshed map (idempotent) — there's no separate "remove" wire
 * format. Mirrors {@code RoomUsersGuildBadgesComposer}'s
 * send-the-whole-thing strategy.
 */
public final class CorpBadgeBroadcaster {

    private CorpBadgeBroadcaster() {}

    /**
     * Build a {@code {habboId -> badge code}} map of every corp-employed
     * habbo currently in the room (whose corp has a {@code badge_code} set).
     */
    public static Map<Integer, String> buildRoomMap(Room room) {
        Map<Integer, String> map = new HashMap<>();
        if (room == null) return map;
        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getHabboInfo() == null) continue;
            int habboId = habbo.getHabboInfo().getId();
            CorporationManager.getBadgeCodeFor(habboId).ifPresent(code -> map.put(habboId, code));
        }
        return map;
    }

    /** Broadcast the room's corp-badge map to every habbo currently in it. */
    public static void pushFullToRoom(Room room) {
        if (room == null) return;
        room.sendComposer(new UpdateCorpBadgesComposer(buildRoomMap(room)).compose());
    }

    /**
     * Re-broadcast the room map for the habbo's current room, if any. Used
     * after hire / fire / quit so the override appears or disappears live
     * for other room occupants without re-entering the room. No-op if the
     * habbo is offline or not in a room.
     */
    public static void pushUserChange(int habboId) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        if (habbo == null) return;
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;
        pushFullToRoom(room);
    }

    /**
     * Re-broadcast every active room's map. Called from
     * {@link CorporationManager#init()} so a hot-reload of corp data
     * immediately pushes a fresh override to every connected client.
     */
    public static void pushAllRooms() {
        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            pushFullToRoom(room);
        }
    }
}
