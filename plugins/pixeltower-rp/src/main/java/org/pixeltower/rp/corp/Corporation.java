package org.pixeltower.rp.corp;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A corp loaded from {@code rp_corporations} + its rank ladder from
 * {@code rp_corporation_ranks}. The scalar fields are immutable post-load;
 * the rank map is populated during {@link CorporationManager#init()} and
 * then treated as read-only.
 */
public final class Corporation {

    private final int id;
    private final String corpKey;
    private final String name;
    private final Integer hqRoomId;
    private final int paycheckIntervalS;
    private final int stockCapacity;
    private final Map<Integer, CorporationRank> ranksByNum;

    public Corporation(int id, String corpKey, String name, Integer hqRoomId,
                       int paycheckIntervalS, int stockCapacity) {
        this.id = id;
        this.corpKey = corpKey;
        this.name = name;
        this.hqRoomId = hqRoomId;
        this.paycheckIntervalS = paycheckIntervalS;
        this.stockCapacity = stockCapacity;
        this.ranksByNum = new ConcurrentHashMap<>();
    }

    public int getId() { return this.id; }
    public String getCorpKey() { return this.corpKey; }
    public String getName() { return this.name; }
    public Integer getHqRoomId() { return this.hqRoomId; }
    public int getPaycheckIntervalS() { return this.paycheckIntervalS; }
    public int getStockCapacity() { return this.stockCapacity; }

    public Map<Integer, CorporationRank> getRanks() {
        return Collections.unmodifiableMap(this.ranksByNum);
    }

    public Optional<CorporationRank> getRank(int rankNum) {
        return Optional.ofNullable(this.ranksByNum.get(rankNum));
    }

    void addRank(CorporationRank rank) {
        this.ranksByNum.put(rank.getRankNum(), rank);
    }
}
