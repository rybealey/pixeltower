package org.pixeltower.rp.core;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Read/write layer for {@code rp_player_home_position} — the per-user tile +
 * body rotation recorded when they last left their home room. Used by the
 * {@code UserLoginEvent} flow to drop the user back onto the exact tile
 * they were on when they logged off.
 */
public final class HomePositionStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(HomePositionStore.class);

    public record Position(int x, int y, int rotation) {}

    private HomePositionStore() {}

    public static void save(int habboId, int x, int y, int rotation) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rp_player_home_position (habbo_id, x, y, rotation) "
                             + "VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE x = VALUES(x), y = VALUES(y), "
                             + "rotation = VALUES(rotation)")) {
            ps.setInt(1, habboId);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, rotation);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("save home_position failed habbo={} x={} y={} rot={}",
                    habboId, x, y, rotation, e);
        }
    }

    public static Optional<Position> load(int habboId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT x, y, rotation FROM rp_player_home_position WHERE habbo_id = ?")) {
            ps.setInt(1, habboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Position(
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("rotation")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("load home_position failed habbo={}", habboId, e);
        }
        return Optional.empty();
    }
}
