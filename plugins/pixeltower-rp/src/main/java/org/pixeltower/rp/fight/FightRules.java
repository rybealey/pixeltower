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
 * {@link Optional#empty()} on allowed, otherwise a {@link Deny} carrying
 * both a typed {@link Deny.Reason} (so callers can branch — the
 * command layer presents {@link Deny.Reason#OUT_OF_RANGE} as a public
 * miss emote instead of a private whisper) and a human-readable
 * message the command layer can surface directly.
 *
 * Check order is from hardest-to-fix to easiest: target unavailable
 * and attacker-downed come first, followed by scene-level gates
 * (safe zone, corp), then attacker-state gates (energy, cooldown),
 * and finally the range check — since moving closer is the fastest
 * remedy, it's the last thing we refuse for.
 */
public final class FightRules {

    private FightRules() {}

    public record Deny(Reason reason, String message) {
        public enum Reason {
            NULL_ARG,
            SELF_TARGET,
            NO_ROOM,
            WRONG_ROOM,
            STATS_NOT_READY,
            ATTACKER_DOWNED,
            DEFENDER_DOWNED,
            SAFE_ZONE,
            CORP_FRATRICIDE,
            NO_ENERGY,
            ON_COOLDOWN,
            OUT_OF_RANGE
        }
    }

    public static Optional<Deny> canEngage(Habbo attacker, Habbo defender) {
        if (attacker == null || defender == null) {
            return Optional.of(new Deny(Deny.Reason.NULL_ARG, "Target not found."));
        }
        int attId = attacker.getHabboInfo().getId();
        int defId = defender.getHabboInfo().getId();
        if (attId == defId) {
            return Optional.of(new Deny(Deny.Reason.SELF_TARGET, "You can't hit yourself."));
        }

        Room attRoom = attacker.getHabboInfo().getCurrentRoom();
        Room defRoom = defender.getHabboInfo().getCurrentRoom();
        if (attRoom == null || defRoom == null) {
            return Optional.of(new Deny(Deny.Reason.NO_ROOM,
                    "You need to be in the same room as your target."));
        }
        if (attRoom.getId() != defRoom.getId()) {
            return Optional.of(new Deny(Deny.Reason.WRONG_ROOM,
                    defender.getHabboInfo().getUsername() + " isn't in this room."));
        }

        Optional<PlayerStats> attStats = StatsManager.get(attId);
        Optional<PlayerStats> defStats = StatsManager.get(defId);
        if (attStats.isEmpty() || defStats.isEmpty()) {
            return Optional.of(new Deny(Deny.Reason.STATS_NOT_READY,
                    "Stats not ready — try again in a moment."));
        }
        if (attStats.get().getHp() <= 0) {
            return Optional.of(new Deny(Deny.Reason.ATTACKER_DOWNED,
                    "You're downed — you can't fight."));
        }
        if (defStats.get().getHp() <= 0) {
            return Optional.of(new Deny(Deny.Reason.DEFENDER_DOWNED,
                    defender.getHabboInfo().getUsername() + " is already downed."));
        }

        if (RoomFlags.get(attRoom.getId()).noPvp()) {
            return Optional.of(new Deny(Deny.Reason.SAFE_ZONE,
                    "This is a safe zone — you can't fight here."));
        }

        boolean allowFratricide =
                "true".equalsIgnoreCase(Emulator.getConfig().getValue(
                        "rp.fight.allow_corp_fratricide", "false"));
        if (!allowFratricide && sameCorp(attId, defId)) {
            return Optional.of(new Deny(Deny.Reason.CORP_FRATRICIDE,
                    "You can't fight a member of your own corp."));
        }

        if (attStats.get().getEnergy() <= 0) {
            return Optional.of(new Deny(Deny.Reason.NO_ENERGY,
                    "You're out of energy — rest a moment."));
        }

        long cooldownMs = Emulator.getConfig().getInt("rp.fight.cooldown_ms", 1000);
        long lastSwing = FightService.getLastSwingAtMs(attId);
        long now = System.currentTimeMillis();
        if (lastSwing > 0 && now - lastSwing < cooldownMs) {
            long remaining = cooldownMs - (now - lastSwing);
            return Optional.of(new Deny(Deny.Reason.ON_COOLDOWN,
                    "Too soon — swing cooldown " + remaining + "ms left."));
        }

        // Range last — moving closer is the easiest fix, and the command
        // layer renders this as a public miss emote instead of a whisper.
        int maxTiles = Emulator.getConfig().getInt("rp.fight.range_tiles", 1);
        if (!FightRange.withinRange(attacker, defender, maxTiles)) {
            return Optional.of(new Deny(Deny.Reason.OUT_OF_RANGE,
                    defender.getHabboInfo().getUsername() + " is out of range."));
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
