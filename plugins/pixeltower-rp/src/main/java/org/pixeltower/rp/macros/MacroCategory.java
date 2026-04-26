package org.pixeltower.rp.macros;

/**
 * One row from {@code rp_macro_categories} (V023). Categories scope a
 * player's macros into named sets ("fighting", "job", …) and exactly one
 * is active per player; the active set is what
 * {@code MacrosKeybindListener} dispatches against.
 */
public final class MacroCategory {
    private final int id;
    private final int habboId;
    private final String name;
    private final int sortOrder;
    private final boolean isActive;

    public MacroCategory(int id, int habboId, String name, int sortOrder, boolean isActive) {
        this.id = id;
        this.habboId = habboId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
    }

    public int getId() { return id; }
    public int getHabboId() { return habboId; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return isActive; }
}
