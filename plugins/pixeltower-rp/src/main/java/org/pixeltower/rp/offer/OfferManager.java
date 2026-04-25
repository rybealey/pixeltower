package org.pixeltower.rp.offer;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.offer.outgoing.RoleplayOfferDismissedComposer;
import org.pixeltower.rp.offer.outgoing.RoleplayOfferReceivedComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks roleplay service offers awaiting target response. One offer per
 * target at a time — a new offer for the same target replaces and dismisses
 * the previous so the popup state on screen is always unambiguous.
 *
 * Lifecycle:
 *   1. {@link #createOffer} stores a {@link PendingOffer}, pushes a
 *      {@link RoleplayOfferReceivedComposer} to the target, schedules a
 *      15s expiry via the Arcturus threading service.
 *   2. {@link #consume} is called from the offer-response handler. If
 *      the offer is still pending and the responder matches the target,
 *      it's removed and returned for the caller to apply / discard.
 *   3. The scheduled task runs after 15s; if the offer is still in the
 *      map (i.e. wasn't consumed), it's removed, a dismiss composer is
 *      pushed to the target, and the offerer is whispered.
 */
public final class OfferManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfferManager.class);

    public static final long OFFER_TTL_MS = 15_000L;

    private static final AtomicInteger ID_SEQ = new AtomicInteger(1);
    private static final ConcurrentMap<Integer, PendingOffer> BY_ID = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, Integer> ACTIVE_BY_TARGET = new ConcurrentHashMap<>();

    private OfferManager() {}

    /**
     * Registers a new offer, pushes the popup to the target, and schedules
     * expiry. Any pre-existing offer for the same target is dismissed first.
     * Returns the freshly-issued offer id.
     */
    public static int createOffer(OfferService service, Habbo offerer, int targetId,
                                  String targetUsername) {
        // Pre-empt any open offer to the same target.
        Integer prevId = ACTIVE_BY_TARGET.remove(targetId);
        if (prevId != null) {
            PendingOffer prev = BY_ID.remove(prevId);
            if (prev != null) {
                pushDismiss(prev);
                whisperOfferer(prev,
                        "Your previous offer to " + targetUsername + " was replaced.");
            }
        }

        int id = ID_SEQ.getAndIncrement();
        long expiresAtMs = System.currentTimeMillis() + OFFER_TTL_MS;
        PendingOffer offer = new PendingOffer(
                id,
                service.key(),
                offerer.getHabboInfo().getId(),
                offerer.getHabboInfo().getUsername(),
                targetId,
                expiresAtMs);
        BY_ID.put(id, offer);
        ACTIVE_BY_TARGET.put(targetId, id);

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetId);
        if (target != null && target.getClient() != null) {
            target.getClient().sendResponse(new RoleplayOfferReceivedComposer(
                    id,
                    service.key(),
                    offer.offererUsername,
                    service.title(),
                    service.description(),
                    service.iconResource(),
                    service.price(),
                    OFFER_TTL_MS / 1000L));
        }

        Emulator.getThreading().run(() -> expire(id), OFFER_TTL_MS);
        LOGGER.info("offer.create id={} service={} offerer={} target={}",
                id, service.key(), offer.offererId, targetId);
        return id;
    }

    /**
     * Atomically removes the offer matching {@code offerId} iff its target
     * is {@code responderId}. Returns the consumed offer; empty if not found
     * or responder didn't match.
     */
    public static Optional<PendingOffer> consume(int offerId, int responderId) {
        PendingOffer offer = BY_ID.get(offerId);
        if (offer == null) return Optional.empty();
        if (offer.targetId != responderId) return Optional.empty();
        if (!BY_ID.remove(offerId, offer)) return Optional.empty();
        ACTIVE_BY_TARGET.remove(offer.targetId, offer.id);
        return Optional.of(offer);
    }

    /**
     * Background expiry task. Idempotent: if the offer was already
     * consumed by accept/reject, this is a no-op.
     */
    private static void expire(int offerId) {
        PendingOffer offer = BY_ID.remove(offerId);
        if (offer == null) return;
        ACTIVE_BY_TARGET.remove(offer.targetId, offer.id);
        pushDismiss(offer);
        whisperOfferer(offer,
                "Your offer to " + lookupUsername(offer.targetId) + " expired.");
        LOGGER.info("offer.expire id={} service={} target={}",
                offer.id, offer.serviceKey, offer.targetId);
    }

    private static void pushDismiss(PendingOffer offer) {
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(offer.targetId);
        if (target != null && target.getClient() != null) {
            target.getClient().sendResponse(new RoleplayOfferDismissedComposer(offer.id));
        }
    }

    private static void whisperOfferer(PendingOffer offer, String message) {
        Habbo offerer = Emulator.getGameEnvironment().getHabboManager().getHabbo(offer.offererId);
        if (offerer != null) {
            offerer.whisper(message, RoomChatMessageBubbles.ALERT);
        }
    }

    private static String lookupUsername(int habboId) {
        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
        if (online != null) return online.getHabboInfo().getUsername();
        return "(target)";
    }
}
