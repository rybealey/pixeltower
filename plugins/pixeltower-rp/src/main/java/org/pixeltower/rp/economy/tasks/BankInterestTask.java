package org.pixeltower.rp.economy.tasks;

import com.eu.habbo.Emulator;
import org.pixeltower.rp.economy.BankManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Pays daily interest to every {@code rp_player_bank} row whose balance
 * meets the minimum. Rate / interval / floor are all tunable via
 * {@code emulator_settings} under the {@code rp.bank.*} keyspace, read
 * fresh every run.
 *
 * Each eligible account:
 *   interest = floor(balance * rp.bank.interest_rate)
 * If the result rounds to 0 (balance too small for the rate), nothing is
 * credited — we don't write zero-delta ledger rows.
 *
 * Scheduling lives in PixeltowerRP.onEmulatorLoadedEvent via
 * {@code Emulator.getThreading().getService().scheduleAtFixedRate}. Task
 * runs on Arcturus's scheduled executor, same pool Room cycle ticks use.
 */
public class BankInterestTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BankInterestTask.class);

    @Override
    public void run() {
        double rate = Emulator.getConfig().getDouble("rp.bank.interest_rate", 0.001d);
        long minBalance = Emulator.getConfig().getInt("rp.bank.interest_min_balance", 100);

        int paidAccounts = 0;
        long paidTotal = 0;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT habbo_id, balance FROM rp_player_bank WHERE balance >= ?")) {
            ps.setLong(1, minBalance);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int habboId = rs.getInt("habbo_id");
                    long balance = rs.getLong("balance");
                    long interest = (long) Math.floor(balance * rate);
                    if (interest <= 0) continue;

                    try (Connection wConn = Emulator.getDatabase().getDataSource().getConnection()) {
                        wConn.setAutoCommit(false);
                        try {
                            creditBank(wConn, habboId, interest);
                            BankManager.writePlayerLedger(
                                    wConn, habboId, interest, balance + interest,
                                    "bank_interest", null);
                            wConn.commit();
                            paidAccounts++;
                            paidTotal += interest;
                        } catch (SQLException inner) {
                            wConn.rollback();
                            LOGGER.error("interest credit failed habbo={} interest={}",
                                    habboId, interest, inner);
                        } finally {
                            wConn.setAutoCommit(true);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("BankInterestTask sweep failed", e);
            return;
        }

        LOGGER.info("bank_interest_tick rate={} accounts_paid={} total_paid={}",
                rate, paidAccounts, paidTotal);
    }

    private static void creditBank(Connection conn, int habboId, long amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE rp_player_bank SET balance = balance + ?, last_txn_at = CURRENT_TIMESTAMP "
                        + "WHERE habbo_id = ?")) {
            ps.setLong(1, amount);
            ps.setInt(2, habboId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("no rp_player_bank row for habbo " + habboId);
            }
        }
    }
}
