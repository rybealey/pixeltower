package org.pixeltower.rp.functional;

/**
 * Mirrors the {@code trigger_type} ENUM on {@code rp_functional_furniture}.
 * Stored DB values are the lower-case names; {@link #fromDb(String)} resolves
 * them with a default of {@link #WALK_ON} for unknown / null inputs (so a
 * misspelled row doesn't silently disable the furni).
 */
public enum TriggerType {
    WALK_ON,
    WALK_OFF,
    CLICK;

    public static TriggerType fromDb(String value) {
        if (value == null) return WALK_ON;
        return switch (value.toLowerCase()) {
            case "click" -> CLICK;
            case "walk_off" -> WALK_OFF;
            default -> WALK_ON;
        };
    }
}
