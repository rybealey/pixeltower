package org.pixeltower.rp.core.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.core.TargetService;
import org.pixeltower.rp.core.TargetTracker;

import java.util.Optional;

/**
 * Manual override for the click-based target cache.
 *
 * Aliases:
 *   {@code :target}            — show current target
 *   {@code :target <user>}     — set target to that user (online or offline)
 *   {@code :t}                 — alias for :target
 *   {@code :t <user>}          — alias
 *   {@code :untarget}          — clear current target
 *
 * Click-based targeting (selecting another user's avatar) is the primary
 * path; the Nitro InfoStand widget emits the pixeltower "set target"
 * packet on every sprite click, handled server-side by
 * {@code PixeltowerRP.onUserTargetSelected}. This command covers the
 * out-of-room cases: targeting someone who isn't currently in the room,
 * or setting an offline user as target.
 */
public class TargetCommand extends Command {

    public TargetCommand() {
        super(null, new String[] {"target", "t", "untarget"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        String alias = params.length > 0 ? params[0].toLowerCase() : "target";

        if ("untarget".equals(alias)) {
            TargetTracker.clear(caller.getHabboInfo().getId());
            caller.whisper("Target cleared.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        // :target / :t
        if (params.length < 2) {
            Optional<Integer> currentId = TargetTracker.get(caller.getHabboInfo().getId());
            if (currentId.isEmpty()) {
                caller.whisper("No target set. Click a user or use :target <name>.",
                        RoomChatMessageBubbles.ALERT);
                return true;
            }
            // Resolve the cached id for display. If the target was deleted,
            // we fall through to an informative message and clear the stale entry.
            try {
                ResolvedTarget current = TargetResolver.resolve(caller, "x");
                caller.whisper("Currently targeting " + current.username + ".",
                        RoomChatMessageBubbles.ALERT);
            } catch (Exception e) {
                TargetTracker.clear(caller.getHabboInfo().getId());
                caller.whisper("Your target no longer exists; cleared.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        }

        String name = params[1];
        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, name);
        } catch (NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        } catch (Exception e) {
            caller.whisper("Couldn't set target: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.habboId == caller.getHabboInfo().getId()) {
            caller.whisper("You can't target yourself.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        TargetService.setAndPush(caller, resolved.habboId);
        caller.whisper("Now targeting " + resolved.username + ".", RoomChatMessageBubbles.ALERT);
        return true;
    }
}
