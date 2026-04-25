package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationMember;
import org.pixeltower.rp.corp.CorporationRank;
import org.pixeltower.rp.corp.exceptions.AlreadyInCorporationException;
import org.pixeltower.rp.corp.exceptions.CorpPermissionDeniedException;
import org.pixeltower.rp.corp.exceptions.CorpRankNotFoundException;
import org.pixeltower.rp.corp.exceptions.IllegalCorpRankException;
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;

/**
 * {@code :hire <user|x> [rank_num]} — add a habbo to the caller's corp at
 * {@code rank_num} (default 1). Requires {@code CAN_HIRE} on the caller's
 * rank; cross-corp hiring isn't a thing — you hire into your own corp.
 * Rank must be strictly below the caller's rank.
 *
 * Works on offline targets (no online-presence check). The target hears
 * no notification until login (Tier 1 has no corp panel yet).
 */
public class HireCommand extends Command {

    public HireCommand() {
        super(null, new String[] {"hire"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        if (params.length < 2) {
            caller.whisper("Usage: :hire <user|x> [rank_num]", RoomChatMessageBubbles.ALERT);
            return true;
        }

        int rankNum = 1;
        if (params.length >= 3) {
            try {
                rankNum = Integer.parseInt(params[2]);
            } catch (NumberFormatException e) {
                caller.whisper("Rank must be a whole number.", RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            CorporationManager.hire(caller.getHabboInfo().getId(), resolved.habboId, rankNum);
        } catch (NotInCorporationException e) {
            caller.whisper("You aren't in a corporation.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (CorpPermissionDeniedException e) {
            caller.whisper("You don't have permission to hire.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (AlreadyInCorporationException e) {
            caller.whisper(resolved.username + " is already in a corporation.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (CorpRankNotFoundException e) {
            caller.whisper("Rank " + e.getRankNum() + " doesn't exist in "
                    + e.getCorpKey() + ".", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalCorpRankException e) {
            caller.whisper("You can only hire below your own rank.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Re-resolve caller's corp + the newly-assigned rank for messaging.
        CorporationMember callerMember = CorporationManager
                .getMembership(caller.getHabboInfo().getId()).orElseThrow();
        Corporation corp = CorporationManager.getById(callerMember.getCorpId()).orElseThrow();
        CorporationRank newRank = corp.getRanks().get(rankNum);
        String rankTitle = newRank != null ? newRank.getTitle() : ("rank " + rankNum);

        RpChat.corpEmote(caller,
                "*hires " + resolved.username + " at " + corp.getName() + "*");
        if (resolved.isOnline()) {
            resolved.online.whisper("You've been hired as " + rankTitle
                    + " at " + corp.getName() + ".", RoomChatMessageBubbles.WIRED);
        }
        return true;
    }
}
