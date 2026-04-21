package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;

/**
 * {@code :withdraw <amount>} — move bank → cash. No fee in v1 (only
 * deposits are fee-bearing). Location-gated to ATM rooms (shared
 * {@link AtmGate}).
 *
 * {@code :withdraw <amount> hide} — omit the amount from the public emote;
 * private whisper confirms the real number.
 */
public class WithdrawCommand extends Command {

    public WithdrawCommand() {
        super(null, new String[] {"withdraw"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        if (params.length < 2) {
            habbo.whisper("Usage: :withdraw <amount> [hide]", RoomChatMessageBubbles.ALERT);
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(params[1]);
        } catch (NumberFormatException e) {
            habbo.whisper("Amount must be a whole number.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (amount <= 0) {
            habbo.whisper("Amount must be greater than zero.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        boolean hide = params.length >= 3 && "hide".equalsIgnoreCase(params[2]);

        if (!AtmGate.inAtmRoom(habbo)) {
            habbo.whisper("You need to be at an ATM to withdraw.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            BankManager.withdraw(habbo, amount, "bank_withdraw");
        } catch (InsufficientFundsException e) {
            habbo.whisper("Not enough in your bank account.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            habbo.whisper("You don't have a bank account. Use :openaccount first.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            habbo.whisper("Invalid withdrawal: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (hide) {
            RpChat.emote(habbo, "*withdraws money from their bank account*");
            habbo.whisper("Withdrew $" + amount + ".", RoomChatMessageBubbles.ALERT);
        } else {
            RpChat.emote(habbo, "*withdraws $" + amount + " from their bank account*");
        }
        return true;
    }
}
