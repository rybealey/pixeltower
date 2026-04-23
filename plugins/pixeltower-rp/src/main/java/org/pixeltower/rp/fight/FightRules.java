package org.pixeltower.rp.fight;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.corp.CorporationManager;
import org.pixeltower.rp.corp.CorporationMember;
import org.pixeltower.rp.stats.PlayerStats;
import org.pixeltower.rp.stats.StatsManager;

import java.util.Optional;

/**
 * Engagement predicate: evaluate every precondition that must hold
 * before {@link FightService#hit} applies damage. Returns
 * {@link Optional#empty()} on allowed; otherwise an
 * {@link Optional#of(Object)} with a human-readable reason the caller
 * can whisper directly to the attacker.
 *
 * Preconditions checked (order matters — cheapest rejections first):
 *
 * <ol>
 *   <li>no self-target</li>
 *   <li>both habbos in a room, same room</li>
 *   <li>tile distance within {@code rp.fight.range_tiles}</li>
 *   <li>safe zone: target room's {@code rp_room_flags.no_pvp=0}</li>
 *   <li>attacker / defender both alive (HP &gt; 0)</li>
 *   <li>corp fratricide rule ({@code rp.fight.allow_corp_fratricide})</li>
 *   <li>attacker has ≥ {@code rp.fight.energy_per_hit} energy</li>
 *   <li>attacker not on per-swing cooldown
 *       ({@code rp.fight.cooldown_ms}; tracked in
 *       {@link FightService#getLastSwingAtMs})</li>
 * </ol>
 */
public final class FightRules {

    private FightRules() {}

    public static Optional<String> canEngage(Habbo attacker, Habbo defender) {
        if (attacker == null || defender == null) {
            return Optional.of("Target not found.");
        }
        int attId = attacker.getHabboInfo().getId();
        int defId = defender.getHabboInfo().getId();
        if (attId == defId) {
            return Optional.of("You can't hit yourself.");
        }

        Room attRoom = attacker.getHabboInfo().getCurrentRoom();
        Room defRoom = defender.getHabboInfo().getCurrentRoom();
        if (attRoom == null || defRoom == null) {
            return Optional.of("You need to be in the same room as your target.");
        }
        if (attRoom.getId() != defRoom.getId()) {
            return Optional.of(defender.getHabboInfo().getUsername() + " isn't in this room.");
        }

        int maxTiles = Emulator.getConfig().getInt("rp.fight.range_tiles", 1);
        if (!FightRange.withinRange(attacker, defender, maxTiles)) {
            return Optional.of(defender.getHabboInfo().getUsername() + " is out of range.");
        }

        if (RoomFlags.get(attRoom.getId()).noPvp()) {
            return Optional.of("This is a safe zone — you can't fight here.");
        }

        Optional<PlayerStats> attStats = StatsManager.get(attId);
        Optional<PlayerStats> defStats = StatsManager.get(defId);
        if (attStats.isEmpty() || defStats.isEmpty()) {
            return Optional.of("Stats not ready — try again in a moment.");
        }
        if (attStats.get().getHp() <= 0) {
            return Optional.of("You're downed — you can't fight.");
        }
        if (defStats.get().getHp() <= 0) {
            return Optional.of(defender.getHabboInfo().getUsername() + " is already downed.");
        }

        boolean allowFratricide =
                "true".equalsIgnoreCase(Emulator.getConfig().getValue(
                        "rp.fight.allow_corp_fratricide", "false"));
        if (!allowFratricide && sameCorp(attId, defId)) {
            return Optional.of("You can't fight a member of your own corp.");
        }

        int energyPerHit = Emulator.getConfig().getInt("rp.fight.energy_per_hit", 10);
        if (attStats.get().getEnergy() < energyPerHit) {
            return Optional.of("You're out of energy — rest a moment.");
        }

        long cooldownMs = Emulator.getConfig().getInt("rp.fight.cooldown_ms", 1000);
        long lastSwing = FightService.getLastSwingAtMs(attId);
        long now = System.currentTimeMillis();
        if (lastSwing > 0 && now - lastSwing < cooldownMs) {
            long remaining = cooldownMs - (now - lastSwing);
            return Optional.of("Too soon — swing cooldown " + remaining + "ms left.");
        }

        return Optional.empty();
    }

    private static boolean sameCorp(int a, int b) {
        Optional<CorporationMember> ma = CorporationManager.getMembership(a);
        Optional<CorporationMember> mb = CorporationManager.getMembership(b);
        return ma.isPresent() && mb.isPresent()
                && ma.get().getCorpId() == mb.get().getCorpId();
    }
}
