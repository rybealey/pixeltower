package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationMember;
import org.pixeltower.rp.corp.ShiftManager;

/**
 * {@code :startwork} — clock in to your corp. You only earn paychecks while
 * clocked in; the per-minute countdown whisper kicks off on the next
 * shift-tick.
 */
public class StartWorkCommand extends Command {

    public StartWorkCommand() {
        super(null, new String[] {"startwork"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        int habboId = caller.getHabboInfo().getId();

        CorporationMember membership = CorporationManager.getMembership(habboId).orElse(null);
        if (membership == null) {
            caller.whisper("You aren't in a corporation.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (ShiftManager.isOnDuty(habboId)) {
            caller.whisper("You're already clocked in.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ShiftManager.startWork(habboId);

        Corporation corp = CorporationManager.getById(membership.getCorpId()).orElse(null);
        String corpName = corp != null ? corp.getName() : "your corp";
        caller.whisper("You've clocked in at " + corpName + ".",
                RoomChatMessageBubbles.WIRED);
        return true;
    }
}
