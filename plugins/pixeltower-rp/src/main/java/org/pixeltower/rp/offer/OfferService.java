package org.pixeltower.rp.offer;

import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;

import java.util.Optional;

/**
 * One pluggable roleplay service offered to other players via
 * {@code :offer <user|x> <key>}. Each implementation declares the corp
 * that may offer it, its price in coins, the popup payload shown to the
 * target, and the effect to apply when the target accepts.
 *
 * Validation is split: {@link #validateOfferer} runs before the popup
 * is even constructed (so the offerer gets a clean error whisper),
 * while {@link #validateTarget} runs at command time AND again when the
 * target accepts — the second check guards against state changes during
 * the 15-second offer window (e.g. target healed by someone else).
 */
public interface OfferService {

    /** Stable lowercase key matched against the {@code :offer x <key>} arg. */
    String key();

    /** Hospital corp id, mechanic corp id, etc. — the corp whose members may offer this. */
    int corpId();

    /** Service title shown in the popup header (e.g. "Healing Service"). */
    String title();

    /** Body line shown below the title (e.g. "Restore your HP to full"). */
    String description();

    /**
     * Image resource passed to the Nitro popup (e.g. a sprite name or icon
     * URL). Empty string is acceptable; the client falls back to a default.
     */
    String iconResource();

    /** Price in coins (wallet credits). Read from a {@code rp.offer.<key>.price} config key. */
    long price();

    /**
     * Returns an error message if {@code caller} can't offer this service
     * (e.g. not in the corp), or empty if they may.
     */
    Optional<String> validateOfferer(Habbo caller);

    /**
     * Returns an error message if the target is not eligible right now
     * (e.g. already at full HP, KO'd, in a different room), or empty if they are.
     */
    Optional<String> validateTarget(Habbo caller, ResolvedTarget target);

    /**
     * Applies the service effect after the target accepts. Should also
     * charge the price and route the funds to the corp treasury. Throws
     * to signal mid-flight failures (insufficient funds, target state
     * changed, etc.); the caller will whisper the message to both parties.
     *
     * The target is guaranteed online at this point — the offer-response
     * packet came from their session — so {@link Habbo} is appropriate.
     */
    void apply(int offererId, Habbo target) throws OfferApplyException;
}
