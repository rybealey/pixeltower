package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationMember;
import org.pixeltower.rp.corp.exceptions.CorpPermissionDeniedException;
import org.pixeltower.rp.corp.exceptions.IllegalCorpRankException;
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;

/**
 * {@code :fire <user|x>} — remove a habbo from the caller's corp.
 * Requires {@code CAN_FIRE}. Target must be in the caller's corp at a
 * strictly lower rank.
 */
public class FireCommand extends Command {

    public FireCommand() {
        super(null, new String[] {"fire"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        if (params.length < 2) {
            caller.whisper("Usage: :fire <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Capture target's corp name BEFORE firing (cache entry gets removed).
        String corpName = CorporationManager.getMembership(resolved.habboId)
                .flatMap(m -> CorporationManager.getById(m.getCorpId()))
                .map(Corporation::getName)
                .orElse("the corporation");

        try {
            CorporationManager.fire(caller.getHabboInfo().getId(), resolved.habboId);
        } catch (NotInCorporationException e) {
            if ("caller".equals(e.getWho())) {
                caller.whisper("You aren't in a corporation.", RoomChatMessageBubbles.ALERT);
            } else {
                caller.whisper(resolved.username + " isn't in your corporation.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        } catch (CorpPermissionDeniedException e) {
            caller.whisper("You don't have permission to fire.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalCorpRankException e) {
            caller.whisper("You can't fire someone at or above your rank.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        caller.whisper("You fired " + resolved.username + ".", RoomChatMessageBubbles.WIRED);
        if (resolved.isOnline()) {
            resolved.online.whisper("You've been fired from " + corpName + ".",
                    RoomChatMessageBubbles.WIRED);
        }
        return true;
    }
}
