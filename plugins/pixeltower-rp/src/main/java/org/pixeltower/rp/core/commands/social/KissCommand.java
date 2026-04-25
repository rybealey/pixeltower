package org.pixeltower.rp.core.commands.social;

/**
 * {@code :kiss <user|x>} — a quick kiss. Both avatars get effect 9 for seven
 * seconds; the room sees a yellow {@code *<caller> plants a kiss on <target>*}.
 */
public class KissCommand extends SocialEmoteCommand {

    public KissCommand() {
        super("kiss", 9, "plants a kiss on %s", "kiss");
    }
}
