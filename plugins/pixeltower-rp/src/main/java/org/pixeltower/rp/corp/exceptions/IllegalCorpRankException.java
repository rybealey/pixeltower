package org.pixeltower.rp.corp.exceptions;

/**
 * Thrown when a rank transition violates a guardrail — e.g. hiring at or
 * above the caller's rank, promoting downward, firing an equal-or-higher
 * rank. The {@code reason} tag is a short snake_case label the command
 * layer uses to select a user-facing message.
 */
public class IllegalCorpRankException extends Exception {

    private final String reason;

    public IllegalCorpRankException(String reason) {
        super("illegal_rank:" + reason);
        this.reason = reason;
    }

    public String getReason() {
        return this.reason;
    }
}
