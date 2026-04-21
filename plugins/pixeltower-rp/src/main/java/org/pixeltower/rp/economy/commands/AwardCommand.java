package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import org.pixeltower.rp.economy.BankAccountNotOpenException;
import org.pixeltower.rp.economy.BankManager;
import org.pixeltower.rp.economy.InsufficientFundsException;
import org.pixeltower.rp.economy.MoneyLedger;

/**
 * {@code :award <user> <currency> <amount>} — staff currency adjustment.
 *
 * Positive amount credits, negative amount debits. Every award writes an
 * {@code rp_money_ledger} row with {@code reason='admin_award_<currency>'}
 * and {@code ref_id=<staff habbo id>} so the audit shows who did it.
 *
 * Supported currencies (v1): {@code cash} (aliases: credits) and
 * {@code bank}. Works on offline users too.
 *
 * Gated by rank — staff only. Threshold tuned via {@code rp.admin.min_rank}
 * in {@code emulator_settings} (default 5). Permission key is
 * {@code cmd_pt_award} but we self-gate on rank inside handle(), so it
 * works without touching the Arcturus {@code permissions} table.
 *
 * Examples:
 *   :award Rye cash 500          → credit 500 to Rye's cash wallet
 *   :award Rye cash -250         → debit 250 from Rye's cash wallet
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
            staff.whisper("Usage: :award <user> <currency> <amount>", RoomChatMessageBubbles.ALERT);
            staff.whisper("Currencies: cash, bank. Negative amount deducts.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];
        String currency = params[2].toLowerCase();
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

        int targetId;
        String targetUsername;
        Habbo onlineTarget = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetName);
        if (onlineTarget != null) {
            targetId = onlineTarget.getHabboInfo().getId();
            targetUsername = onlineTarget.getHabboInfo().getUsername();
        } else {
            HabboInfo offline = HabboManager.getOfflineHabboInfo(targetName);
            if (offline == null) {
                staff.whisper("No such player.", RoomChatMessageBubbles.ALERT);
                return true;
            }
            targetId = offline.getId();
            targetUsername = offline.getUsername();
        }

        long refId = (long) staff.getHabboInfo().getId();
        String reason = "admin_award_" + currency;

        try {
            switch (currency) {
                case "cash":
                case "credits":
                    awardCash(onlineTarget, targetId, amount, reason, refId);
                    break;
                case "bank":
                    BankManager.adminAdjust(targetId, amount, reason, refId);
                    break;
                default:
                    staff.whisper("Unknown currency: " + currency + ". Use cash or bank.",
                            RoomChatMessageBubbles.ALERT);
                    return true;
            }
        } catch (InsufficientFundsException e) {
            staff.whisper(targetUsername + " doesn't have enough " + currency + " to deduct.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (BankAccountNotOpenException e) {
            staff.whisper(targetUsername + " doesn't have a bank account (and delta is negative, so we won't auto-open).",
                    RoomChatMessageBubbles.ALERT);
            return true;
        } catch (IllegalArgumentException e) {
            staff.whisper("Invalid award: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String sign = amount > 0 ? "+" : "";
        staff.whisper("Awarded " + sign + amount + " " + currency + " to " + targetUsername + ".",
                RoomChatMessageBubbles.ALERT);
        if (onlineTarget != null && onlineTarget.getHabboInfo().getId() != staff.getHabboInfo().getId()) {
            onlineTarget.whisper("Staff adjusted your " + currency + " by " + sign + amount + ".",
                    RoomChatMessageBubbles.ALERT);
        }
        return true;
    }

    private static void awardCash(Habbo onlineTarget, int targetId, long amount,
                                  String reason, long refId) throws InsufficientFundsException {
        if (onlineTarget != null) {
            if (amount > 0) {
                MoneyLedger.credit(onlineTarget, amount, reason, refId);
            } else {
                MoneyLedger.debit(onlineTarget, -amount, reason, refId);
            }
            return;
        }
        if (amount > 0) {
            MoneyLedger.creditOffline(targetId, amount, reason, refId);
        } else {
            MoneyLedger.debitOffline(targetId, -amount, reason, refId);
        }
    }
}
