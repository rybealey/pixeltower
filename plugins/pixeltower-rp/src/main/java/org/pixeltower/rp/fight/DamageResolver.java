package org.pixeltower.rp.fight;

import com.eu.habbo.Emulator;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure-function damage calculator for the Tier 2 auto-resolver combat loop.
 *
 * <pre>
 *   base     = rp.fight.base_damage + hitSkill
 *   raw      = base * (1 + uniform(-variance, +variance))
 *   mitig    = raw * max(floor, 1 - endSkill * perPoint)
 *   finalDmg = max(1, round(mitig))
 * </pre>
 *
 * Returns both {@code rawDamage} (rounded pre-mitigation) and
 * {@code finalDamage} (post-mitigation, clamped to ≥ 1) so
 * {@code rp_fight_hits} can log the delta mitigation took.
 *
 * Roll-factor arithmetic is split into {@link #computeWithRoll} (takes a
 * pre-rolled {@code double} in [0, 1]) so JUnit can drive it
 * deterministically; {@link #compute} wraps it with
 * {@link ThreadLocalRandom}. All tunables come from {@code emulator_settings}
 * and are re-read per call — live tuning via SQL costs no redeploy.
 */
public final class DamageResolver {

    private DamageResolver() {}

    public record Result(int rawDamage, int finalDamage) {}

    /** Production entrypoint: reads all tunables from {@code rp.fight.*}. */
    public static Result compute(int hitSkill, int endSkill) {
        double variance         = Emulator.getConfig().getDouble("rp.fight.damage_variance",    0.2);
        int    baseDamage       = Emulator.getConfig().getInt(   "rp.fight.base_damage",        6);
        double endurancePerPt   = Emulator.getConfig().getDouble("rp.fight.endurance_per_point",0.04);
        double enduranceFloor   = Emulator.getConfig().getDouble("rp.fight.endurance_floor",    0.40);
        double roll01           = ThreadLocalRandom.current().nextDouble();
        return computeWithRoll(hitSkill, endSkill, variance,
                baseDamage, endurancePerPt, enduranceFloor, roll01);
    }

    /**
     * Deterministic core — {@code roll01} in [0, 1] maps linearly to a roll
     * factor in [1-variance, 1+variance]. Package-visible for tests.
     */
    static Result computeWithRoll(int hitSkill, int endSkill, double variance,
                                  int baseDamage, double endurancePerPoint,
                                  double enduranceFloor, double roll01) {
        double rollFactor = 1.0 + (roll01 * 2.0 - 1.0) * variance;
        int    base       = baseDamage + hitSkill;
        double raw        = base * rollFactor;
        double mitigation = Math.max(enduranceFloor, 1.0 - endSkill * endurancePerPoint);
        double mitigated  = raw * mitigation;
        int rawInt   = Math.max(0, (int) Math.round(raw));
        int finalInt = Math.max(1, (int) Math.round(mitigated));
        return new Result(rawInt, finalInt);
    }
}
