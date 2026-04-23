package org.pixeltower.rp.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import org.pixeltower.rp.stats.StatsManager;
import org.pixeltower.rp.stats.outgoing.UpdateTargetStatsComposer;

import java.util.Set;

/**
 * Set-and-push semantics for the client-visible target slot.
 *
 * {@link TargetTracker} is the raw in-memory store; callers that also need
 * the Nitro TargetHUD to reflect the change go through this service. A
 * single helper keeps the click-driven (incoming packet), command-driven
 * ({@code :target <user>}) and any future set-paths from drifting apart on
 * what exactly the client sees.
 *
 * Self-targets are filtered here too — matches {@link TargetTracker#set}'s
 * clear-on-self behaviour so the HUD never shows the caller's own info.
 */
public final class TargetService {

    private TargetService() {}

    public static void setAndPush(Habbo viewer, int targetHabboId) {
        if (viewer == null) return;
        int viewerId = viewer.getHabboInfo().getId();
        TargetTracker.set(viewerId, targetHabboId);

        if (viewerId == targetHabboId) return;
        GameClient client = viewer.getClient();
        if (client == null) return;

        sendSnapshot(client, targetHabboId);
    }

    /**
     * Push a fresh UpdateTargetStatsComposer to every online viewer currently
     * targeting {@code targetHabboId}. Called after any stat mutation so
     * observers' TargetHUDs reflect the change without re-clicking the target.
     * No-op when nobody is watching.
     */
    public static void broadcastStatsUpdate(int targetHabboId) {
        broadcastStatsUpdate(targetHabboId, null);
    }

    /**
     * Same as {@link #broadcastStatsUpdate(int)} but uses {@code figureOverride}
     * instead of the value cached on HabboInfo. Used by the figure-saved hook,
     * which fires on the incoming-packet thread BEFORE HabboInfo.setLook() has
     * committed — reading from HabboInfo there returns the stale figure.
     */
    public static void broadcastStatsUpdate(int targetHabboId, String figureOverride) {
        Set<Integer> viewers = TargetTracker.viewersOf(targetHabboId);
        if (viewers.isEmpty()) return;

        HabboManager hm = Emulator.getGameEnvironment().getHabboManager();
        for (int viewerId : viewers) {
            Habbo viewer = hm.getHabbo(viewerId);
            if (viewer == null) continue;
            GameClient client = viewer.getClient();
            if (client == null) continue;
            sendSnapshot(client, targetHabboId, figureOverride);
        }
    }

    private static void sendSnapshot(GameClient client, int targetHabboId) {
        sendSnapshot(client, targetHabboId, null);
    }

    private static void sendSnapshot(GameClient client, int targetHabboId, String figureOverride) {
        String figure;
        String username;
        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetHabboId);
        if (online != null) {
            figure = online.getHabboInfo().getLook();
            username = online.getHabboInfo().getUsername();
        } else {
            HabboInfo offline = HabboManager.getOfflineHabboInfo(targetHabboId);
            if (offline == null) return;
            figure = offline.getLook();
            username = offline.getUsername();
        }
        final String figureF = figureOverride != null ? figureOverride : figure;
        final String usernameF = username;
        StatsManager.getOrFetch(targetHabboId).ifPresent(stats ->
                client.sendResponse(new UpdateTargetStatsComposer(
                        targetHabboId, figureF, usernameF, stats)));
    }
}
