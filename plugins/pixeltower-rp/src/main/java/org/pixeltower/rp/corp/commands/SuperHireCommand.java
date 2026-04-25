package org.pixeltower.rp.corp.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.StaffGate;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationRank;
import org.pixeltower.rp.corp.exceptions.AlreadyInCorporationException;
import org.pixeltower.rp.corp.exceptions.CorpRankNotFoundException;

/**
 * {@code :superhire <user|x> <corp_key> [rank]} — staff force-hire into
 * any corp at any rank. Bypasses the caller-rank gate that {@link
 * HireCommand} enforces (since the actor is staff, not a corp member).
 * Rank defaults to the corp's lowest rank_num when omitted.
 *
 * Gated by {@code rp.admin.min_rank}.
 */
public class SuperHireCommand extends Command {

    public SuperHireCommand() {
        super(null, new String[] {"superhire"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        if (!StaffGate.isStaff(staff)) {
            staff.whisper("You don't have permission to run :superhire.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 3) {
            staff.whisper("Usage: :superhire <user|x> <corp_key> [rank]",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String corpKey = params[2];
        Corporation corp = CorporationManager.getByKey(corpKey).orElse(null);
        if (corp == null) {
            staff.whisper("No corporation with key '" + corpKey + "'.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int rankNum;
        if (params.length >= 4) {
            try {
                rankNum = Integer.parseInt(params[3]);
            } catch (NumberFormatException e) {
                staff.whisper("Rank must be a whole number.", RoomChatMessageBubbles.ALERT);
                return true;
            }
        } else {
            CorporationRank entry = corp.getEntryRank().orElse(null);
            if (entry == null) {
                staff.whisper(corp.getName() + " has no ranks defined yet.",
                        RoomChatMessageBubbles.ALERT);
                return true;
            }
            rankNum = entry.getRankNum();
        }

        try {
            CorporationManager.superHire(resolved.habboId, corp.getId(), rankNum);
        } catch (AlreadyInCorporationException e) {
            staff.whisper(resolved.username + " is already in a corporation.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (CorpRankNotFoundException e) {
            staff.whisper("Rank " + rankNum + " doesn't exist in " + corp.getName() + ".",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String rankTitle = corp.getRank(rankNum)
                .map(CorporationRank::getTitle)
                .orElse("rank " + rankNum);
        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " hires " + resolved.username + " as " + rankTitle
                + " at " + corp.getName() + "*");
        if (resolved.isOnline()) {
            resolved.online.whisper("You've been hired as " + rankTitle
                            + " at " + corp.getName() + ".",
                    RoomChatMessageBubbles.WIRED);
        }
        return true;
    }
}
