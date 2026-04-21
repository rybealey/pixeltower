package org.pixeltower.rp.corp;

/**
 * A single rung on a corp's rank ladder. Immutable — the only mutable bit of
 * state in the corp tree is the member → rank_num mapping.
 */
public final class CorporationRank {

    private final int rankNum;
    private final String title;
    private final long salary;
    private final long permissions;

    public CorporationRank(int rankNum, String title, long salary, long permissions) {
        this.rankNum = rankNum;
        this.title = title;
        this.salary = salary;
        this.permissions = permissions;
    }

    public int getRankNum() { return this.rankNum; }
    public String getTitle() { return this.title; }
    public long getSalary() { return this.salary; }
    public long getPermissions() { return this.permissions; }

    public boolean has(RankPermission p) {
        return RankPermission.has(this.permissions, p);
    }
}
