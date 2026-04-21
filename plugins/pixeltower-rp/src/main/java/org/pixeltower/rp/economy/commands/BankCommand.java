package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankManager;

import java.util.Optional;

/**
 * {@code :bank} — public RP emote announcing the caller's bank balance
 * to the room. Mirror of {@link BalanceCommand} but for bank funds.
 *
 * {@code :bank hide} — public emote drops the amount; private whisper
 * carries the actual number.
 */
public class BankCommand extends Command {

    public BankCommand() {
        super(null, new String[] {"bank"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        Optional<Long> balance = BankManager.getBalance(habbo.getHabboInfo().getId());
        if (balance.isEmpty()) {
            habbo.whisper("You don't have a bank account. Use :openaccount first.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        long bal = balance.get();
        boolean hide = params.length >= 2 && "hide".equalsIgnoreCase(params[1]);

        if (hide) {
            RpChat.emote(habbo, "*checks their bank balance*");
            habbo.whisper("Bank balance: $" + bal, RoomChatMessageBubbles.ALERT);
            return true;
        }
        RpChat.emote(habbo, "*checks their bank balance, finding that they have $" + bal + "*");
        return true;
    }
}
