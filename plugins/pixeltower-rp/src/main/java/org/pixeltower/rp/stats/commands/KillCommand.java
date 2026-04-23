package org.pixeltower.rp.stats.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

/**
 * {@code :kill <user|x>} — staff instant KO. Sets the target's HP to 0
 * (cache + DB via {@link StatsManager#killPlayer(int)}) and shouts a
 * flavor-text emote visible to everyone in the room.
 *
 * Gated by {@code rp.admin.min_rank}, matching {@link RestoreCommand}.
 */
public class KillCommand extends Command {

    public KillCommand() {
        super(null, new String[] {"kill"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        if (staff.getHabboInfo().getRank().getId() < minRank) {
            staff.whisper("You don't have permission to run :kill.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 2) {
            staff.whisper("Usage: :kill <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!resolved.isOnline()) {
            staff.whisper(resolved.username + " is offline.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!StatsManager.killPlayer(resolved.habboId)) {
            staff.whisper(resolved.username + " doesn't have a stats row yet.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.isOnline()) {
            StatsManager.get(resolved.habboId).ifPresent(stats ->
                    resolved.online.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
        }

        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " casts a spell, killing " + resolved.username + " with a lightning bolt*");
        return true;
    }
}
