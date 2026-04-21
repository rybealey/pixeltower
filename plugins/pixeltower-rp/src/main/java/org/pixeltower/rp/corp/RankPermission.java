package org.pixeltower.rp.corp;

/**
 * Corporate rank permission bits. Matches the bitmap documented in
 * {@code sql/V001__tier1_stats.sql} lines 9–19 and seeded into
 * {@code rp_corporation_ranks.permissions} as a {@code BIGINT}.
 */
public enum RankPermission {
    CAN_HIRE(1),
    CAN_FIRE(2),
    CAN_PROMOTE(4),
    CAN_DEMOTE(8),
    CAN_VIEW_ROSTER(16),
    CAN_VIEW_STOCK(32),
    CAN_VIEW_LEDGER(64),
    CAN_ADJUST_SALARIES(128),
    CAN_BROADCAST(256),
    CAN_WITHDRAW_STOCK(512);

    private final long bit;

    RankPermission(long bit) {
        this.bit = bit;
    }

    public long getBit() {
        return this.bit;
    }

    public static boolean has(long bitmap, RankPermission p) {
        return (bitmap & p.bit) != 0;
    }
}
