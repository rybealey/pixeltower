package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;

/**
 * {@code :balance} / {@code :bal} — public RP emote announcing the caller's
 * balance to everyone in the room:
 *   <code>*checks their balance, finding that they have $X*</code>
 *
 * {@code :balance hide} / {@code :bal hide} — same beat, amount kept private.
 * Public chat is just <code>*checks their balance*</code>; caller gets an
 * ephemeral whisper with the actual number.
 *
 * Public emote styling (bold blue shout) lives in
 * {@link org.pixeltower.rp.core.RpChat#emote}.
 *
 * Permission is {@code null} so the CommandHandler permission bypass applies.
 */
public class BalanceCommand extends Command {

    public BalanceCommand() {
        super(null, new String[] {"balance", "bal"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        int credits = habbo.getHabboInfo().getCredits();
        boolean hide = params.length >= 2 && "hide".equalsIgnoreCase(params[1]);

        if (hide) {
            RpChat.emote(habbo, "*checks their balance*");
            habbo.whisper("Balance: $" + credits, RoomChatMessageBubbles.ALERT);
            return true;
        }

        RpChat.emote(habbo, "*checks their balance, finding that they have $" + credits + "*");
        return true;
    }
}
