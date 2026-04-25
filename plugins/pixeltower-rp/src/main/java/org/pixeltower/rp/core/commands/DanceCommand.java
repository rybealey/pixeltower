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
 * {@code :dance [name]} — toggle / select a dance for yourself.
 *
 * No-arg behavior:
 *   - if currently dancing → stop
 *   - else                 → start the standard Dance ({@code HAB_HOP})
 *
 * Named: {@code :dance dance | pogo mogo | duck funk | the rollie}.
 * Names are case-insensitive and accept the squished form
 * (e.g. {@code pogomogo}, {@code therollie}) since the chat split would
 * otherwise turn multi-word names into multiple params.
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
            StringBuilder joined = new StringBuilder();
            for (int i = 1; i < params.length; i++) {
                if (i > 1) joined.append(' ');
                joined.append(params[i]);
            }
            DanceType resolved = resolve(joined.toString());
            if (resolved == null) {
                caller.whisper("Usage: :dance [dance | pogo mogo | duck funk | the rollie]",
                        RoomChatMessageBubbles.ALERT);
                return true;
            }
            target = resolved;
        }

        unit.setDanceType(target);
        room.sendComposer(new RoomUserDanceComposer(unit).compose());
        return true;
    }

    private static DanceType resolve(String name) {
        String key = name.trim().toLowerCase().replace(" ", "");
        return switch (key) {
            case "dance"     -> DanceType.HAB_HOP;
            case "pogomogo"  -> DanceType.POGO_MOGO;
            case "duckfunk"  -> DanceType.DUCK_FUNK;
            case "therollie" -> DanceType.THE_ROLLIE;
            default          -> null;
        };
    }
}
