package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;

/**
 * {@code :deposit <amount>} — move coins to bank. A fee (1% of gross,
 * minimum $2) is routed to the Bank corp treasury (see
 * {@code rp.bank.fee_rate} / {@code rp.bank.fee_min} /
 * {@code rp.bank.fee_corp_key}).
 *
 * {@code :deposit <amount> hide} — omit the number from the public emote;
 * private whisper carries the actual amounts.
 *
 * Location gate: caller must be in one of the rooms listed in
 * {@code rp.bank.atm_room_ids} (comma-separated). Empty list = no gate
 * (fallback while ATM rooms aren't configured yet).
 */
public class DepositCommand extends Command {

    public DepositCommand() {
        super(null, new String[] {"deposit"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        if (params.length < 2) {
            habbo.whisper("Usage: :deposit <amount> [hide]", RoomChatMessageBubbles.ALERT);
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
            habbo.whisper("You need to be at an ATM to deposit.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        long net;
        try {
            net = BankManager.deposit(habbo, amount, "bank_deposit");
        } catch (InsufficientFundsException e) {
            habbo.whisper("Not enough coins. Balance: $" + habbo.getHabboInfo().getCredits(),
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            habbo.whisper("You don't have a bank account. Use :openaccount first.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("deposit too small")) {
                habbo.whisper("A minimum deposit at $3 is required.", RoomChatMessageBubbles.WIRED);
            } else {
                habbo.whisper("Invalid deposit: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            }
            return true;
        }

        long fee = amount - net;
        RpChat.emote(habbo, hide
                ? "*deposits money into their bank account*"
                : "*deposits $" + amount + " into their bank account*");
        habbo.whisper("You've been charged a processing fee of $" + fee
                        + "; $" + net + " has been deposited to your account.",
                RoomChatMessageBubbles.WIRED);
        return true;
    }
}
