package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankManager;

import java.util.Optional;

/**
 * {@code :balance} / {@code :bal} — public RP emote announcing the
 * caller's bank balance to the room. "Balance" in RP vernacular means the
 * money in your account, not the coins physically on you — those live on
 * Habbo's top-bar counter already.
 *
 * Synonym of {@link BankCommand} but with subtler emote wording (the
 * target + observers learn the RP flavour without the explicit "bank"
 * word, useful when you want to sound casual).
 *
 * {@code :balance hide} / {@code :bal hide} — public emote drops the
 * amount; private whisper carries the number.
 *
 * Requires a bank account; no auto-open. Players who haven't run
 * {@code :openaccount} get pointed there.
 */
public class BalanceCommand extends Command {

    public BalanceCommand() {
        super(null, new String[] {"balance", "bal"});
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
            RpChat.emote(habbo, "*checks their balance*");
            RpChat.infoBubble(habbo, "You've got $" + bal + " in your bank account.");
            return true;
        }
        RpChat.emote(habbo, "*checks their balance, finding that they have $" + bal + "*");
        return true;
    }
}
