package org.pixeltower.rp.fight;

/**
 * Unordered identity key for an engagement — two players in the same
 * room match regardless of who swung first. Normalising via
 * {@code (min, max)} means {@code A.hit(B)} and a subsequent {@code
 * B.hit(A)} land on the same {@link Engagement} row.
 */
public record FightKey(int lowId, int highId, int roomId) {

    public static FightKey of(int habboA, int habboB, int roomId) {
        int lo = Math.min(habboA, habboB);
        int hi = Math.max(habboA, habboB);
        return new FightKey(lo, hi, roomId);
    }

    public boolean involves(int habboId) {
        return habboId == lowId || habboId == highId;
    }
}
