package org.pixeltower.rp.offer;

/**
 * Thrown by {@link OfferService#apply} when the service effect can't
 * be applied at accept time — e.g. target's wallet no longer covers the
 * price, target's HP changed, target left the room. The message is
 * whispered to both parties and the offer is cleared.
 */
public class OfferApplyException extends Exception {
    public OfferApplyException(String message) {
        super(message);
    }
}
