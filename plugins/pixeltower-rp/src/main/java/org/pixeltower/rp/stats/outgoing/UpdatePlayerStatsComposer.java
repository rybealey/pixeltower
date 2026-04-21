package org.pixeltower.rp.stats.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import org.pixeltower.rp.stats.PlayerStats;

/**
 * Pushes a {@link PlayerStats} snapshot to the Pixeltower-patched Nitro
 * client. Header {@link #HEADER_ID} is handled by
 * {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts} (registered
 * alongside the {@code PlaySoundComposer} listener) and flows into the
 * {@code useStats} hook driving {@code StatsHUD.tsx}.
 *
 * Sent once on login today. Tier 2+ stat mutations (damage, XP gain,
 * skill spends) will re-send the composer each time so the HUD re-renders.
 */
public class UpdatePlayerStatsComposer extends MessageComposer {

    public static final int HEADER_ID = 6501;

    private final PlayerStats stats;

    public UpdatePlayerStatsComposer(PlayerStats stats) {
        this.stats = stats;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
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
