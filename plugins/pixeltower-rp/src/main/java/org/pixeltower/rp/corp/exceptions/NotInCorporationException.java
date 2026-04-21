package org.pixeltower.rp.corp.exceptions;

/**
 * Thrown when a corp operation references a habbo who has no active
 * {@code rp_corporation_members} row. The {@code who} field distinguishes
 * caller-side ({@code "caller"}) from target-side ({@code "target"}) at the
 * command layer for user-facing messaging.
 */
public class NotInCorporationException extends Exception {

    private final String who;

    public NotInCorporationException(String who) {
        super("not_in_corporation:" + who);
        this.who = who;
    }

    public String getWho() {
        return this.who;
    }
}
