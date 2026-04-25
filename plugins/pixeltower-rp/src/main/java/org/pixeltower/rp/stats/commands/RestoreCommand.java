package org.pixeltower.rp.stats.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import org.pixeltower.rp.core.NoSuchUserException;
import org.pixeltower.rp.core.NoTargetException;
import org.pixeltower.rp.core.RpChat;
import org.pixeltower.rp.core.TargetResolver;
import org.pixeltower.rp.core.TargetResolver.ResolvedTarget;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdatePlayerStatsComposer;

/**
 * {@code :restore <user|x>} — staff full HP + energy refill.
 *
 * Writes {@code hp := max_hp} and {@code energy := max_energy} through
 * {@link StatsManager#restoreStats(int)} (cache + DB), then pushes an
 * {@link UpdatePlayerStatsComposer} so the online target's Stats HUD
 * updates immediately. Works on offline users too — the DB row is the
 * source of truth; the composer push is simply skipped.
 *
 * Gated by {@code rp.admin.min_rank} (default 5), matching {@link
 * org.pixeltower.rp.economy.commands.AwardCommand}.
 */
public class RestoreCommand extends Command {

    public RestoreCommand() {
        super(null, new String[] {"restore"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        Habbo staff = gameClient.getHabbo();

        int minRank = Emulator.getConfig().getInt("rp.admin.min_rank", 5);
        if (staff.getHabboInfo().getRank().getId() < minRank) {
            staff.whisper("You don't have permission to run :restore.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 2) {
            staff.whisper("Usage: :restore <user|x>", RoomChatMessageBubbles.ALERT);
            return true;
        }

        ResolvedTarget resolved;
        try {
            resolved = TargetResolver.resolve(staff, params[1]);
        } catch (NoTargetException | NoSuchUserException e) {
            staff.whisper(e.getMessage(), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!StatsManager.restoreStats(resolved.habboId)) {
            staff.whisper(resolved.username + " doesn't have a stats row yet.",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (resolved.isOnline()) {
            StatsManager.get(resolved.habboId).ifPresent(stats ->
                    resolved.online.getClient().sendResponse(new UpdatePlayerStatsComposer(stats)));
        }

        RpChat.staffEmote(staff, "*" + staff.getHabboInfo().getUsername()
                + " casts a spell, restoring " + resolved.username + " to full strength*");

        if (resolved.isOnline()) {
            resolved.online.whisper("Your energy and health have been restored.",
                    RoomChatMessageBubbles.FRANK);
        }
        return true;
    }
}
