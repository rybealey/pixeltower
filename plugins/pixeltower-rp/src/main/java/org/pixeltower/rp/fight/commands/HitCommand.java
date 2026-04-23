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
import org.pixeltower.rp.fight.FightRules;
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
            // Out-of-range is narrative: render a public miss emote instead
            // of a private whisper so bystanders see the telegraphed swing.
            if (result.denyReason() == FightRules.Deny.Reason.OUT_OF_RANGE) {
                RpChat.emote(attacker,
                        "*takes a swing at " + resolved.username
                                + ", but misses the mark*");
            } else {
                attacker.whisper(result.denyMessage(), RoomChatMessageBubbles.ALERT);
            }
            return true;
        }

        // Client patch prepends the speaker's username to asterisk-wrapped
        // shouts; viewers render as "<attacker> swings at <target>, causing
        // N damage" (or the KO variant on the final blow). RED bubble on
        // the KO emote signals the mechanical outcome distinct from routine
        // hits — ALERT is Arcturus bubble-style 1 (tan/system-alert); RED
        // is bubble-style 3 which renders as the actual red combat bubble.
        if (result.knockout()) {
            RpChat.emote(attacker,
                    "*lands the final blow on " + resolved.username
                            + ", knocking them out*",
                    RoomChatMessageBubbles.RED);
        } else {
            RpChat.emote(attacker,
                    "*swings at " + resolved.username
                            + ", causing " + result.damage() + " damage*");
        }
        return true;
    }
}
