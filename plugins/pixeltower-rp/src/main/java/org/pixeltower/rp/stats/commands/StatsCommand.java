package org.pixeltower.rp.stats.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;

/**
 * {@code :stats} — whisper the caller their own stat sheet across three
 * WIRED bubbles (HP/energy, level/XP/skill points, and the three skill
 * values). Self-only in Tier 1; cross-user lookup is a later feature.
 */
public class StatsCommand extends Command {

    public StatsCommand() {
        super(null, new String[] {"stats"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        PlayerStats stats = StatsManager.get(habbo.getHabboInfo().getId()).orElse(null);
        if (stats == null) {
            habbo.whisper("Your stats aren't loaded yet — try again in a moment.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        habbo.whisper("HP: " + stats.getHp() + "/" + stats.getMaxHp()
                        + "  —  Energy: " + stats.getEnergy() + "/" + stats.getMaxEnergy(),
                RoomChatMessageBubbles.WIRED);
        habbo.whisper("Level " + stats.getLevel()
                        + "  —  " + stats.getXp() + " XP"
                        + "  —  " + stats.getSkillPointsUnspent() + " skill points",
                RoomChatMessageBubbles.WIRED);
        habbo.whisper("Skills: Hit " + stats.getSkillHit()
                        + ", Endurance " + stats.getSkillEndurance()
                        + ", Stamina " + stats.getSkillStamina(),
                RoomChatMessageBubbles.WIRED);
        return true;
    }
}
