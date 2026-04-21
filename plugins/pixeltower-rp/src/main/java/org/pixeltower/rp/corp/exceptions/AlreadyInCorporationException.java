package org.pixeltower.rp.corp.exceptions;

/**
 * Thrown on {@code :hire} when the target already has a membership row.
 * Membership is one-corp-at-a-time (enforced by a UNIQUE index on
 * {@code rp_corporation_members.habbo_id}).
 */
public class AlreadyInCorporationException extends Exception {

    public AlreadyInCorporationException() {
        super("already_in_corporation");
    }
}
