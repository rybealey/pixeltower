package org.pixeltower.rp.core.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;

/**
 * {@code :dance [1-4]} — toggle / select a dance for yourself.
 *
 * No-arg behavior:
 *   - if currently dancing → stop
 *   - else                 → start the standard Dance (id 1)
 *
 * IDs follow the Arcturus {@link DanceType} ordinals: 1=Dance, 2=Pogo Mogo,
 * 3=Duck Funk, 4=The Rollie.
 */
public class DanceCommand extends Command {

    public DanceCommand() {
        super(null, new String[] {"dance"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        RoomUnit unit = caller.getRoomUnit();
        Room room = caller.getHabboInfo().getCurrentRoom();
        if (unit == null || room == null || !unit.isInRoom()) {
            caller.whisper("You need to be in a room to dance.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        DanceType target;
        if (params.length < 2) {
            target = unit.getDanceType() == DanceType.NONE
                    ? DanceType.HAB_HOP
                    : DanceType.NONE;
        } else {
            DanceType resolved = resolve(params[1]);
            if (resolved == null) return true;
            target = resolved;
        }

        unit.setDanceType(target);
        room.sendComposer(new RoomUserDanceComposer(unit).compose());
        return true;
    }

    private static DanceType resolve(String arg) {
        return switch (arg.trim()) {
            case "1" -> DanceType.HAB_HOP;
            case "2" -> DanceType.POGO_MOGO;
            case "3" -> DanceType.DUCK_FUNK;
            case "4" -> DanceType.THE_ROLLIE;
            default  -> null;
        };
    }
}
