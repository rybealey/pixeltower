package org.pixeltower.rp.economy.commands;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.economy.BankManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code :balance} / {@code :bal} — public RP emote announcing the
 * caller's bank balance to the room. "Balance" in RP vernacular means the
 * money in your account, not the coins physically on you — those live on
 * Habbo's top-bar counter already.
 *
 * {@code :balance hide} / {@code :bal hide} — public emote drops the
 * amount; private whisper carries the number.
 *
 * Rate-limited per caller ({@value COOLDOWN_MS}ms) to curb spam — balance
 * checks are cheap but the public emote they produce isn't.
 *
 * Requires a bank account; no auto-open. Players who haven't run
 * {@code :openaccount} get pointed there.
 */
public class BalanceCommand extends Command {

    private static final long COOLDOWN_MS = 10_000L;

    private static final Map<Integer, Long> LAST_CHECK = new ConcurrentHashMap<>();

    public BalanceCommand() {
        super(null, new String[] {"balance", "bal"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo habbo = gameClient.getHabbo();
        int habboId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        Long last = LAST_CHECK.get(habboId);
        if (last != null) {
            long sinceLast = now - last;
            if (sinceLast < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - sinceLast + 999) / 1000;
                habbo.whisper("You can check your balance again in " + remaining + "s.",
                        RoomChatMessageBubbles.ALERT);
                return true;
            }
        }
        LAST_CHECK.put(habboId, now);

        Optional<Long> balance = BankManager.getBalance(habboId);
        if (balance.isEmpty()) {
            habbo.whisper("You don't have a bank account. Use :openaccount first.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        long bal = balance.get();
        boolean hide = params.length >= 2 && "hide".equalsIgnoreCase(params[1]);

        if (hide) {
            RpChat.emote(habbo, "*checks their balance*");
            habbo.whisper("You've got $" + bal + " in your bank account.",
                    RoomChatMessageBubbles.WIRED);
            return true;
        }
        RpChat.emote(habbo, "*checks their balance, finding that they have $" + bal + "*");
        return true;
    }
}
