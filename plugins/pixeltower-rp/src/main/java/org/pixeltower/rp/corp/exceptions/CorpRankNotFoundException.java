package org.pixeltower.rp.corp.exceptions;

/** Thrown when a referenced rank_num isn't defined for the target corp. */
public class CorpRankNotFoundException extends Exception {

    private final int rankNum;
    private final String corpKey;

    public CorpRankNotFoundException(int rankNum, String corpKey) {
        super("rank_not_found:" + corpKey + "#" + rankNum);
        this.rankNum = rankNum;
        this.corpKey = corpKey;
    }

    public int getRankNum() { return this.rankNum; }
    public String getCorpKey() { return this.corpKey; }
}
