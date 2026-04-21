package org.pixeltower.rp.corp.exceptions;

import org.pixeltower.rp.corp.RankPermission;

/** Thrown when the caller lacks the permission bit required for the operation. */
public class CorpPermissionDeniedException extends Exception {

    private final RankPermission required;

    public CorpPermissionDeniedException(RankPermission required) {
        super("missing_permission:" + required.name());
        this.required = required;
    }

    public RankPermission getRequired() {
        return this.required;
    }
}
