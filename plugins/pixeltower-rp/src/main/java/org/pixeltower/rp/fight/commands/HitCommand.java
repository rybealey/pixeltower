package org.pixeltower.rp.fight.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.fight.FightService;
import org.pixeltower.rp.fight.FightService.HitResult;

/**
 * {@code :hit <user|x>} — swing at a target in range. All preconditions
 * (range, safe zone, alive, energy, cooldown, corp fratricide) are
 * enforced by {@link FightService#hit}; this command is a thin wrapper
 * that resolves the target and relays the deny reason (if any) as an
 * ALERT whisper, or shouts a damage emote on a successful hit.
 */
public class HitCommand extends Command {

    public HitCommand() {
        super(null, new String[] {"hit"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo attacker = gameClient.getHabbo();

        if (params.length < 2) {
            attacker.whisper("Usage: :hit <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(attacker, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            attacker.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!resolved.isOnline()) {
            attacker.whisper(resolved.username + " isn't online.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        HitResult result = FightService.hit(attacker, resolved.online);
        if (result.denied()) {
            attacker.whisper(result.denyReason(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Client patch prepends the speaker's username to asterisk-wrapped
        // shouts; viewers render as "<attacker> hits <target>, causing N damage"
        // (or the KO variant on the final blow). Red ALERT bubble on the KO
        // emote signals the mechanical outcome distinct from routine hits.
        if (result.knockout()) {
            RpChat.emote(attacker,
                    "*lands the final blow on " + resolved.username
                            + ", knocking them out*",
                    RoomChatMessageBubbles.ALERT);
        } else {
            RpChat.emote(attacker,
                    "*swings at " + resolved.username
                            + ", causing " + result.damage() + " damage*");
        }
        return true;
    }
}
