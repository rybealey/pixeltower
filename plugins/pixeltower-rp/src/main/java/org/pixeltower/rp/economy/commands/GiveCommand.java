package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;

/**
 * {@code :give <username> <amount>} — transfer credits from the caller to
 * another online player. Online-only by design; offline transfer is not a
 * Tier 1 feature and would require different UX (escrow vs. immediate).
 *
 * Example in-game: {@code :give alice 500}
 */
public class GiveCommand extends Command {

    public GiveCommand() {
        super(null, new String[] {"give", "pay"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo sender = gameClient.getHabbo();

        // params[0] is the command alias itself; args start at [1]
        if (params.length < 3) {
            sender.whisper("Usage: :give <username> <amount>", RoomChatMessageBubbles.ALERT);
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

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetName);
        if (target == null) {
            sender.whisper("That player is offline or doesn't exist. :give is online-only.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (target.getHabboInfo().getId() == sender.getHabboInfo().getId()) {
            sender.whisper("You can't give yourself money.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        try {
            MoneyLedger.transfer(sender, target, amount, "give", null);
        } catch (InsufficientFundsException e) {
            sender.whisper("Not enough money. Balance: $" + sender.getHabboInfo().getCredits(),
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            sender.whisper("Invalid transfer: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        sender.whisper(
                "Sent $" + amount + " to " + target.getHabboInfo().getUsername() + ".",
                RoomChatMessageBubbles.ALERT
        );
        target.whisper(
                sender.getHabboInfo().getUsername() + " sent you $" + amount + ".",
                RoomChatMessageBubbles.ALERT
        );
        return true;
    }
}
