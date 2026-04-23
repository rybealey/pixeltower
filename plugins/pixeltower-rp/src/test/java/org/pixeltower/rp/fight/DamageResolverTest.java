package org.pixeltower.rp.fight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageResolverTest {

    private static final double VARIANCE  = 0.20;
    private static final int    BASE      = 6;
    private static final double PER_POINT = 0.04;
    private static final double FLOOR     = 0.40;

    @Test
    void clampsToMinimumOneWhenRawWouldFloorToZero() {
        // base=0, hit=0, any roll → raw=0 → final clamps to 1 (raw stays at 0).
        DamageResolver.Result r = DamageResolver.computeWithRoll(
                /* hitSkill */ 0, /* endSkill */ 0, VARIANCE,
                /* baseDamage */ 0, PER_POINT, FLOOR,
                /* roll01 */ 0.5);
        assertEquals(0, r.rawDamage());
        assertEquals(1, r.finalDamage());
    }

    @Test
    void rollZeroProducesMinVarianceBound() {
        // roll01=0 → rollFactor = 1 - variance = 0.80
        // base=6+10=16, raw=12.8 → rawInt=13
        // mitigation=max(0.40, 1 - 1*0.04)=0.96, mitigated=12.288 → round 12
        DamageResolver.Result r =
                DamageResolver.computeWithRoll(10, 1, VARIANCE, BASE, PER_POINT, FLOOR, 0.0);
        assertEquals(13, r.rawDamage());
        assertEquals(12, r.finalDamage());
    }

    @Test
    void rollOneProducesMaxVarianceBound() {
        // roll01=1 → rollFactor = 1 + variance = 1.20
        // base=16, raw=19.2 → rawInt=19
        // mitigation=0.96, mitigated=18.432 → round 18
        DamageResolver.Result r =
                DamageResolver.computeWithRoll(10, 1, VARIANCE, BASE, PER_POINT, FLOOR, 1.0);
        assertEquals(19, r.rawDamage());
        assertEquals(18, r.finalDamage());
    }

    @Test
    void rollHalfProducesNominalExpectation() {
        // roll01=0.5 → rollFactor=1.0 → raw=base*1.0
        // hit=10, end=1 → base=16, raw=16, mitigation=0.96, mitigated=15.36 → round 15
        DamageResolver.Result r =
                DamageResolver.computeWithRoll(10, 1, VARIANCE, BASE, PER_POINT, FLOOR, 0.5);
        assertEquals(16, r.rawDamage());
        assertEquals(15, r.finalDamage());
    }

    @Test
    void enduranceFloorCapsMitigationAtFloor() {
        // endSkill=100 → raw mitigation would be 1-100*0.04 = -3; floor clamps to 0.40.
        // hit=10, base=16, raw=16*1.0=16, mitigated=6.4 → round 6.
        DamageResolver.Result r =
                DamageResolver.computeWithRoll(10, 100, VARIANCE, BASE, PER_POINT, FLOOR, 0.5);
        assertEquals(16, r.rawDamage());
        assertEquals(6, r.finalDamage());
    }

    @Test
    void zeroEnduranceAppliesNoMitigation() {
        // endSkill=0 → mitigation=1.0 exactly.
        // hit=10, base=16, roll=0.5 → rollFactor=1.0 → 16 → 16
        DamageResolver.Result r =
                DamageResolver.computeWithRoll(10, 0, VARIANCE, BASE, PER_POINT, FLOOR, 0.5);
        assertEquals(16, r.rawDamage());
        assertEquals(16, r.finalDamage());
    }

    @Test
    void damageStaysWithinVarianceBoundsAcrossRollRange() {
        // Sweep 101 evenly-spaced rolls and assert each result sits in
        // [floor_dmg, ceil_dmg] bracketed by the ±variance endpoints.
        int hit = 8, end = 5;
        int lo = DamageResolver.computeWithRoll(
                hit, end, VARIANCE, BASE, PER_POINT, FLOOR, 0.0).finalDamage();
        int hi = DamageResolver.computeWithRoll(
                hit, end, VARIANCE, BASE, PER_POINT, FLOOR, 1.0).finalDamage();
        assertTrue(lo <= hi, "min roll damage must not exceed max roll damage");
        for (int i = 0; i <= 100; i++) {
            double r = i / 100.0;
            int d = DamageResolver.computeWithRoll(
                    hit, end, VARIANCE, BASE, PER_POINT, FLOOR, r).finalDamage();
            assertTrue(d >= lo && d <= hi,
                    "roll " + r + " yielded " + d + ", outside [" + lo + "," + hi + "]");
            assertTrue(d >= 1, "roll " + r + " yielded sub-minimum damage " + d);
        }
    }

    @Test
    void higherHitSkillMonotonicallyIncreasesDamage() {
        // Same roll, same endurance — more hit skill never reduces damage.
        int prev = 0;
        for (int hit = 0; hit <= 30; hit++) {
            int d = DamageResolver.computeWithRoll(
                    hit, 5, VARIANCE, BASE, PER_POINT, FLOOR, 0.5).finalDamage();
            assertTrue(d >= prev, "damage regressed at hit=" + hit + ": " + prev + " → " + d);
            prev = d;
        }
    }

    @Test
    void higherEnduranceMonotonicallyDecreasesDamageUntilFloor() {
        // Same roll, same hit — more endurance never increases damage.
        int prev = Integer.MAX_VALUE;
        for (int end = 0; end <= 30; end++) {
            int d = DamageResolver.computeWithRoll(
                    10, end, VARIANCE, BASE, PER_POINT, FLOOR, 0.5).finalDamage();
            assertTrue(d <= prev, "damage regressed upward at end=" + end + ": " + prev + " → " + d);
            prev = d;
        }
    }
}
