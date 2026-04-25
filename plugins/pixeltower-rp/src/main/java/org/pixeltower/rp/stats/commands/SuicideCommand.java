package org.pixeltower.rp.stats.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.SuicideService;

import java.util.Optional;

/**
 * {@code :suicide} — start a self-inflicted bleed-out. Player loses 2 HP
 * every second until they hit zero, at which point the standard death
 * pipeline takes over (DeathState lay+freeze, rp_downed_players row,
 * respawn timer).
 *
 * No-ops if the caller is already at 0 HP or already bleeding out.
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

        if (!SuicideService.start(habboId)) {
            caller.whisper("You're already bleeding out.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        RpChat.emote(caller,
                "*has taken a cyanide capsule, taking their life slowly and painfully*");
        return true;
    }
}
