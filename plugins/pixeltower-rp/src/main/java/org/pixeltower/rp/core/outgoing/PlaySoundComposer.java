package org.pixeltower.rp.core.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Tells the Pixeltower-patched Nitro client to play a named sound sample.
 * Paired with {@code nitro-patches/pixeltower-play-sound.patch}, which
 * registers header {@link #HEADER_ID} and dispatches
 * {@code NitroSoundEvent.PLAY_SOUND} with the sample name on receipt.
 *
 * Used to fire the "cha-ching" sound on bank-balance-only mutations
 * (admin bank awards, bank-to-bank transfers) — paths that don't touch
 * {@code users.credits} and therefore don't trigger Nitro's built-in
 * credits-change sound.
 */
public class PlaySoundComposer extends MessageComposer {

    public static final int HEADER_ID = 6500;

    public static final String SAMPLE_CREDITS = "credits";

    private final String sample;

    public PlaySoundComposer(String sample) {
        this.sample = sample;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendString(this.sample);
        return this.response;
    }
}
