package org.pixeltower.rp.economy;

/**
 * Thrown by {@link MoneyLedger#debit} / {@link MoneyLedger#transfer} when
 * the payer's balance is lower than the requested amount.
 */
public class InsufficientFundsException extends Exception {

    private final int habboId;
    private final int requestedAmount;

    public InsufficientFundsException(int habboId, int requestedAmount) {
        super("habbo " + habboId + " has insufficient funds for " + requestedAmount);
        this.habboId = habboId;
        this.requestedAmount = requestedAmount;
    }

    public int getHabboId() {
        return habboId;
    }

    public int getRequestedAmount() {
        return requestedAmount;
    }
}
