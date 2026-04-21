package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.outgoing.PlaySoundComposer;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;

/**
 * {@code :transfer <username|x> <amount>} — bank-to-bank wire. Neither
 * party's coins (cash) are touched; this purely moves
 * {@code rp_player_bank.balance} between the two accounts.
 *
 * Passing {@code x} as the username substitutes the caller's current
 * target.
 *
 * Recipient may be offline — that's the whole point of bank transfers
 * over {@code :give}. No fee. Both parties must have opened accounts.
 *
 * Location-gated to ATM rooms for the sender (the recipient can be
 * anywhere, including offline).
 */
public class TransferCommand extends Command {

    public TransferCommand() {
        super(null, new String[] {"transfer"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo sender = gameClient.getHabbo();
        if (params.length < 3) {
            sender.whisper("Usage: :transfer <username|x> <amount> [hide]", RoomChatMessageBubbles.ALERT);
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(params[2]);
        } catch (NumberFormatException e) {
            sender.whisper("Amount must be a whole number.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (amount <= 0) {
            sender.whisper("Amount must be greater than zero.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        boolean hide = params.length >= 4 && "hide".equalsIgnoreCase(params[3]);

        if (!AtmGate.inAtmRoom(sender)) {
            sender.whisper("You need to be at an ATM to transfer.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(sender, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            sender.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.habboId == sender.getHabboInfo().getId()) {
            sender.whisper("You can't transfer to yourself.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            BankManager.bankTransfer(sender.getHabboInfo().getId(), resolved.habboId,
                    amount, "bank_transfer");
        } catch (InsufficientFundsException e) {
            sender.whisper("Not enough in your bank account.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            if (e.getHabboId() == sender.getHabboInfo().getId()) {
                sender.whisper("You don't have a bank account. Use :openaccount first.",
                        RoomChatMessageBubbles.ALERT);
            } else {
                sender.whisper(resolved.username + " doesn't have a bank account.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        } catch (IllegalArgumentException e) {
            sender.whisper("Invalid transfer: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (hide) {
            RpChat.emote(sender, "*transfers money to " + resolved.username + "*");
            sender.whisper("$" + amount + " has been transferred to "
                            + resolved.username + "'s bank account.",
                    RoomChatMessageBubbles.WIRED);
        } else {
            RpChat.emote(sender, "*transfers $" + amount + " to " + resolved.username + "*");
        }
        sender.getClient().sendResponse(new PlaySoundComposer(PlaySoundComposer.SAMPLE_CREDITS));
        if (resolved.isOnline()) {
            resolved.online.whisper(sender.getHabboInfo().getUsername() + " transferred $"
                    + amount + " to your bank account.", RoomChatMessageBubbles.WIRED);
            resolved.online.getClient().sendResponse(
                    new PlaySoundComposer(PlaySoundComposer.SAMPLE_CREDITS));
        }
        return true;
    }
}
