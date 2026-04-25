package org.pixeltower.rp.macros;

/**
 * Single per-player keybind → command binding. Persisted in
 * {@code rp_macros} (V021) and shipped to the client via
 * {@code MacrosListComposer} (header 6553).
 *
 * The {@code keybind} string uses the canonical encoding from
 * {@code nitro/src/api/pixeltower/MacroBindingFormat.ts} — the server
 * treats it as opaque, the client both produces and matches it.
 */
public final class Macro {
    private final int macroId;
    private final int habboId;
    private final String keybind;
    private final String command;
    private final String category;

    public Macro(int macroId, int habboId, String keybind, String command, String category) {
        this.macroId = macroId;
        this.habboId = habboId;
        this.keybind = keybind;
        this.command = command;
        this.category = category;
    }

    public int getMacroId() { return macroId; }
    public int getHabboId() { return habboId; }
    public String getKeybind() { return keybind; }
    public String getCommand() { return command; }
    public String getCategory() { return category; }
}
