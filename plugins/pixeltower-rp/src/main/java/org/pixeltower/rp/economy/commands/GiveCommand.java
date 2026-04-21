package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;

/**
 * {@code :give <username|x> <amount>} — transfer coins (cash-on-hand) from
 * the caller to another online player. Online-only by design; offline
 * transfer is not a Tier 1 feature and would require different UX
 * (escrow vs. immediate).
 *
 * Passing {@code x} as the username substitutes the caller's current
 * target (the last user whose profile card they opened, or whoever
 * they set via {@code :target}).
 *
 * Example in-game: {@code :give alice 500}  or  click alice then {@code :give x 500}
 */
public class GiveCommand extends Command {

    public GiveCommand() {
        super(null, new String[] {"give", "pay"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo sender = gameClient.getHabbo();

        if (params.length < 3) {
            sender.whisper("Usage: :give <username|x> <amount>", RoomChatMessageBubbles.ALERT);
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

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(sender, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            sender.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!resolved.isOnline()) {
            sender.whisper("That player is offline. :give is online-only.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (resolved.habboId == sender.getHabboInfo().getId()) {
            sender.whisper("You can't give yourself money.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        Habbo target = resolved.online;
        try {
            MoneyLedger.transfer(sender, target, amount, "give", null);
        } catch (InsufficientFundsException e) {
            sender.whisper("Not enough coins. Balance: $" + sender.getHabboInfo().getCredits(),
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            sender.whisper("Invalid transfer: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        sender.whisper(
                "Sent $" + amount + " to " + resolved.username + ".",
                RoomChatMessageBubbles.ALERT
        );
        target.whisper(
                sender.getHabboInfo().getUsername() + " sent you $" + amount + ".",
                RoomChatMessageBubbles.ALERT
        );
        return true;
    }
}
