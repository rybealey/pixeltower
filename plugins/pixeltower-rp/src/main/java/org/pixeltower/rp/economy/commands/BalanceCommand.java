package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

/**
 * {@code :balance} / {@code :bal} — whispers the caller their current
 * {@code users.credits}. No target-selection; a staff-only
 * {@code :checkbalance <user>} could layer on later if needed (Tier 1 doesn't
 * need it because housekeeping will expose the ledger viewer at
 * {@code /housekeeping/users/{id}/ledger}).
 *
 * Permission is {@code null} so the {@code CommandHandler} bypass applies:
 * available to every rank without touching the {@code permissions} table.
 */
public class BalanceCommand extends Command {

    public BalanceCommand() {
        super(null, new String[] {"balance", "bal"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        int credits = gameClient.getHabbo().getHabboInfo().getCredits();
        gameClient.getHabbo().whisper(
                "Balance: $" + credits,
                RoomChatMessageBubbles.ALERT
        );
        return true;
    }
}
