package org.pixeltower.rp.medical.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.death.DeathState;
import org.pixeltower.rp.medical.RespawnScheduler;

/**
 * {@code :respawn} — voluntary early-out from the downed state. Same
 * outcome as waiting out the timer (teleport + restore + penalty), but
 * the player doesn't have to sit through the remaining seconds. Anti-
 * trap: if no paramedic is online, the player can opt to pay the fee
 * and get on with their life instead of being held hostage.
 */
public class RespawnCommand extends Command {

    public RespawnCommand() {
        super(null, new String[] {"respawn"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        int habboId = caller.getHabboInfo().getId();

        if (!DeathState.isDead(habboId)) {
            caller.whisper("You're not downed.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        RespawnScheduler.runNow(habboId);
        return true;
    }
}
