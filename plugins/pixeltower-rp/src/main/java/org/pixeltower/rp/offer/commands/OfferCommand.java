package org.pixeltower.rp.offer.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.offer.OfferManager;
import org.pixeltower.rp.offer.OfferRegistry;
import org.pixeltower.rp.offer.OfferService;

import java.util.Optional;

/**
 * {@code :offer <user|x> <service>} — pushes a roleplay-service offer
 * popup to the target's client. Each service ({@code heal}, {@code repair},
 * …) defines its own corp gating, price, and effect via the {@link
 * OfferService} interface; this command is a generic dispatcher.
 *
 * Currently registered services: {@code heal} (hospital corp).
 */
public class OfferCommand extends Command {

    public OfferCommand() {
        super(null, new String[] {"offer"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo caller = gameClient.getHabbo();
        if (params.length < 3) {
            caller.whisper("Usage: :offer <user|x> <service>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        OfferService service = OfferRegistry.lookup(params[2]).orElse(null);
        if (service == null) {
            caller.whisper("Unknown service '" + params[2] + "'.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Optional<String> offererErr = service.validateOfferer(caller);
        if (offererErr.isPresent()) {
            caller.whisper(offererErr.get(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(caller, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            caller.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.habboId == caller.getHabboInfo().getId()) {
            caller.whisper("You can't offer a service to yourself.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Optional<String> targetErr = service.validateTarget(caller, resolved);
        if (targetErr.isPresent()) {
            caller.whisper(targetErr.get(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        OfferManager.createOffer(service, caller, resolved.habboId, resolved.username);
        caller.whisper("Offered " + service.key() + " to " + resolved.username
                        + " for " + service.price() + " coins.",
                RoomChatMessageBubbles.WIRED);
        return true;
    }
}
