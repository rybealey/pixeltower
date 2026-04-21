package org.pixeltower.rp.core;

/**
 * Thrown by {@link TargetResolver} when a command arg of {@code "x"}
 * couldn't be resolved because the caller has no current target. The
 * message is designed to be whispered straight to the player.
 */
public class NoTargetException extends Exception {
    public NoTargetException() {
        super("You don't have a target. Click a user or use :target <name>.");
    }
}
