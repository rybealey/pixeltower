package org.pixeltower.rp.core.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import org.pixeltower.rp.core.outgoing.OpenClientLinkComposer;

/**
 * {@code :wd} / {@code :wardrobe} — open the Change Your Look view for
 * the caller. Same client surface a dressing-room rp_functional tile
 * triggers; this is the chat-driven escape hatch for rooms without one.
 */
public class WardrobeCommand extends Command {

    public WardrobeCommand() {
        super(null, new String[] {"wd", "wardrobe"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        gameClient.sendResponse(new OpenClientLinkComposer("avatar-editor/show"));
        return true;
    }
}
