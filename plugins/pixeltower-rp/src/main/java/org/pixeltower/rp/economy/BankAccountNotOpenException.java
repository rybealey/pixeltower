package org.pixeltower.rp.economy;

/**
 * Thrown by {@link BankManager} deposit/withdraw/transfer operations when
 * the subject habbo has no {@code rp_player_bank} row. Players must run
 * {@code :openaccount} first.
 */
public class BankAccountNotOpenException extends Exception {

    private final int habboId;

    public BankAccountNotOpenException(int habboId) {
        super("habbo " + habboId + " has no bank account");
        this.habboId = habboId;
    }

    public int getHabboId() {
        return habboId;
    }
}
