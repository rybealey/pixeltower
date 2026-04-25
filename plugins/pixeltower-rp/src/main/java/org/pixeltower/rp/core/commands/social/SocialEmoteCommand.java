package org.pixeltower.rp.core.commands.social;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.fight.FightRange;

/**
 * Shared scaffolding for the affectionate social commands ({@code :hug},
 * {@code :kiss}, {@code :holdhands}, {@code :bite}). All four commands
 * resolve a target by name (or {@code x}), gate on adjacency / online /
 * self-target, apply a transient avatar effect to both avatars for seven
 * seconds, and shout an asterisk-wrapped action emote in the yellow
 * bubble — which the action-emote Nitro patch renders as
 * {@code *<caller> <verb> <target>*} for everyone in the room.
 *
 * Subclasses supply only the four pieces that vary: command alias,
 * effect id, an emote template, and an infinitive verb-phrase used in
 * the self-block whisper.
 */
abstract class SocialEmoteCommand extends Command {

    private static final int DURATION_SECONDS = 7;

    private final String alias;
    private final int effectId;
    /** Asterisk-less template; {@code %s} is the target username. e.g. {@code "gives %s a warm hug"}. */
    private final String emoteTemplate;
    /** Infinitive form used in the self-target whisper. e.g. {@code "hug"}, {@code "hold hands with"}. */
    private final String selfBlockVerb;

    protected SocialEmoteCommand(String alias, int effectId,
                                 String emoteTemplate, String selfBlockVerb) {
        super(null, new String[] { alias });
        this.alias = alias;
        this.effectId = effectId;
        this.emoteTemplate = emoteTemplate;
        this.selfBlockVerb = selfBlockVerb;
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();

        if (params.length < 2) {
            caller.whisper("Usage: :" + alias + " <user|x>",
                    RoomChatMessageBubbles.ALERT);
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
            caller.whisper("You can't " + selfBlockVerb + " yourself.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Same-room AND chebyshev <= 1 (adjacent or same tile). FightRange
        // returns false if either habbo isn't placed in a room.
        if (!FightRange.withinRange(caller, target, 1)) {
            caller.whisper(resolved.username
                            + " is too far away, try getting closer to them.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        applyEffect(caller.getRoomUnit());
        applyEffect(target.getRoomUnit());

        RpChat.emote(caller, "*" + emoteTemplate.formatted(resolved.username) + "*");
        return true;
    }

    private void applyEffect(RoomUnit unit) {
        // Arcturus 3.0.0 API: RoomUnit#setEffectId(effectId, durationSeconds)
        // stores the effect + auto-expiry timestamp on the unit, but doesn't
        // broadcast — viewers won't see the effect until a RoomUserEffectComposer
        // is sent to the room. The unit auto-clears server-side after the
        // duration elapses, so no manual cleanup is needed.
        unit.setEffectId(this.effectId, DURATION_SECONDS);
        Room room = unit.getRoom();
        if (room != null) {
            room.sendComposer(new RoomUserEffectComposer(unit).compose());
        }
    }
}
