package org.pixeltower.rp.macros;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateless data-access layer for {@code rp_macros}. Macros are small
 * (≤ a few dozen per player) and only touched on window open / save /
 * delete, so each operation hits the DB directly — no in-memory cache.
 *
 * Validation lives here too: keybind + command must be non-empty, and
 * size limits match the column widths from V021.
 */
public final class MacrosManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MacrosManager.class);

    private static final int MAX_KEYBIND_LEN = 64;
    private static final int MAX_COMMAND_LEN = 128;
    private static final int MAX_CATEGORY_LEN = 32;

    private MacrosManager() {}

    public static List<Macro> loadFor(int habboId) {
        List<Macro> out = new ArrayList<>();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, habbo_id, keybind, command, category "
                             + "FROM rp_macros WHERE habbo_id = ? "
                             + "ORDER BY category, id")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Macro(
                            rs.getInt("id"),
                            rs.getInt("habbo_id"),
                            rs.getString("keybind"),
                            rs.getString("command"),
                            rs.getString("category")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.loadFor habbo={} failed", habboId, e);
            return Collections.emptyList();
        }
        return out;
    }

    /**
     * Insert or replace the macro at (habbo_id, keybind). Saving twice
     * with the same keybind updates the existing row's command/category
     * rather than creating a duplicate. Returns true on success.
     */
    public static boolean save(int habboId, String keybind, String command, String category) {
        if (habboId <= 0) return false;
        if (keybind == null || keybind.isEmpty() || keybind.length() > MAX_KEYBIND_LEN) return false;
        if (command == null || command.isEmpty() || command.length() > MAX_COMMAND_LEN) return false;
        String cat = (category == null || category.isEmpty()) ? "Default" : category;
        if (cat.length() > MAX_CATEGORY_LEN) cat = cat.substring(0, MAX_CATEGORY_LEN);

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_macros (habbo_id, keybind, command, category) "
                             + "VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE command = VALUES(command), category = VALUES(category)")) {
            ps.setInt(1, habboId);
            ps.setString(2, keybind);
            ps.setString(3, command);
            ps.setString(4, cat);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.save habbo={} keybind={} failed", habboId, keybind, e);
            return false;
        }
    }

    /**
     * Delete the macro by id, but only if it belongs to the requester.
     * Returns true if a row was deleted.
     */
    public static boolean delete(int habboId, int macroId) {
        if (habboId <= 0 || macroId <= 0) return false;
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM rp_macros WHERE id = ? AND habbo_id = ?",
                     Statement.NO_GENERATED_KEYS)) {
            ps.setInt(1, macroId);
            ps.setInt(2, habboId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.delete habbo={} macroId={} failed", habboId, macroId, e);
            return false;
        }
    }
}
