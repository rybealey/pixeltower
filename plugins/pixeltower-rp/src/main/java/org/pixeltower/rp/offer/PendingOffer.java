package org.pixeltower.rp.offer;

/**
 * One in-flight roleplay service offer awaiting the target's accept,
 * reject, or 15-second timeout. Held by {@link OfferManager} keyed on
 * {@link #id}; the offerer's username is captured at create time so we
 * can render the popup even if the offerer logs off mid-window.
 */
public final class PendingOffer {

    public final int id;
    public final String serviceKey;
    public final int offererId;
    public final String offererUsername;
    public final int targetId;
    public final long expiresAtMs;

    public PendingOffer(int id, String serviceKey, int offererId, String offererUsername,
                        int targetId, long expiresAtMs) {
        this.id = id;
        this.serviceKey = serviceKey;
        this.offererId = offererId;
        this.offererUsername = offererUsername;
        this.targetId = targetId;
        this.expiresAtMs = expiresAtMs;
    }
}
