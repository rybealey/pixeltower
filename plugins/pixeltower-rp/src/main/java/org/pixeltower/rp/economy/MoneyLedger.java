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

/**
 * The money system's write layer.
 *
 * Currency lives on Arcturus's built-in {@code users.credits} column; this
 * class does not own balance state. Every mutation (a) calls
 * {@link Habbo#giveCredits(int)} for online players, so the in-memory
 * HabboInfo, the Arcturus persistence queue, and the client's top-bar
 * counter are all kept in sync automatically, and (b) writes an append-only
 * audit row to {@code rp_money_ledger} with a {@code balance_after}
 * snapshot.
 *
 * Audit rows are written <em>before</em> the in-memory mutation so a
 * failed ledger write short-circuits without moving credits. This
 * deliberately accepts the opposite failure mode: if the in-memory
 * {@code giveCredits} is cancelled (e.g. by another plugin hooking
 * {@code UserCreditsEvent}), the ledger row is orphaned. That's acceptable
 * for v1 — the invariant worth protecting is "no credits moved without an
 * audit row"; detecting "audit row without credits moved" can be done by
 * reconciling ledger sums against live balances.
 *
 * Tier 1 scope: only online-player operations. Offline credit/debit (for
 * paycheck processing of logged-out members, bounty expiry, etc.) ships
 * in Tier 1's corp subsystem where it's actually needed.
 */
