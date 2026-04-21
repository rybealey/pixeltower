package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * {@code :balance} / {@code :bal} — public RP emote that announces the
 * caller's balance to everyone in the room:
 *   <code>*checks their balance, finding that they have $X*</code>
 *
 * {@code :balance hide} / {@code :bal hide} — same beat but keeps the amount
 * private: public chat is just {@code *checks their balance*} and the caller
 * gets an ephemeral whisper with the actual number.
 *
 * Outside a room (e.g. called from the hotel view), the public emote is not
 * possible; we fall back to a whisper so the player still sees the output.
 *
 * Permission is {@code null} so every rank can run this without touching the
 * Arcturus {@code permissions} table (see {@code CommandHandler.handleCommand}
 * permission bypass).
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
        boolean inRoom = habbo.getRoomUnit() != null && habbo.getRoomUnit().isInRoom();

        if (hide) {
            if (inRoom) {
                habbo.talk("*checks their balance*", RoomChatMessageBubbles.NORMAL);
            }
            habbo.whisper("Balance: $" + credits, RoomChatMessageBubbles.ALERT);
            return true;
        }

        String emote = "*checks their balance, finding that they have $" + credits + "*";
        if (inRoom) {
            habbo.talk(emote, RoomChatMessageBubbles.NORMAL);
        } else {
            habbo.whisper(emote, RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
