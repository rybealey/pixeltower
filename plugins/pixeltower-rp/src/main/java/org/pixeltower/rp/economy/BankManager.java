package org.pixeltower.rp.economy;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * The bank's write layer. All state lives in {@code rp_player_bank.balance}
 * (never on {@code users.credits} — that column is exclusively coins-on-hand).
 *
 * Deposits pay a {@code rp.bank.fee_rate} (default 1%) of gross to the
 * Bank corp's treasury. Withdrawals and bank-to-bank transfers are free
 * per locked Tier 1 design.
 *
 * Every balance change is audited:
 *   - Player-side via {@code rp_money_ledger}
 *   - Corp treasury side via {@code rp_corp_treasury_ledger}
 *
 * Location gating ({@code rp.bank.atm_room_ids}) is enforced at the command
 * layer, not here — this service trusts its caller to have done that check.
 */
public final class BankManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BankManager.class);

    private BankManager() {}

    // ──────────── account lifecycle ────────────

    public static boolean hasAccount(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM rp_player_bank WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("hasAccount habbo={} failed", habboId, e);
            throw new RuntimeException(e);
        }
    }

    /** Returns true if a new row was inserted; false if the account already existed. */
    public static boolean openAccount(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO rp_player_bank (habbo_id) VALUES (?)")) {
            ps.setInt(1, habboId);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("bank_account_opened habbo={}", habboId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.error("openAccount habbo={} failed", habboId, e);
            throw new RuntimeException(e);
        }
    }

    /** Returns the bank balance, or empty if no account exists. */
    public static Optional<Long> getBalance(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT balance FROM rp_player_bank WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("balance"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            LOGGER.error("getBalance habbo={} failed", habboId, e);
            throw new RuntimeException(e);
        }
    }

    // ──────────── admin direct-adjust ────────────

    /**
     * Staff-driven adjustment of a bank balance. Signed delta: positive
     * credits, negative debits. Auto-opens an account on positive delta if
     * the user doesn't have one yet. Writes an audit row in
     * {@code rp_money_ledger} (reason='admin_award_bank') with the staff's
     * habbo id as ref_id.
     *
     * Does NOT touch users.credits — this mints/burns bank balance directly
     * (the "admin award" shape). If you want the full deposit/withdraw
     * semantics with fees + coin movement, use those APIs instead.
     *
     * @return new bank balance after the adjustment
     * @throws InsufficientFundsException if delta is negative and balance
     *                                    would go below zero
     * @throws BankAccountNotOpenException if delta is negative and the
     *                                     target has no account (we don't
     *                                     silently create-then-overdraw)
     */
    public static long adminAdjust(int habboId, long delta, String reason, Long refId)
            throws InsufficientFundsException, BankAccountNotOpenException {
        if (delta == 0) throw new IllegalArgumentException("delta must be non-zero");

        boolean hasAccount = hasAccount(habboId);
        if (delta > 0 && !hasAccount) {
            openAccount(habboId);
        } else if (delta < 0 && !hasAccount) {
            throw new BankAccountNotOpenException(habboId);
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long balanceAfter;
                if (delta > 0) {
                    balanceAfter = creditBankInTx(conn, habboId, delta);
                } else {
                    balanceAfter = debitBankInTx(conn, habboId, -delta);
                }
                writePlayerLedger(conn, habboId, delta, balanceAfter, reason, refId);
                conn.commit();
                LOGGER.info("admin_bank_adjust habbo={} delta={} newBalance={} reason={} refId={}",
                        habboId, delta, balanceAfter, reason, refId);
                return balanceAfter;
            } catch (SQLException inner) {
                conn.rollback();
                // debitBankInTx signals insufficient funds via message prefix; re-map to typed.
                if (inner.getMessage() != null && inner.getMessage().startsWith("insufficient bank funds")) {
                    throw new InsufficientFundsException(habboId, (int) Math.min(Integer.MAX_VALUE, -delta));
                }
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("adminAdjust habbo={} delta={} failed", habboId, delta, e);
            throw new RuntimeException("adminAdjust failed", e);
        }
    }

    // ──────────── coins ↔ bank operations ────────────

    /**
     * Move coins → bank. Takes the configured deposit fee off the gross and
     * routes it to the Bank corp treasury.
     *
     * @return the amount actually credited to the player's bank (gross - fee)
     */
    public static long deposit(Habbo habbo, long gross, String reason) throws
            InsufficientFundsException, BankAccountNotOpenException {
        if (gross <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (gross > Integer.MAX_VALUE) throw new IllegalArgumentException("amount exceeds credits column range");
        int habboId = habbo.getHabboInfo().getId();
        if (!hasAccount(habboId)) throw new BankAccountNotOpenException(habboId);

        double feeRate = Emulator.getConfig().getDouble("rp.bank.fee_rate", 0.01d);
        long fee = (long) Math.floor(gross * feeRate);
        long net = gross - fee;

        // Debit coins for the full gross — this writes the player-side
        // ledger row and syncs HabboInfo + top-bar.
        MoneyLedger.debit(habbo, gross, reason, null);

        // Credit bank + route fee to treasury in a single DB transaction so
        // either both land or neither does.
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long newBankBalance = creditBankInTx(conn, habboId, net);
                writePlayerLedger(conn, habboId, net, newBankBalance, "bank_deposit", null);
                if (fee > 0) {
                    int feeCorpId = resolveFeeCorpId(conn);
                    long newTreasury = creditTreasuryInTx(conn, feeCorpId, fee);
                    writeTreasuryLedger(conn, feeCorpId, fee, newTreasury, "bank_deposit_fee", habboId);
                }
                conn.commit();
                LOGGER.info("bank_deposit habbo={} gross={} fee={} net={} newBalance={}",
                        habboId, gross, fee, net, newBankBalance);
                return net;
            } catch (SQLException inner) {
                conn.rollback();
                // Refund the coins we just debited — best-effort; if this also
                // throws we log loudly so ops can reconcile manually.
                try {
                    MoneyLedger.credit(habbo, gross, "bank_deposit_rollback", null);
                } catch (Exception refund) {
                    LOGGER.error("REFUND FAILED after deposit rollback habbo={} gross={}",
                            habboId, gross, refund);
                }
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("deposit transaction failed habbo={} gross={}", habboId, gross, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Move bank → coins. No fee. Player must have an account and sufficient
     * bank balance.
     *
     * @return new bank balance after withdrawal
     */
    public static long withdraw(Habbo habbo, long amount, String reason) throws
            InsufficientFundsException, BankAccountNotOpenException {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("amount exceeds credits column range");
        int habboId = habbo.getHabboInfo().getId();
        long currentBalance = getBalance(habboId)
                .orElseThrow(() -> new BankAccountNotOpenException(habboId));
        if (currentBalance < amount) {
            throw new InsufficientFundsException(habboId, (int) amount);
        }

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long newBankBalance = debitBankInTx(conn, habboId, amount);
                writePlayerLedger(conn, habboId, -amount, newBankBalance, "bank_withdraw", null);
                conn.commit();
                // Coin credit happens outside the tx — the MoneyLedger write
                // has its own transaction; failure here means bank was
                // debited but coins not credited (we log + continue; the
                // inverse direction of the deposit rollback).
                MoneyLedger.credit(habbo, amount, reason, null);
                LOGGER.info("bank_withdraw habbo={} amount={} newBalance={}",
                        habboId, amount, newBankBalance);
                return newBankBalance;
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("withdraw transaction failed habbo={} amount={}", habboId, amount, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Bank-to-bank transfer. Both parties must have accounts; recipient may
     * be offline (that's the whole point of bank transfers vs. :give coins).
     * No fee.
     */
    public static void bankTransfer(int fromHabboId, int toHabboId, long amount, String reason)
            throws InsufficientFundsException, BankAccountNotOpenException {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("amount exceeds credits column range");
        if (fromHabboId == toHabboId) throw new IllegalArgumentException("cannot transfer to self");
        if (!hasAccount(fromHabboId)) throw new BankAccountNotOpenException(fromHabboId);
        if (!hasAccount(toHabboId))   throw new BankAccountNotOpenException(toHabboId);

        long senderBalance = getBalance(fromHabboId).orElse(0L);
        if (senderBalance < amount) throw new InsufficientFundsException(fromHabboId, (int) amount);

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long senderNew    = debitBankInTx(conn, fromHabboId, amount);
                long recipientNew = creditBankInTx(conn, toHabboId, amount);
                writePlayerLedger(conn, fromHabboId, -amount, senderNew,    "bank_transfer", (long) toHabboId);
                writePlayerLedger(conn, toHabboId,   amount,  recipientNew, "bank_transfer", (long) fromHabboId);
                conn.commit();
                LOGGER.info("bank_transfer sender={} recipient={} amount={} senderAfter={} recipientAfter={}",
                        fromHabboId, toHabboId, amount, senderNew, recipientNew);
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("bankTransfer failed {} -> {} amount={}", fromHabboId, toHabboId, amount, e);
            throw new RuntimeException(e);
        }
    }

    // ──────────── SQL helpers (all assume an open transaction) ────────────

    private static long creditBankInTx(Connection conn, int habboId, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE rp_player_bank SET balance = balance + ?, last_txn_at = CURRENT_TIMESTAMP "
                        + "WHERE habbo_id = ?")) {
            ps.setLong(1, delta);
            ps.setInt(2, habboId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("no rp_player_bank row for habbo " + habboId);
            }
        }
        return getBalanceInTx(conn, habboId);
    }

    private static long debitBankInTx(Connection conn, int habboId, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE rp_player_bank SET balance = balance - ?, last_txn_at = CURRENT_TIMESTAMP "
                        + "WHERE habbo_id = ? AND balance >= ?")) {
            ps.setLong(1, delta);
            ps.setInt(2, habboId);
            ps.setLong(3, delta);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("insufficient bank funds habbo=" + habboId);
            }
        }
        return getBalanceInTx(conn, habboId);
    }

    private static long getBalanceInTx(Connection conn, int habboId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM rp_player_bank WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("no rp_player_bank row for habbo " + habboId);
                return rs.getLong(1);
            }
        }
    }

    private static int resolveFeeCorpId(Connection conn) throws SQLException {
        String key = Emulator.getConfig().getValue("rp.bank.fee_corp_key", "bank");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM rp_corporations WHERE corp_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("no rp_corporations row for key=" + key);
                return rs.getInt(1);
            }
        }
    }

    private static long creditTreasuryInTx(Connection conn, int corpId, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE rp_corporations SET treasury = treasury + ? WHERE id = ?")) {
            ps.setLong(1, delta);
            ps.setInt(2, corpId);
            if (ps.executeUpdate() == 0) throw new SQLException("no rp_corporations row id=" + corpId);
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT treasury FROM rp_corporations WHERE id = ?")) {
            ps.setInt(1, corpId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("no rp_corporations row id=" + corpId);
                return rs.getLong(1);
            }
        }
    }

    public static void writePlayerLedger(Connection conn, int habboId, long delta, long balanceAfter,
                                         String reason, Long refId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO rp_money_ledger (habbo_id, delta, balance_after, reason, ref_id) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, habboId);
            ps.setLong(2, delta);
            ps.setLong(3, balanceAfter);
            ps.setString(4, reason);
            if (refId == null) ps.setNull(5, Types.BIGINT); else ps.setLong(5, refId);
            ps.executeUpdate();
        }
    }

    public static void writeTreasuryLedger(Connection conn, int corpId, long delta, long balanceAfter,
                                           String reason, Integer refHabboId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO rp_corp_treasury_ledger (corp_id, delta, balance_after, reason, ref_habbo_id) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, corpId);
            ps.setLong(2, delta);
            ps.setLong(3, balanceAfter);
            ps.setString(4, reason);
            if (refHabboId == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, refHabboId);
            ps.executeUpdate();
        }
    }
}