public final class MoneyLedger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoneyLedger.class);

    private MoneyLedger() {
        // static-only
    }

    /**
     * Player-to-player transfer. Both Habbo references must be non-null
     * (online) and distinct. Audit rows for both sides are written in a
     * single DB transaction; if that commit fails, no credits move.
     *
     * @throws InsufficientFundsException if the sender's balance &lt; amount
     * @throws IllegalArgumentException   on zero/negative amount, int overflow,
     *                                    null Habbo, or self-transfer
     */
    public static void transfer(Habbo from, Habbo to, long amount, String reason, Long refId)
            throws InsufficientFundsException {
        if (from == null || to == null) {
            throw new IllegalArgumentException("habbo must not be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("amount exceeds credits column range");
        }
        int intAmount = (int) amount;
        int senderId = from.getHabboInfo().getId();
        int recipientId = to.getHabboInfo().getId();
        if (senderId == recipientId) {
            throw new IllegalArgumentException("cannot transfer to self");
        }
        int senderBalance = from.getHabboInfo().getCredits();
        if (senderBalance < intAmount) {
            throw new InsufficientFundsException(senderId, intAmount);
        }

        long senderBalanceAfter = (long) senderBalance - intAmount;
        long recipientBalanceAfter = (long) to.getHabboInfo().getCredits() + intAmount;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertLedgerRow(conn, senderId,    -intAmount, senderBalanceAfter,    reason, refId);
                insertLedgerRow(conn, recipientId,  intAmount, recipientBalanceAfter, reason, refId);
                conn.commit();
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("transfer ledger write failed sender={} recipient={} amount={}",
                    senderId, recipientId, intAmount, e);
            throw new RuntimeException("ledger write failed", e);
        }

        from.giveCredits(-intAmount);
        to.giveCredits(intAmount);

        LOGGER.info("transfer sender={} recipient={} amount={} reason={} refId={}",
                senderId, recipientId, intAmount, reason, refId);
    }

    /**
     * Credit an online player. Writes one audit row, then calls
     * {@link Habbo#giveCredits(int)}.
     */
    public static void credit(Habbo habbo, long amount, String reason, Long refId) {
        validate(habbo, amount);
        int intAmount = (int) amount;
        int habboId = habbo.getHabboInfo().getId();
        long balanceAfter = (long) habbo.getHabboInfo().getCredits() + intAmount;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            insertLedgerRow(conn, habboId, intAmount, balanceAfter, reason, refId);
        } catch (SQLException e) {
            LOGGER.error("credit ledger write failed habbo={} amount={}", habboId, intAmount, e);
            throw new RuntimeException("ledger write failed", e);
        }
        habbo.giveCredits(intAmount);
        LOGGER.info("credit habbo={} amount={} reason={} refId={}", habboId, intAmount, reason, refId);
    }

    /**
     * Debit an online player. Throws if balance &lt; amount.
     *
     * @return new balance after the debit
     */
    public static long debit(Habbo habbo, long amount, String reason, Long refId)
            throws InsufficientFundsException {
        validate(habbo, amount);
        int intAmount = (int) amount;
        int habboId = habbo.getHabboInfo().getId();
        int balance = habbo.getHabboInfo().getCredits();
        if (balance < intAmount) {
            throw new InsufficientFundsException(habboId, intAmount);
        }
        long balanceAfter = (long) balance - intAmount;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            insertLedgerRow(conn, habboId, -intAmount, balanceAfter, reason, refId);
        } catch (SQLException e) {
            LOGGER.error("debit ledger write failed habbo={} amount={}", habboId, intAmount, e);
            throw new RuntimeException("ledger write failed", e);
        }
        habbo.giveCredits(-intAmount);
        LOGGER.info("debit habbo={} amount={} reason={} refId={}", habboId, intAmount, reason, refId);
        return balanceAfter;
    }

    /**
     * Credit an offline player. Direct SQL UPDATE on {@code users.credits}
     * + audit row. If the player is currently online the in-memory
     * HabboInfo will not be updated; callers who know the player is
     * online should use {@link #credit(Habbo, long, String, Long)} instead.
     *
     * @return new balance after the credit
     */
    public static long creditOffline(int habboId, long amount, String reason, Long refId) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("amount exceeds credits column range");

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long balanceAfter = updateCreditsAndRead(conn, habboId, (int) amount);
                insertLedgerRow(conn, habboId, amount, balanceAfter, reason, refId);
                conn.commit();
                LOGGER.info("credit_offline habbo={} amount={} reason={} refId={}",
                        habboId, amount, reason, refId);
                return balanceAfter;
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("creditOffline habbo={} amount={} failed", habboId, amount, e);
            throw new RuntimeException("creditOffline failed", e);
        }
    }

    /**
     * Debit an offline player. Throws if balance &lt; amount.
     * @return new balance after the debit
     */
    public static long debitOffline(int habboId, long amount, String reason, Long refId)
            throws InsufficientFundsException {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("amount exceeds credits column range");

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long balanceAfter = updateCreditsWithGuardAndRead(conn, habboId, -(int) amount);
                insertLedgerRow(conn, habboId, -amount, balanceAfter, reason, refId);
                conn.commit();
                LOGGER.info("debit_offline habbo={} amount={} reason={} refId={}",
                        habboId, amount, reason, refId);
                return balanceAfter;
            } catch (InsufficientFundsException ife) {
                conn.rollback();
                throw ife;
            } catch (SQLException inner) {
                conn.rollback();
                throw inner;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("debitOffline habbo={} amount={} failed", habboId, amount, e);
            throw new RuntimeException("debitOffline failed", e);
        }
    }

    private static long updateCreditsAndRead(Connection conn, int habboId, int delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET credits = credits + ? WHERE id = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, habboId);
            if (ps.executeUpdate() == 0) throw new SQLException("no users row for id=" + habboId);
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT credits FROM users WHERE id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("no users row for id=" + habboId);
                return rs.getInt(1);
            }
        }
    }

    private static long updateCreditsWithGuardAndRead(Connection conn, int habboId, int signedDelta)
            throws SQLException, InsufficientFundsException {
        // For debit, the guard is `credits + signedDelta >= 0` which is the same as
        // `credits >= -signedDelta`.
        int guardAmount = -signedDelta;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET credits = credits + ? WHERE id = ? AND credits >= ?")) {
            ps.setInt(1, signedDelta);
            ps.setInt(2, habboId);
            ps.setInt(3, guardAmount);
            if (ps.executeUpdate() == 0) {
                throw new InsufficientFundsException(habboId, guardAmount);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT credits FROM users WHERE id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("no users row for id=" + habboId);
                return rs.getInt(1);
            }
        }
    }

    private static void validate(Habbo habbo, long amount) {
        if (habbo == null) throw new IllegalArgumentException("habbo must not be null");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("amount exceeds credits column range");
        }
    }

    private static void insertLedgerRow(Connection conn, int habboId, long delta, long balanceAfter,
                                        String reason, Long refId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO rp_money_ledger (habbo_id, delta, balance_after, reason, ref_id) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, habboId);
            ps.setLong(2, delta);
            ps.setLong(3, balanceAfter);
            ps.setString(4, reason);
            if (refId == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, refId);
            }
            ps.executeUpdate();
        }
    }
}
