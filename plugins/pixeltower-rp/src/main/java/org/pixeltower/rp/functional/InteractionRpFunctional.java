package org.pixeltower.rp.functional;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Generic interaction class bound to the items_base.interaction_type string
 * {@code "rp_functional"}. Extends {@link InteractionDefault} so toggle/state,
 * walk-effect, and the rest of Arcturus's vanilla furni semantics still work
 * — we only add the rp action dispatch on top.
 *
 * Walk-on dispatch is gated by the per-(player, placed-furni) cooldown in
 * {@link FunctionalFurnitureService#tryFire(int, int, int)}. Vanilla
 * {@code InteractionDefault.onMove} only fires {@code onWalkOn} when a unit's
 * tile actually changes, but the cooldown also protects against click-trigger
 * spam (which has no walk-off equivalent) and against placing two of the same
 * functional furni adjacent to each other.
 *
 * Bots and other non-USER {@link RoomUnitType} units are explicitly skipped:
 * dispatching to a phantom GameClient would NPE in
 * {@link FunctionalActionDispatcher#dispatch}.
 */
public class InteractionRpFunctional extends InteractionDefault {

    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionRpFunctional.class);

    public InteractionRpFunctional(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRpFunctional(int id, int userId, Item item, String extradata,
                                   int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
        fireFor(roomUnit, room, TriggerType.WALK_ON);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
        fireFor(roomUnit, room, TriggerType.WALK_OFF);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
        if (client == null) return;
        Habbo habbo = client.getHabbo();
        if (habbo == null) return;
        FunctionalFurnitureService.lookup(this.getBaseItem().getId(), TriggerType.CLICK)
                .ifPresent(action -> {
                    if (FunctionalFurnitureService.tryFire(
                            habbo.getHabboInfo().getId(), this.getId(),
                            TriggerType.CLICK, action.cooldownMs())) {
                        FunctionalActionDispatcher.dispatch(habbo, action);
                    }
                });
    }

    private void fireFor(RoomUnit roomUnit, Room room, TriggerType trigger) {
        // TEMP DEBUG: trace every walk-on / walk-off call so we can pinpoint
        // why the dressing-room walk-on dispatch isn't reaching the client.
        // Strip once the issue is diagnosed.
        LOGGER.info("rp_functional {} fired: baseId={} placedFurniId={} unit={} unitType={}",
                trigger,
                this.getBaseItem() != null ? this.getBaseItem().getId() : -1,
                this.getId(),
                roomUnit != null ? roomUnit.getId() : -1,
                roomUnit != null ? roomUnit.getRoomUnitType() : "null");
        if (roomUnit == null || room == null) return;
        if (roomUnit.getRoomUnitType() != RoomUnitType.USER) return;
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) {
            LOGGER.warn("rp_functional {} skipped — room.getHabbo(unit) returned null", trigger);
            return;
        }
        java.util.Optional<FunctionalAction> action = FunctionalFurnitureService.lookup(
                this.getBaseItem().getId(), trigger);
        if (action.isEmpty()) {
            LOGGER.warn("rp_functional {} skipped — no row in rp_functional_furniture for baseId={}",
                    trigger, this.getBaseItem().getId());
            return;
        }
        if (!FunctionalFurnitureService.tryFire(
                habbo.getHabboInfo().getId(), this.getId(),
                trigger, action.get().cooldownMs())) {
            LOGGER.info("rp_functional {} cooldown-gated for habboId={} placedFurni={}",
                    trigger, habbo.getHabboInfo().getId(), this.getId());
            return;
        }
        LOGGER.info("rp_functional {} dispatching action_type={} payload={} to habboId={}",
                trigger, action.get().actionType(), action.get().payload(),
                habbo.getHabboInfo().getId());
        FunctionalActionDispatcher.dispatch(habbo, action.get());
    }
}
