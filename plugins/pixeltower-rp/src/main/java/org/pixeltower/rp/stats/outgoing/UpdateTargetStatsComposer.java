package org.pixeltower.rp.stats.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import org.pixeltower.rp.stats.PlayerStats;

/**
 * Pushes a locked-target stat snapshot to the Pixeltower-patched Nitro
 * client. Header {@link #HEADER_ID} is paired with {@code useTarget} and
 * {@code TargetHUD.tsx} on the client side.
 *
 * Fired from {@code PixeltowerRP.onUserProfileCardViewed} whenever a
 * user views another user's profile (same gate as the existing
 * {@code TargetTracker}). Self-views are filtered at the event handler
 * so the target slot never shows the caller's own info.
 *
 * Payload order: habboId (int), figure (string), username (string),
 * then the ten stat ints matching {@link UpdatePlayerStatsComposer}.
 */
public class UpdateTargetStatsComposer extends MessageComposer {

    public static final int HEADER_ID = 6502;

    private final int habboId;
    private final String figure;
    private final String username;
    private final PlayerStats stats;

    public UpdateTargetStatsComposer(int habboId, String figure, String username, PlayerStats stats) {
        this.habboId = habboId;
        this.figure = figure != null ? figure : "";
        this.username = username != null ? username : "";
        this.stats = stats;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.habboId);
        this.response.appendString(this.figure);
        this.response.appendString(this.username);
        this.response.appendInt(this.stats.getHp());
        this.response.appendInt(this.stats.getMaxHp());
        this.response.appendInt(this.stats.getEnergy());
        this.response.appendInt(this.stats.getMaxEnergy());
        this.response.appendInt(this.stats.getLevel());
        this.response.appendInt(this.stats.getXp());
        this.response.appendInt(this.stats.getSkillPointsUnspent());
        this.response.appendInt(this.stats.getSkillHit());
        this.response.appendInt(this.stats.getSkillEndurance());
        this.response.appendInt(this.stats.getSkillStamina());
        return this.response;
    }
}
