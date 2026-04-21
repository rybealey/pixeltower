package org.pixeltower.rp.core;

import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;

/**
 * Shared helpers for how RP-plugin commands emit chat. Centralises the
 * "what does a Pixeltower action emote look like?" decision — so every
 * new :command that describes an in-character action (balance check,
 * reaching for a gun, checking a watch, etc.) looks identical across
 * the server.
 *
 * RP emote convention on Pixeltower:
 *   - rendered as a shout (Habbo's shift+enter equivalent, bold text)
 *   - yellow bubble (RoomChatMessageBubbles.YELLOW)
 *   - wrapped in asterisks by the caller
 * Falls back to a private whisper when the caller isn't in a room.
 */
public final class RpChat {

    private RpChat() {}

    /**
     * Emit {@code text} as a public RP action emote from {@code habbo}. The
     * caller supplies the raw string including its asterisk wrapping so
     * this helper stays generic (not every emote uses {@code *...*} — some
     * like dice roll results want the formatting without asterisks).
     */
    public static void emote(Habbo habbo, String text) {
        if (habbo.getRoomUnit() != null && habbo.getRoomUnit().isInRoom()) {
            habbo.shout(text, RoomChatMessageBubbles.YELLOW);
        } else {
            habbo.whisper(text, RoomChatMessageBubbles.ALERT);
        }
    }

    /**
     * Same as {@link #emote} but on the STAFF bubble (style id 23). Used for
     * actions performed by staff (e.g. :award) where the emote text already
     * includes the admin's name. The companion Nitro patch
     * ({@code nitro-patches/action-emote.patch}) detects STAFF-styled,
     * asterisk-wrapped messages and renders them without the username
     * splice — so the text is displayed verbatim.
     */
    public static void staffEmote(Habbo habbo, String text) {
        if (habbo.getRoomUnit() != null && habbo.getRoomUnit().isInRoom()) {
            habbo.shout(text, RoomChatMessageBubbles.STAFF);
        } else {
            habbo.whisper(text, RoomChatMessageBubbles.STAFF);
        }
    }

    /**
     * Show the target an info-bubble notification — the centered green
     * exclamation popup Nitro renders for administrative transient alerts.
     * Only visible to the target; other players in the room see nothing.
     *
     * Backed by Arcturus's {@link BubbleAlertComposer} with the
     * {@code admin.transient} key (auto-dismissing style). Safe no-op when
     * the target has no active gameclient (offline).
     */
    public static void infoBubble(Habbo target, String message) {
        if (target == null || target.getClient() == null) return;
        target.getClient().sendResponse(new BubbleAlertComposer("admin.transient", message));
    }
}
