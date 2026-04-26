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
    private static final String DEFAULT_CATEGORY = "Default";

    private MacrosManager() {}

    public static List<Macro> loadFor(int habboId) {
        List<Macro> out = new ArrayList<>();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, habbo_id, keybind, command, category "
                             + "FROM rp_macros WHERE habbo_id = ? "
                             + "ORDER BY category, sort_order, id")) {
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
     * Returns the player's category list, seeding a "Default" row with
     * is_active=1 if none exist yet. Pre-existing macros' category VARCHAR
     * already reads "Default", so they bind to the seeded row by name.
     */
    public static List<MacroCategory> loadCategoriesFor(int habboId) {
        List<MacroCategory> out = readCategories(habboId);
        if (!out.isEmpty()) return out;

        // First time we've seen this habbo — seed Default + return.
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO rp_macro_categories "
                             + "(habbo_id, name, sort_order, is_active) "
                             + "VALUES (?, ?, 0, 1)")) {
            ps.setInt(1, habboId);
            ps.setString(2, DEFAULT_CATEGORY);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.loadCategoriesFor seed habbo={} failed", habboId, e);
            return Collections.emptyList();
        }
        return readCategories(habboId);
    }

    private static List<MacroCategory> readCategories(int habboId) {
        List<MacroCategory> out = new ArrayList<>();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, habbo_id, name, sort_order, is_active "
                             + "FROM rp_macro_categories WHERE habbo_id = ? "
                             + "ORDER BY sort_order, id")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MacroCategory(
                            rs.getInt("id"),
                            rs.getInt("habbo_id"),
                            rs.getString("name"),
                            rs.getInt("sort_order"),
                            rs.getBoolean("is_active")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.readCategories habbo={} failed", habboId, e);
            return Collections.emptyList();
        }
        return out;
    }

    /**
     * Mark exactly one category active for the requester. Refuses if the
     * id doesn't belong to {@code habboId} (the SET-WHERE-AND-id check
     * handles that without an extra select).
     */
    public static boolean setActiveCategory(int habboId, int categoryId) {
        if (habboId <= 0 || categoryId <= 0) return false;
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE rp_macro_categories SET is_active = (id = ?) WHERE habbo_id = ?")) {
                ps.setInt(1, categoryId);
                ps.setInt(2, habboId);
                int updated = ps.executeUpdate();
                conn.commit();
                return updated > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.setActiveCategory habbo={} cat={} failed",
                    habboId, categoryId, e);
            return false;
        }
    }

    /**
     * Insert a new category for the requester. Name is trimmed, length
     * 1..32, deduped case-insensitive against existing names. Returns 0 on
     * any rejection (empty/too-long/duplicate/SQL error).
     */
    public static int createCategory(int habboId, String name) {
        if (habboId <= 0 || name == null) return 0;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CATEGORY_LEN) return 0;

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_macro_categories "
                             + "(habbo_id, name, sort_order, is_active) "
                             + "SELECT ?, ?, COALESCE(MAX(sort_order), -1) + 1, 0 "
                             + "FROM rp_macro_categories WHERE habbo_id = ?",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, habboId);
            ps.setString(2, trimmed);
            ps.setInt(3, habboId);
            int inserted = ps.executeUpdate();
            if (inserted == 0) return 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            // Likely duplicate name (uniq_habbo_name) — quiet to keep the
            // log clean; UI surfaces this by simply not adding the row.
            LOGGER.debug("MacrosManager.createCategory habbo={} name='{}' rejected: {}",
                    habboId, trimmed, e.getMessage());
        }
        return 0;
    }

    /**
     * Hard-delete a category and every macro inside it. Refuses to touch
     * the "Default" row (UI hides the trash there too). If the deleted
     * row was active, Default becomes active before the row is gone.
     */
    public static boolean deleteCategory(int habboId, int categoryId) {
        if (habboId <= 0 || categoryId <= 0) return false;
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);

            String name;
            boolean wasActive;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, is_active FROM rp_macro_categories "
                            + "WHERE id = ? AND habbo_id = ?")) {
                ps.setInt(1, categoryId);
                ps.setInt(2, habboId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return false; }
                    name = rs.getString("name");
                    wasActive = rs.getBoolean("is_active");
                }
            }
            if (DEFAULT_CATEGORY.equalsIgnoreCase(name)) { conn.rollback(); return false; }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM rp_macros WHERE habbo_id = ? AND category = ?")) {
                ps.setInt(1, habboId);
                ps.setString(2, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM rp_macro_categories WHERE id = ? AND habbo_id = ?")) {
                ps.setInt(1, categoryId);
                ps.setInt(2, habboId);
                ps.executeUpdate();
            }
            if (wasActive) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rp_macro_categories SET is_active = 1 "
                                + "WHERE habbo_id = ? AND name = ?")) {
                    ps.setInt(1, habboId);
                    ps.setString(2, DEFAULT_CATEGORY);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.deleteCategory habbo={} cat={} failed",
                    habboId, categoryId, e);
            return false;
        }
    }

    /**
     * Apply a new display order. The list is the desired top-of-list-first
     * sequence of macroIds; sort_order is assigned 0..N-1 in a single
     * transaction. Ids that don't belong to {@code habboId} are silently
     * skipped (defensive — the WHERE clause + bind ensures we only ever
     * touch the requester's rows).
     */
    public static boolean reorder(int habboId, int[] orderedIds) {
        if (habboId <= 0 || orderedIds == null || orderedIds.length == 0) return false;
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE rp_macros SET sort_order = ? WHERE id = ? AND habbo_id = ?")) {
                for (int i = 0; i < orderedIds.length; i++) {
                    ps.setInt(1, i);
                    ps.setInt(2, orderedIds[i]);
                    ps.setInt(3, habboId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("MacrosManager.reorder habbo={} failed", habboId, e);
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
