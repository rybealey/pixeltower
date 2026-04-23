package org.pixeltower.rp.functional;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.outgoing.OpenClientLinkComposer;
import org.pixeltower.rp.core.outgoing.PlaySoundComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates a {@link FunctionalAction} row into the actual server→client
 * effect for a specific player. New action types only need a new switch
 * case; the wire format ({@link OpenClientLinkComposer}, header 6503) is
 * generic enough to cover any "open Nitro UI X" need.
 *
 * Sends are per-player by construction: we only call sendResponse on the
 * triggering Habbo's GameClient, never broadcast to the room.
 */
public final class FunctionalActionDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionalActionDispatcher.class);

    private FunctionalActionDispatcher() {}

    public static void dispatch(Habbo habbo, FunctionalAction action) {
        if (habbo == null || action == null) return;
        GameClient client = habbo.getClient();
        if (client == null) return;

        String payload = action.payload() == null ? "" : action.payload();
        switch (action.actionType()) {
            case "open_avatar_editor":
                client.sendResponse(new OpenClientLinkComposer(
                        "avatar-editor/" + (payload.isEmpty() ? "show" : payload)));
                break;
            case "open_navigator":
                client.sendResponse(new OpenClientLinkComposer(
                        "navigator/" + (payload.isEmpty() ? "show" : payload)));
                break;
            case "open_catalog":
                client.sendResponse(new OpenClientLinkComposer(
                        "catalog/open/" + payload));
                break;
            case "play_sound":
                client.sendResponse(new PlaySoundComposer(payload));
                break;
            default:
                LOGGER.warn("Unknown rp_functional action_type '{}' for items_base.id={}",
                        action.actionType(), action.itemBaseId());
        }
    }
}
