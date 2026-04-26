package org.pixeltower.rp.macros.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import org.pixeltower.rp.macros.Macro;
import org.pixeltower.rp.macros.MacroCategory;

import java.util.List;

/**
 * Snapshot of every macro the player has saved + the player's category
 * list. Header {@link #HEADER_ID} is consumed by
 * {@code nitro/src/api/pixeltower/PixeltowerSoundMessages.ts} and flows
 * into the {@code useMacros} hook driving both the
 * {@code MacrosWindow.tsx} list and the {@code MacrosKeybindListener.tsx}
 * global dispatcher.
 *
 * Sent in response to a {@code UserMacrosRequestedEvent}, and re-sent
 * after every save/delete/reorder/category mutation so the client never
 * has to merge state on its own — the wire format is always the full
 * current list.
 *
 * Wire format:
 * <pre>
 * int macroCount
 *   for each macro:
 *     int macroId
 *     string keybind   // canonical encoding (see MacroBindingFormat.ts)
 *     string command
 *     string category  // matches a category row's name
 * int categoryCount
 *   for each category:
 *     int id
 *     string name
 *     int sort_order
 *     boolean isActive
 * </pre>
 */
public class MacrosListComposer extends MessageComposer {

    public static final int HEADER_ID = 6553;

    private final List<Macro> macros;
    private final List<MacroCategory> categories;

    public MacrosListComposer(List<Macro> macros, List<MacroCategory> categories) {
        this.macros = macros;
        this.categories = categories;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER_ID);
        this.response.appendInt(this.macros.size());
        for (Macro m : this.macros) {
            this.response.appendInt(m.getMacroId());
            this.response.appendString(m.getKeybind());
            this.response.appendString(m.getCommand());
            this.response.appendString(m.getCategory());
        }
        this.response.appendInt(this.categories.size());
        for (MacroCategory c : this.categories) {
            this.response.appendInt(c.getId());
            this.response.appendString(c.getName());
            this.response.appendInt(c.getSortOrder());
            this.response.appendBoolean(c.isActive());
        }
        return this.response;
    }
}
