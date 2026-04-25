package org.pixeltower.rp.corp.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

import java.util.Map;

/**
 * Per-room map of {@code habboId → corp badge code} used by the Pixeltower-
 * patched Nitro client to override the favorite-Habbo-Group badge slot in
 * the user infostand. The override is purely visual; the player's actual
 * favorite group on the server is unchanged.
 *
 * <p>Header {@link #HEADER_ID} is registered alongside the existing
 * Pixeltower custom packets in
 * {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts}. Sent on
 * room entry and after any hire/fire/quit that might change the map's
 * contents — see {@code CorpBadgeBroadcaster}.</p>
 *
 * <p>Wire format: {@code int count} followed by {@code count} pairs of
 * {@code (int habboId, string badgeCode)}. Empty maps are valid (signal
 * to the client to clear its room cache).</p>
 */
public class UpdateCorpBadgesComposer extends MessageComposer {

    public static final int HEADER_ID = 6504;

    private final Map<Integer, String> badgesByHabboId;

    public UpdateCorpBadgesComposer(Map<Integer, String> badgesByHabboId) {
        this.badgesByHabboId = badgesByHabboId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.badgesByHabboId.size());
        for (Map.Entry<Integer, String> entry : this.badgesByHabboId.entrySet()) {
            this.response.appendInt(entry.getKey());
            this.response.appendString(entry.getValue());
        }
        return this.response;
    }
}
