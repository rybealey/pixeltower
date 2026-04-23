package org.pixeltower.rp.core.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Tells the Pixeltower-patched Nitro client to fire {@code CreateLinkEvent(uri)}
 * for the supplied link. Generic primitive — any server-side code that needs
 * to nudge the client into opening a Nitro UI (avatar editor, navigator,
 * catalog page, etc.) can send this composer instead of inventing a new header.
 *
 * Header {@link #HEADER_ID} is registered in
 * {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts} and consumed by
 * {@code components/main/PixeltowerLinkListener.tsx}, which validates the URI
 * against an allow-list of known link prefixes before invoking
 * {@code CreateLinkEvent}.
 */
public class OpenClientLinkComposer extends MessageComposer {

    public static final int HEADER_ID = 6503;

    private final String uri;

    public OpenClientLinkComposer(String uri) {
        this.uri = uri;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendString(this.uri);
        return this.response;
    }
}
