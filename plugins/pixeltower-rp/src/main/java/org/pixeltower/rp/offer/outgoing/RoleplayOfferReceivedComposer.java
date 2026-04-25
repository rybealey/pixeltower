package org.pixeltower.rp.offer.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Pushes a roleplay service offer popup to the target's client. Header
 * {@link #HEADER_ID} is wired in {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts}
 * and routed to {@code components/pixeltower/OfferHUD.tsx} which renders
 * the slide-in popup beneath the player HUD.
 *
 * Wire format: int offerId, string serviceKey, string offererName,
 * string title, string description, string iconResource, int price,
 * int expirySeconds.
 */
public class RoleplayOfferReceivedComposer extends MessageComposer {

    public static final int HEADER_ID = 6510;

    private final int offerId;
    private final String serviceKey;
    private final String offererName;
    private final String title;
    private final String description;
    private final String iconResource;
    private final long price;
    private final long expirySeconds;

    public RoleplayOfferReceivedComposer(int offerId, String serviceKey, String offererName,
                                         String title, String description, String iconResource,
                                         long price, long expirySeconds) {
        this.offerId = offerId;
        this.serviceKey = serviceKey;
        this.offererName = offererName;
        this.title = title;
        this.description = description;
        this.iconResource = iconResource;
        this.price = price;
        this.expirySeconds = expirySeconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.offerId);
        this.response.appendString(this.serviceKey);
        this.response.appendString(this.offererName);
        this.response.appendString(this.title);
        this.response.appendString(this.description);
        this.response.appendString(this.iconResource);
        this.response.appendInt((int) Math.min(this.price, Integer.MAX_VALUE));
        this.response.appendInt((int) Math.min(this.expirySeconds, Integer.MAX_VALUE));
        return this.response;
    }
}
