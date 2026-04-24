package org.pixeltower.rp.functional;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Paired teleport that fires on walk-on instead of click. Extends the
 * stock {@link InteractionTeleport} so the whole Arcturus teleport-pair
 * infrastructure (items_teleport table, emerge animation, occupancy
 * guards) keeps working — we only change the trigger edge.
 *
 * Bound via items_base.interaction_type = 'rp_teleport_walkon' in
 * emulator/pixeltower-custom-catalog.sql and registered against that
 * string in PixeltowerRP.onEmulatorLoadItemsManager.
 *
 * onClick is intentionally left to the parent so admins can still click
 * an unpaired teleport to get the pairing dialog; only the trigger edge
 * for already-paired teleports is moved to walk-on.
 */
public class InteractionWalkOnTeleport extends InteractionTeleport {

    public InteractionWalkOnTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWalkOnTeleport(int id, int userId, Item item, String extradata,
                                     int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
        if (roomUnit == null || room == null) return;
        if (roomUnit.getRoomUnitType() != RoomUnitType.USER) return;
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) return;
        GameClient client = habbo.getClient();
        if (client == null) return;
        // Reuse the stock teleport's click-branch — handles paired emerge
        // and is a no-op if the teleport has no partner, so a user who
        // steps on an unpaired one doesn't get a surprise pair dialog.
        super.onClick(client, room, objects);
    }
}
