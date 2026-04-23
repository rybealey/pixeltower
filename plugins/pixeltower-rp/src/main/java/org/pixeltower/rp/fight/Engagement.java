package org.pixeltower.rp.fight;

/**
 * Mutable live state of one {@code rp_fights} row. The initiating
 * attacker ({@link #attackerId}) is fixed at row creation; subsequent
 * retaliatory hits from the other party update
 * {@link #defenderHits} / {@link #totalDamageToAttacker} on the same
 * engagement so the DB row accumulates both sides of the exchange.
 *
 * All counter fields are mutated from {@link EngagementRegistry} under
 * the lock implied by each engagement being accessed through
 * a {@link java.util.concurrent.ConcurrentHashMap} compute call — no
 * field is written from two threads concurrently.
 */
public final class Engagement {

    private final long fightRowId;
    private final int attackerId;
    private final int defenderId;
    private final int roomId;

    private volatile long lastHitAtMs;
    private int attackerHits;
    private int defenderHits;
    private int totalDamageToDefender;
    private int totalDamageToAttacker;

    Engagement(long fightRowId, int attackerId, int defenderId, int roomId, long startedAtMs) {
        this.fightRowId = fightRowId;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.roomId = roomId;
        this.lastHitAtMs = startedAtMs;
    }

    public long getFightRowId()        { return fightRowId; }
    public int  getAttackerId()        { return attackerId; }
    public int  getDefenderId()        { return defenderId; }
    public int  getRoomId()            { return roomId; }
    public long getLastHitAtMs()       { return lastHitAtMs; }
    public int  getAttackerHits()      { return attackerHits; }
    public int  getDefenderHits()      { return defenderHits; }
    public int  getTotalDamageToDefender() { return totalDamageToDefender; }
    public int  getTotalDamageToAttacker() { return totalDamageToAttacker; }

    void recordAttackerHit(int finalDamage, long nowMs) {
        this.attackerHits++;
        this.totalDamageToDefender += finalDamage;
        this.lastHitAtMs = nowMs;
    }

    void recordDefenderHit(int finalDamage, long nowMs) {
        this.defenderHits++;
        this.totalDamageToAttacker += finalDamage;
        this.lastHitAtMs = nowMs;
    }
}
