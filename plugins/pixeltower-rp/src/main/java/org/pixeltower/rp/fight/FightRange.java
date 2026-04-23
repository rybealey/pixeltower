package org.pixeltower.rp.fight;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Tile-distance helpers. Chebyshev distance (a.k.a. "king move" /
 * 8-neighbour) is the right metric for adjacency on an isometric tile
 * grid: both axis-aligned and diagonal neighbours count as "one tile
 * away", matching the visual sense of melee range.
 */
public final class FightRange {

    private FightRange() {}

    /**
     * Chebyshev tile distance between two habbos. Returns
     * {@link Integer#MAX_VALUE} if either habbo isn't placed in a room —
     * a caller treating the result as "too far" short-circuits cleanly.
     */
    public static int chebyshev(Habbo a, Habbo b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        RoomUnit ua = a.getRoomUnit();
        RoomUnit ub = b.getRoomUnit();
        if (ua == null || ub == null || !ua.isInRoom() || !ub.isInRoom()) {
            return Integer.MAX_VALUE;
        }
        int dx = Math.abs(ua.getX() - ub.getX());
        int dy = Math.abs(ua.getY() - ub.getY());
        return Math.max(dx, dy);
    }

    /**
     * True iff both habbos are in the same room and the tile distance
     * between them is within {@code maxTiles}.
     */
    public static boolean withinRange(Habbo a, Habbo b, int maxTiles) {
        if (a == null || b == null) return false;
        if (a.getHabboInfo().getCurrentRoom() == null
                || b.getHabboInfo().getCurrentRoom() == null) return false;
        if (a.getHabboInfo().getCurrentRoom().getId()
                != b.getHabboInfo().getCurrentRoom().getId()) return false;
        return chebyshev(a, b) <= maxTiles;
    }
}
