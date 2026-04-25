package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;

/**
 * {@code :quitjob} — leave your corporation voluntarily. No permission
 * gate; works at any rank. Stops any active shift as a side effect.
 */
public class QuitJobCommand extends Command {

    public QuitJobCommand() {
        super(null, new String[] {"quitjob"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        int habboId = caller.getHabboInfo().getId();

        // Capture corp name BEFORE quitting (cache entry gets removed).
        String corpName = CorporationManager.getMembership(habboId)
                .flatMap(m -> CorporationManager.getById(m.getCorpId()))
                .map(Corporation::getName)
                .orElse("the corporation");

        try {
            CorporationManager.quit(habboId);
        } catch (NotInCorporationException e) {
            caller.whisper("You aren't in a corporation.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        RpChat.emote(caller, "*resigns from their role at " + corpName + "*");
        return true;
    }
}
