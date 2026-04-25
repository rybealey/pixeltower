package org.pixeltower.rp.core.commands.social;

/**
 * {@code :holdhands <user|x>} — reach out and take their hand. Both avatars
 * get effect 9 for five seconds; the room sees a hearts
 * {@code *<caller> takes <target>'s hand*}.
 */
public class HoldHandsCommand extends SocialEmoteCommand {

    public HoldHandsCommand() {
        super("holdhands", 9, "takes %s's hand", "hold hands with");
    }
}
