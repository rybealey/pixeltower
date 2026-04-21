package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;

/**
 * {@code :award <user|x> <currency> <amount>} — staff currency adjustment.
 *
 * Positive amount credits, negative amount debits. Every award writes an
 * {@code rp_money_ledger} row with {@code reason='admin_award_<currency>'}
 * and {@code ref_id=<staff habbo id>} so the audit shows who did it.
 *
 * Supported currencies (v1):
 *   - {@code coins}   — the primary RP dollar, maps to users.credits
 *   - {@code credits} — silent alias for 'coins' (the DB column name)
 *   - {@code bank}    — rp_player_bank.balance
 *
 * Works on offline users. Passing {@code x} in place of a username
 * substitutes the caller's current target.
 *
 * Gated by rank — staff only. Threshold tuned via {@code rp.admin.min_rank}
 * in {@code emulator_settings} (default 5). Self-gate on rank inside
 * handle(), so it works without touching the Arcturus {@code permissions}
 * table.
 *
 * Examples:
 *   :award Rye coins 500         → credit 500 to Rye's coins wallet
 *   :award Rye coins -250        → debit 250 from Rye's coins wallet
 *   :award x coins 100           → same, to the clicked/target user
 *   :award Rye bank 10000        → credit 10000 to Rye's bank (opens acct if needed)
 *   :award Rye bank -1000        → debit 1000 from Rye's bank (must exist + have funds)
 */
public class AwardCommand extends Command {

    public AwardCommand() {
        super(null, new String[] {"award"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        if (staff.getHabboInfo().getRank().getId() < minRank) {
            staff.whisper("You don't have permission to run :award.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 4) {
            staff.whisper("Usage: :award <user|x> <currency> <amount>", RoomChatMessageBubbles.ALERT);
            staff.whisper("Currencies: coins, bank. Negative amount deducts.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String currencyArg = params[2].toLowerCase();
        long amount;
        try {
            amount = Long.parseLong(params[3]);
        } catch (NumberFormatException e) {
            staff.whisper("Amount must be a whole number.", RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (amount == 0) {
            staff.whisper("Amount must be non-zero.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        long refId = (long) staff.getHabboInfo().getId();
        String reason = "admin_award_" + currencyArg;

        try {
            switch (currencyArg) {
                case "coins":
                case "credits":
                    awardCoins(resolved, amount, reason, refId);
                    break;
                case "bank":
                    BankManager.adminAdjust(resolved.habboId, amount, reason, refId);
                    break;
                default:
                    staff.whisper("Unknown currency: " + currencyArg + ". Use coins or bank.",
                            RoomChatMessageBubbles.ALERT);
                    return true;
            }
        } catch (InsufficientFundsException e) {
            staff.whisper(resolved.username + " doesn't have enough " + currencyArg + " to deduct.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            staff.whisper(resolved.username + " doesn't have a bank account (and delta is negative, so we won't auto-open).",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            staff.whisper("Invalid award: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        RpChat.staffEmote(staff, buildAnnouncement(
                staff.getHabboInfo().getUsername(), resolved.username, currencyArg, amount));

        String sign = amount > 0 ? "+" : "";
        staff.whisper("Awarded " + sign + amount + " " + currencyArg + " to " + resolved.username + ".",
                RoomChatMessageBubbles.ALERT);
        if (resolved.isOnline() && resolved.habboId != staff.getHabboInfo().getId()) {
            resolved.online.whisper("Staff adjusted your " + currencyArg + " by " + sign + amount + ".",
                    RoomChatMessageBubbles.ALERT);
        }
        return true;
    }

    /**
     * Public announcement text. Four templates covering (coins|bank) x
     * (credit|debit). Always asterisk-wrapped so the Nitro action-emote
     * renderer kicks in; the STAFF bubble branch leaves the username
     * in-text intact (no splice).
     */
    private static String buildAnnouncement(String admin, String target, String currency, long amount) {
        boolean isBank = "bank".equals(currency);
        long abs = Math.abs(amount);
        if (amount > 0) {
            if (isBank) {
                return "*" + admin + " issued $" + abs + " to " + target + "'s bank account*";
            }
            return "*" + admin + " awarded " + target + " $" + abs + "*";
        }
        // negative
        if (isBank) {
            return "*" + admin + " revoked $" + abs + " from " + target + "'s bank account*";
        }
        return "*" + admin + " deducted $" + abs + " from " + target + "*";
    }

    private static void awardCoins(ResolvedTarget target, long amount, String reason, long refId)
            throws InsufficientFundsException {
        if (target.isOnline()) {
            if (amount > 0) {
                MoneyLedger.credit(target.online, amount, reason, refId);
            } else {
                MoneyLedger.debit(target.online, -amount, reason, refId);
            }
            return;
        }
        if (amount > 0) {
            MoneyLedger.creditOffline(target.habboId, amount, reason, refId);
        } else {
            MoneyLedger.debitOffline(target.habboId, -amount, reason, refId);
        }
    }
}
