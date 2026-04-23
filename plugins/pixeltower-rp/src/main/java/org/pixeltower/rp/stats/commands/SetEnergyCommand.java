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
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

/**
 * {@code :setenergy <user|x> <value>} — staff absolute-set of energy.
 * Goes through {@link StatsManager#adjustEnergy}, which clamps to
 * {@code [0, max_energy]} and write-throughs to cache + DB. No death
 * side-effects — energy has no game-state threshold the way HP does.
 *
 * Gated by {@code rp.admin.min_rank} (default 5).
 */
public class SetEnergyCommand extends Command {

    public SetEnergyCommand() {
        super(null, new String[] {"setenergy"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        if (staff.getHabboInfo().getRank().getId() < minRank) {
            staff.whisper("You don't have permission to run :setenergy.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 3) {
            staff.whisper("Usage: :setenergy <user|x> <value>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        int requested;
        try {
            requested = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            staff.whisper("Value must be an integer.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (requested < 0) {
            staff.whisper("Value must be >= 0.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        PlayerStats current = StatsManager.getOrFetch(resolved.habboId).orElse(null);
        if (current == null) {
            staff.whisper(resolved.username + " doesn't have a stats row yet.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int applied = Math.min(requested, current.getMaxEnergy());
        int delta = applied - current.getEnergy();
        if (delta != 0) {
            StatsManager.adjustEnergy(resolved.habboId, delta);
        }

        if (resolved.isOnline()) {
            StatsManager.get(resolved.habboId).ifPresent(stats ->
                    resolved.online.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
        }

        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " sets " + resolved.username + "'s energy to " + applied + "*");
        return true;
    }
}
