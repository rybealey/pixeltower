package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.ShiftManager;

/**
 * {@code :stopwork} — clock out. Stops countdown whispers and halts
 * paychecks until the next {@code :startwork}. Any partially-accrued
 * minutes in the current cycle are discarded.
 */
public class StopWorkCommand extends Command {

    public StopWorkCommand() {
        super(null, new String[] {"stopwork"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        int habboId = caller.getHabboInfo().getId();

        if (!ShiftManager.stopWork(habboId)) {
            caller.whisper("You aren't clocked in.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        caller.whisper("You've clocked out.", RoomChatMessageBubbles.WIRED);
        return true;
    }
}
