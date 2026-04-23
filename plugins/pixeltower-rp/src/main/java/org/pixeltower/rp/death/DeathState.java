package org.pixeltower.rp.death;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import org.pixeltower.rp.stats.StatsManager;

/**
 * Runtime death-state primitives. When a player's HP hits 0 they're laid
 * down and frozen; {@link #reapplyIfDead} exists because HP persists in
 * {@code rp_player_stats} but the RoomUnit flags (LAY status, canWalk) do
 * not — a player who disconnects while dead comes back standing and mobile
 * unless we re-apply on room-enter.
 *
 * Stateless — the source of truth is {@link StatsManager}'s HP cache plus
 * the live {@link RoomUnit} flags.
 */
public final class DeathState {

    private DeathState() {}

    /**
     * Apply lay posture + freeze + broadcast. No-op if the habbo isn't in
     * a room. Idempotent: safe to call on an already-dead player.
     *
     * Mirrors vanilla {@code :lay} — cardinal-snap rotation so the lay
     * pose renders correctly, LAY status with 0.5 height offset, and the
     * cmdLay/cmdSit flags. The walkability check {@code :lay} does on the
     * 3 tiles in front is intentionally skipped: you die wherever you're
     * standing.
     */
    public static void enter(Habbo habbo) {
        if (habbo == null) return;
        RoomUnit unit = habbo.getRoomUnit();
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (unit == null || room == null) return;

        int rot = unit.getBodyRotation().getValue();
        unit.setRotation(RoomUserRotation.fromValue(rot - rot % 2));
        unit.cmdLay = true;
        unit.cmdSit = true;

        unit.setStatus(RoomUnitStatus.LAY, "0.5");
        unit.setCanWalk(false);

        room.sendComposer(new RoomUserStatusComposer(unit).compose());
    }

    /**
     * Clear lay + unfreeze + broadcast. No-op if the habbo isn't in a room.
     */
    public static void exit(Habbo habbo) {
        if (habbo == null) return;
        RoomUnit unit = habbo.getRoomUnit();
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (unit == null || room == null) return;

        unit.removeStatus(RoomUnitStatus.LAY);
        unit.cmdLay = false;
        unit.cmdSit = false;
        unit.setCanWalk(true);

        room.sendComposer(new RoomUserStatusComposer(unit).compose());
    }

    /**
     * Re-apply death state on room-enter if the cached HP is 0. Called
     * from the login/room-enter flow after the RoomUnit is placed in the
     * room (see PixeltowerRP.attemptReapplyAfterReady).
     */
    public static void reapplyIfDead(Habbo habbo) {
        if (habbo == null) return;
        int habboId = habbo.getHabboInfo().getId();
        StatsManager.get(habboId).ifPresent(stats -> {
            if (stats.getHp() <= 0) enter(habbo);
        });
    }
}
