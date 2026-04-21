package org.pixeltower.rp.economy.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Shared location check for bank commands that should only run at an ATM.
 * Reads {@code rp.bank.atm_room_ids} from {@code emulator_settings} — a
 * comma-separated list of room IDs. An empty list means "no ATM rooms
 * configured yet" and the gate passes globally (fallback so Tier 1 is
 * testable before any bank room exists).
 */
final class AtmGate {

    private AtmGate() {}

    static boolean inAtmRoom(Habbo habbo) {
        String raw = Emulator.getConfig().getValue("rp.bank.atm_room_ids", "").trim();
        if (raw.isEmpty()) return true;

        if (habbo.getRoomUnit() == null || !habbo.getRoomUnit().isInRoom()) return false;
        int currentRoomId = habbo.getHabboInfo().getCurrentRoom() != null
                ? habbo.getHabboInfo().getCurrentRoom().getId()
                : 0;
        if (currentRoomId <= 0) return false;

        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                if (Integer.parseInt(p) == currentRoomId) return true;
            } catch (NumberFormatException ignore) {
                // ignore bad entries, don't let one typo break the gate
            }
        }
        return false;
    }
}
