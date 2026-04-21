package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;

/**
 * {@code :transfer <username> <amount>} — bank-to-bank wire. Neither
 * party's cash is touched; this purely moves {@code rp_player_bank.balance}
 * between the two accounts.
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
            sender.whisper("Usage: :transfer <username> <amount> [hide]", RoomChatMessageBubbles.ALERT);
            return true;
        }
        String targetName = params[1];
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

        // Resolve recipient — prefer online (we get a name + can whisper them),
        // fall back to offline HabboInfo (id + name; we just do the DB side).
        Habbo onlineTarget = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetName);
        int targetHabboId;
        String targetUsername;
        if (onlineTarget != null) {
            targetHabboId = onlineTarget.getHabboInfo().getId();
            targetUsername = onlineTarget.getHabboInfo().getUsername();
        } else {
            HabboInfo offline = HabboManager.getOfflineHabboInfo(targetName);
            if (offline == null) {
                sender.whisper("No such player.", RoomChatMessageBubbles.ALERT);
                return true;
            }
            targetHabboId = offline.getId();
            targetUsername = offline.getUsername();
        }

        if (targetHabboId == sender.getHabboInfo().getId()) {
            sender.whisper("You can't transfer to yourself.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            BankManager.bankTransfer(sender.getHabboInfo().getId(), targetHabboId, amount, "bank_transfer");
        } catch (InsufficientFundsException e) {
            sender.whisper("Not enough in your bank account.", RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            if (e.getHabboId() == sender.getHabboInfo().getId()) {
                sender.whisper("You don't have a bank account. Use :openaccount first.",
                        RoomChatMessageBubbles.ALERT);
            } else {
                sender.whisper(targetUsername + " doesn't have a bank account.",
                        RoomChatMessageBubbles.ALERT);
            }
            return true;
        } catch (IllegalArgumentException e) {
            sender.whisper("Invalid transfer: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (hide) {
            RpChat.emote(sender, "*transfers money to " + targetUsername + "*");
            sender.whisper("Transferred $" + amount + " to " + targetUsername + ".",
                    RoomChatMessageBubbles.ALERT);
        } else {
            RpChat.emote(sender, "*transfers $" + amount + " to " + targetUsername + "*");
        }
        if (onlineTarget != null) {
            onlineTarget.whisper(sender.getHabboInfo().getUsername() + " transferred $"
                    + amount + " to your bank account.", RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}
