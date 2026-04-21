package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;

/**
 * {@code :give <username|x> <amount>} — transfer coins (cash-on-hand) from
 * the caller to another player. Online-only and proximity-gated: the
 * recipient must be in the same room as the caller, within 2 tiles
 * (Chebyshev / king-move). Offline transfer is not a Tier 1 feature
 * and would require different UX (escrow vs. immediate).
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

        if (sender.getRoomUnit() == null || !sender.getRoomUnit().isInRoom()) {
            sender.whisper("You must be in a room to :give.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (target.getRoomUnit() == null
                || !target.getRoomUnit().isInRoom()
                || sender.getRoomUnit().getRoom() != target.getRoomUnit().getRoom()) {
            sender.whisper(resolved.username + " isn't here.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        int dx = Math.abs(sender.getRoomUnit().getX() - target.getRoomUnit().getX());
        int dy = Math.abs(sender.getRoomUnit().getY() - target.getRoomUnit().getY());
        if (Math.max(dx, dy) > 2) {
            sender.whisper(resolved.username + " is too far away, try getting closer to them.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

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

        RpChat.emote(sender, "*gives " + resolved.username + " $" + amount + "*");
        target.whisper(
                sender.getHabboInfo().getUsername() + " gave you $" + amount + ".",
                RoomChatMessageBubbles.WIRED
        );
        return true;
    }
}
