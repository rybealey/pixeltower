package org.pixeltower.rp.core.commands.social;

/**
 * {@code :bite <user|x>} — a playful nibble. Both avatars get effect 9 for
 * ten seconds; the room sees a yellow {@code *<caller> playfully bites <target>*}.
 */
public class BiteCommand extends SocialEmoteCommand {

    public BiteCommand() {
        super("bite", 9, "playfully bites %s", "bite");
    }
}
