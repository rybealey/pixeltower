package org.pixeltower.rp.fight.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.StaffGate;
import org.pixeltower.rp.fight.RoomFlags;

/**
 * {@code :setzone <safe|unsafe>} — staff toggle for the current room's
 * {@code rp_room_flags.no_pvp}. Acts on {@code habbo.getCurrentRoom()}
 * so staff only have to be standing in the target room — no id lookup.
 *
 * Gated via {@link StaffGate}.
 */
public class SetZoneCommand extends Command {

    public SetZoneCommand() {
        super(null, new String[] {"setzone"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        if (!StaffGate.isStaff(staff)) {
            staff.whisper("You don't have permission to run :setzone.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 2) {
            staff.whisper("Usage: :setzone <safe|unsafe>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        boolean safe;
        if ("safe".equalsIgnoreCase(params[1])) {
            safe = true;
        } else if ("unsafe".equalsIgnoreCase(params[1])) {
            safe = false;
        } else {
            staff.whisper("Zone type must be 'safe' or 'unsafe'.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Room room = staff.getHabboInfo().getCurrentRoom();
        if (room == null) {
            staff.whisper("You need to be in the room you want to flag.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!RoomFlags.setNoPvp(room.getId(), safe)) {
            staff.whisper("Failed to update the zone flag — check the logs.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String verb = safe ? "marks this room as a safe zone" : "marks this room as unsafe";
        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername() + " " + verb + "*");
        return true;
    }
}
