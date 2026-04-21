package org.pixeltower.rp.core;

/**
 * Thrown by {@link TargetResolver} when a username arg doesn't match any
 * online or offline habbo. Message is whisper-ready.
 */
public class NoSuchUserException extends Exception {
    public NoSuchUserException(String name) {
        super("No such player: " + name);
    }
}
