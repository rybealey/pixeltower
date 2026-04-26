package org.pixeltower.rp.corp.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import org.pixeltower.rp.corp.Corporation;
import org.pixeltower.rp.corp.CorporationRank;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pushes a snapshot of every corp + its rank ladder + its current member
 * roster (with username + figure for the imager) to the patched Nitro
 * client. Header {@link #HEADER_ID} is handled by
 * {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts} and flows
 * into the {@code useCorporations} hook driving {@code CorporationsWindow.tsx}.
 *
 * Sent in response to a {@code UserCorporationsRequestedEvent}, which
 * fires when the window opens. The roster is public — no per-viewer
 * filtering. Username + figure are looked up via SQL JOIN against the
 * {@code users} table (not the in-memory {@code CorporationManager}, which
 * stores only ids), so offline employees are included.
 *
 * Wire format:
 * <pre>
 * int corpCount
 *   for each corp:
 *     int corpId
 *     string corpKey
 *     string corpName
 *     string badgeCode      // empty string if NULL
 *     int stockCapacity
 *     int rankCount
 *       for each rank:
 *         int rankNum
 *         string rankTitle
 *         int salary
 *     int memberCount
 *       for each member:
 *         int habboId
 *         string username
 *         string figure
 *         int rankNum
 *         boolean isOnline   // true if the Habbo is currently in-game
 * </pre>
 */
public class CorporationsListComposer extends MessageComposer {

    public static final int HEADER_ID = 6505;

    private final List<Corporation> corps;
    private final Map<Integer, List<MemberRow>> membersByCorp;

    public CorporationsListComposer(List<Corporation> corps,
                                    Map<Integer, List<MemberRow>> membersByCorp) {
        this.corps = corps;
        this.membersByCorp = membersByCorp;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.corps.size());
        for (Corporation corp : this.corps) {
            this.response.appendInt(corp.getId());
            this.response.appendString(corp.getCorpKey());
            this.response.appendString(corp.getName());
            this.response.appendString(corp.getBadgeCode() == null ? "" : corp.getBadgeCode());
            this.response.appendInt(corp.getStockCapacity());

            List<CorporationRank> ranks = corp.getRanks().values().stream()
                    .sorted(Comparator.comparingInt(CorporationRank::getRankNum))
                    .toList();
            this.response.appendInt(ranks.size());
            for (CorporationRank rank : ranks) {
                this.response.appendInt(rank.getRankNum());
                this.response.appendString(rank.getTitle());
                this.response.appendInt((int) Math.min(rank.getSalary(), Integer.MAX_VALUE));
            }

            List<MemberRow> members = this.membersByCorp.getOrDefault(corp.getId(), Collections.emptyList());
            this.response.appendInt(members.size());
            for (MemberRow m : members) {
                this.response.appendInt(m.habboId);
                this.response.appendString(m.username);
                this.response.appendString(m.figure);
                this.response.appendInt(m.rankNum);
                this.response.appendBoolean(m.isOnline);
            }
        }
        return this.response;
    }

    /** Single member row, used as the input shape for the composer. */
    public static final class MemberRow {
        public final int habboId;
        public final String username;
        public final String figure;
        public final int rankNum;
        public final boolean isOnline;

        public MemberRow(int habboId, String username, String figure, int rankNum, boolean isOnline) {
            this.habboId = habboId;
            this.username = username;
            this.figure = figure;
            this.rankNum = rankNum;
            this.isOnline = isOnline;
        }
    }
}
