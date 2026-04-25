package org.pixeltower.rp.core.commands.social;

/**
 * {@code :hug <user|x>} — a warm hug. Both avatars get effect 168 for five
 * seconds; the room sees a hearts {@code *<caller> gives <target> a warm hug*}.
 */
public class HugCommand extends SocialEmoteCommand {

    public HugCommand() {
        super("hug", 168, "gives %s a warm hug", "hug");
    }
}
