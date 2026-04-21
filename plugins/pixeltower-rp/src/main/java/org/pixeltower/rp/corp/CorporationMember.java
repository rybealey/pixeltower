package org.pixeltower.rp.corp;

import java.time.Instant;

/**
 * A single row of {@code rp_corporation_members}. Only {@code rankNum} is
 * mutable post-construction (updated in-place by {@link CorporationManager#promote}).
 */
public final class CorporationMember {

    private final int corpId;
    private final int habboId;
    private volatile int rankNum;
    private final Instant hiredAt;

    public CorporationMember(int corpId, int habboId, int rankNum, Instant hiredAt) {
        this.corpId = corpId;
        this.habboId = habboId;
        this.rankNum = rankNum;
        this.hiredAt = hiredAt;
    }

    public int getCorpId() { return this.corpId; }
    public int getHabboId() { return this.habboId; }
    public int getRankNum() { return this.rankNum; }
    public Instant getHiredAt() { return this.hiredAt; }

    void setRankNum(int rankNum) {
        this.rankNum = rankNum;
    }
}
