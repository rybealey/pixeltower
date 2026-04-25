package org.pixeltower.rp.offer.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

/**
 * {@code :heal <user|x>} — hospital-corp shorthand for
 * {@code :offer <user|x> heal}. Synthesizes the service argument and
 * hands off to {@link OfferCommand} so the validation and pending-offer
 * flow stay single-sourced.
 */
public class HealCommand extends Command {

    private final OfferCommand delegate = new OfferCommand();

    public HealCommand() {
        super(null, new String[] {"heal"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (params.length < 2) {
            gameClient.getHabbo().whisper("Usage: :heal <user|x>",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        String[] offerParams = new String[] { params[0], params[1], "heal" };
        return delegate.handle(gameClient, offerParams);
    }
}
