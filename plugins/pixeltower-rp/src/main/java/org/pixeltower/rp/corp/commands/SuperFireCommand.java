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
import org.pixeltower.rp.corp.exceptions.NotInCorporationException;

/**
 * {@code :superfire <user|x>} — staff force-remove a habbo from whatever
 * corp they're in. Bypasses the caller-rank gate that {@link FireCommand}
 * enforces (since the actor is staff, not a corp member). Stops any
 * active shift as a side effect.
 *
 * Gated by {@code rp.admin.min_rank}.
 */
public class SuperFireCommand extends Command {

    public SuperFireCommand() {
        super(null, new String[] {"superfire"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        if (!StaffGate.isStaff(staff)) {
            staff.whisper("You don't have permission to run :superfire.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 2) {
            staff.whisper("Usage: :superfire <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Capture corp name BEFORE firing (cache entry gets removed).
        String corpName = CorporationManager.getMembership(resolved.habboId)
                .flatMap(m -> CorporationManager.getById(m.getCorpId()))
                .map(Corporation::getName)
                .orElse("the corporation");

        try {
            CorporationManager.superFire(resolved.habboId);
        } catch (NotInCorporationException e) {
            staff.whisper(resolved.username + " isn't in a corporation.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " fires " + resolved.username + " from " + corpName + "*");
        if (resolved.isOnline()) {
            resolved.online.whisper("You've been fired from " + corpName + ".",
                    RoomChatMessageBubbles.WIRED);
        }
        return true;
    }
}
