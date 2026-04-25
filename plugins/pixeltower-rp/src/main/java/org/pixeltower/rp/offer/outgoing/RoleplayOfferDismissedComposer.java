package org.pixeltower.rp.offer.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Tells the client to drop the roleplay offer popup matching {@code offerId}.
 * Sent on server-side expiry (15s) and on offer replacement when the same
 * target gets a fresh offer mid-window.
 *
 * Wire format: int offerId.
 */
public class RoleplayOfferDismissedComposer extends MessageComposer {

    public static final int HEADER_ID = 6511;

    private final int offerId;

    public RoleplayOfferDismissedComposer(int offerId) {
        this.offerId = offerId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.offerId);
        return this.response;
    }
}
