package org.pixeltower.rp.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Single source of truth for "is this user staff?" — a rank
 * comparison against {@code rp.admin.min_rank}. Staff bypass the
 * roleplay gates (chat lock while downed, posture lock while dead)
 * because they need to keep moderating regardless of their in-game
 * RP state; the rank-gated commands themselves still enforce their
 * own permission checks.
 */
public final class StaffGate {

    private StaffGate() {}

    public static boolean isStaff(Habbo habbo) {
        if (habbo == null) return false;
        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        return habbo.getHabboInfo().getRank().getId() >= minRank;
    }
}
