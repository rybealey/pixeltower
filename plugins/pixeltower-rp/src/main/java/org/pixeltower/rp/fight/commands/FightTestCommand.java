package org.pixeltower.rp.fight.commands;

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
 * {@code :fighttest <user|x> <damage>} — staff-only exerciser for the
 * damage pipeline. Drives {@link StatsManager#adjustHp} end-to-end so
 * the DeathState / rp_downed_players / broadcast paths can be validated
 * before the real {@code :hit} command (P2) + the fight engagement
 * registry are wired.
 *
 * Gated by {@code rp.admin.min_rank}, matching the rest of the staff
 * tools ({@code :kill}, {@code :restore}, {@code :award}).
 */
public class FightTestCommand extends Command {

    public FightTestCommand() {
        super(null, new String[] {"fighttest"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        if (staff.getHabboInfo().getRank().getId() < minRank) {
            staff.whisper("You don't have permission to run :fighttest.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 3) {
            staff.whisper("Usage: :fighttest <user|x> <damage>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        int damage;
        try {
            damage = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            staff.whisper("Damage must be an integer.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (damage <= 0) {
            staff.whisper("Damage must be positive.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!StatsManager.adjustHp(resolved.habboId, -damage)) {
            staff.whisper(resolved.username + " doesn't have a stats row yet.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.isOnline()) {
            StatsManager.get(resolved.habboId).ifPresent(stats ->
                    resolved.online.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
        }

        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " hits " + resolved.username + " for " + damage + " damage (test)*");
        return true;
    }
}
