package org.pixeltower.rp.fight;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy cache over {@code rp_room_flags}. Rooms without a row use
 * {@link Flags#DEFAULT} (PvP-enabled, everything else future-default).
 * Missing rows dominate cold starts — an absent key is still a real
 * negative result, so we cache {@link Flags#DEFAULT} for them as well
 * and never re-query.
 *
 * Invalidation: staff edits happen via SQL (no AtomCMS housekeeping UI
 * yet), and the cache wipes on plugin re-init. A {@code :rpreload}
 * admin command can call {@link #clear} when it lands.
 */
public final class RoomFlags {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomFlags.class);

    private static final Map<Integer, Flags> CACHE = new ConcurrentHashMap<>();

    private RoomFlags() {}

    public record Flags(boolean noPvp) {
        public static final Flags DEFAULT = new Flags(false);
    }

    public static Flags get(int roomId) {
        return CACHE.computeIfAbsent(roomId, RoomFlags::fetch);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static Flags fetch(int roomId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT no_pvp FROM rp_room_flags WHERE room_id = ?")) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Flags.DEFAULT;
                return new Flags(rs.getBoolean("no_pvp"));
            }
        } catch (SQLException e) {
            LOGGER.error("fetch rp_room_flags failed room={}", roomId, e);
            return Flags.DEFAULT;
        }
    }
}
