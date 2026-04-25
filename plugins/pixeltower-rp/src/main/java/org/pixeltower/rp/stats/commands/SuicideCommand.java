package org.pixeltower.rp.stats.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

import java.util.Optional;

/**
 * {@code :suicide} — instant self-KO. Drops the caller's HP to zero
 * through {@link StatsManager#killPlayer(int)}, which fires the standard
 * death side effects (DeathState lay+freeze, rp_downed_players row,
 * respawn timer). No-ops if the caller is already dead.
 */
public class SuicideCommand extends Command {

    public SuicideCommand() {
        super(null, new String[] {"suicide"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        int habboId = caller.getHabboInfo().getId();

        Optional<PlayerStats> stats = StatsManager.get(habboId);
        if (stats.isEmpty() || stats.get().getHp() <= 0) {
            caller.whisper("You are already dead.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!StatsManager.killPlayer(habboId)) {
            caller.whisper("You don't have a stats row yet.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        StatsManager.get(habboId).ifPresent(updated ->
                gameClient.sendResponse(new UpdatePlayerStatsComposer(updated)));

        RpChat.emote(caller,
                "*has taken a cyanide capsule, taking their life slowly and painfully*");
        return true;
    }
}
