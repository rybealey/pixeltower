package org.pixeltower.rp.fight.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.fight.FightRange;
import org.pixeltower.rp.fight.RoomFlags;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

import java.util.Optional;

/**
 * {@code :slap <user|x>} — a single open-handed slap. Always renders a
 * yellow public action emote; in a non-safe-zone room, additionally
 * deducts 1 HP from the target. Deliberately does NOT go through
 * {@link org.pixeltower.rp.fight.FightService#hit} — slap has no
 * cooldown, no energy cost, no corp-fratricide gate, and ignores the
 * fight system's range / stats-not-ready denials. It only borrows the
 * room's safe-zone flag from {@link RoomFlags} to decide whether the
 * slap "stings".
 *
 * Damage path is gated on the target still being alive (HP &gt; 0):
 * slapping a downed player is a no-op for HP, and the emote falls back
 * to the plain form. {@link StatsManager#adjustHp} clamps and triggers
 * the existing knockout-on-zero side effects, so a 1-HP target slapped
 * in an unsafe room still goes down correctly.
 */
public class SlapCommand extends Command {

    public SlapCommand() {
        super(null, new String[] {"slap"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();

        if (params.length < 2) {
            caller.whisper("Usage: :slap <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!resolved.isOnline()) {
            caller.whisper(resolved.username + " isn't online.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = resolved.online;

        if (target.getHabboInfo().getId() == caller.getHabboInfo().getId()) {
            caller.whisper("You can't slap yourself.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!FightRange.withinRange(caller, target, 1)) {
            caller.whisper(resolved.username
                            + " is too far away, try getting closer to them.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Room room = caller.getHabboInfo().getCurrentRoom();
        boolean unsafeZone = room != null && !RoomFlags.get(room.getId()).noPvp();

        boolean damaged = false;
        if (unsafeZone) {
            int targetId = target.getHabboInfo().getId();
            Optional<PlayerStats> stats = StatsManager.get(targetId);
            if (stats.isPresent() && stats.get().getHp() > 0
                    && StatsManager.adjustHp(targetId, -1)) {
                damaged = true;
                target.getClient().sendResponse(
                        new UpdatePlayerStatsComposer(stats.get()));
            }
        }

        String emote = damaged
                ? "*slaps " + resolved.username + " across the face, causing 1 damage*"
                : "*slaps " + resolved.username + " across the face*";
        RpChat.emote(caller, emote);
        return true;
    }
}
