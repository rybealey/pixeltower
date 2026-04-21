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
import org.pixeltower.rp.corp.CorporationRank;
import org.pixeltower.rp.corp.exceptions.CorpPermissionDeniedException;
import org.pixeltower.rp.corp.exceptions.CorpRankNotFoundException;
import org.pixeltower.rp.corp.exceptions.IllegalCorpRankException;
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;

/**
 * {@code :promote <user|x> [rank_num]} — move a corp member up the ladder.
 * Default rank_num is {@code target.rank + 1}. Requires {@code CAN_PROMOTE};
 * new rank must be strictly above the target's current rank and strictly
 * below the caller's rank. Downward movement lives on a future {@code :demote}.
 */
public class PromoteCommand extends Command {

    public PromoteCommand() {
        super(null, new String[] {"promote"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        if (params.length < 2) {
            caller.whisper("Usage: :promote <user|x> [rank_num]", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        CorporationMember targetMember = CorporationManager.getMembership(resolved.habboId)
                .orElse(null);
        if (targetMember == null) {
            caller.whisper(resolved.username + " isn't in a corporation.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int newRank;
        if (params.length >= 3) {
            try {
                newRank = Integer.parseInt(params[2]);
            } catch (NumberFormatException e) {
                caller.whisper("Rank must be a whole number.", RoomChatMessageBubbles.ALERT);
                return true;
            }
        } else {
            newRank = targetMember.getRankNum() + 1;
        }

        try {
            CorporationManager.promote(caller.getHabboInfo().getId(), resolved.habboId, newRank);
        } catch (NotInCorporationException e) {
            if ("caller".equals(e.getWho())) {
                caller.whisper("You aren't in a corporation.", RoomChatMessageBubbles.ALERT);
            } else {
                caller.whisper(resolved.username + " isn't in your corporation.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        } catch (CorpPermissionDeniedException e) {
            caller.whisper("You don't have permission to promote.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (CorpRankNotFoundException e) {
            caller.whisper("Rank " + e.getRankNum() + " doesn't exist in "
                    + e.getCorpKey() + ".", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalCorpRankException e) {
            if ("promote_not_upward".equals(e.getReason())) {
                caller.whisper("Promotions must move upward. Use :demote for the other direction.",
                        RoomChatMessageBubbles.ALERT);
            } else {
                caller.whisper("You can only promote below your own rank.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        }

        Corporation corp = CorporationManager.getById(targetMember.getCorpId()).orElseThrow();
        CorporationRank rank = corp.getRanks().get(newRank);
        String rankTitle = rank != null ? rank.getTitle() : ("rank " + newRank);

        caller.whisper("You promoted " + resolved.username + " to " + rankTitle + ".",
                RoomChatMessageBubbles.WIRED);
        if (resolved.isOnline()) {
            resolved.online.whisper("You've been promoted to " + rankTitle + ".",
                    RoomChatMessageBubbles.WIRED);
        }
        return true;
    }
}
