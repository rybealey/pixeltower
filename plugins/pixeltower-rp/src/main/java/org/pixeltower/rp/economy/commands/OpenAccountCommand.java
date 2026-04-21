package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankManager;

/**
 * {@code :openaccount} — creates the caller's {@code rp_player_bank} row
 * if it doesn't already exist. Free (no opening fee in v1). No location
 * gating — you can open remotely; only deposits/withdrawals require
 * being at an ATM room.
 */
public class OpenAccountCommand extends Command {

    public OpenAccountCommand() {
        super(null, new String[] {"openaccount"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        boolean opened = BankManager.openAccount(habbo.getHabboInfo().getId());
        if (opened) {
            RpChat.emote(habbo, "*opens a new bank account*");
            habbo.whisper("Your bank account is now open. Use :deposit, :withdraw, :transfer.",
                    RoomChatMessageBubbles.WIRED);
        } else {
            habbo.whisper("You already have a bank account.", RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
