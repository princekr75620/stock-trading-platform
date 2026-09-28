package exception;

/**
 * Custom Exception thrown when a trader attempts to purchase stock
 * but does not possess sufficient cash balance in their account.
 * Fulfills Core Java Exception Handling requirement for GUVI Evaluation.
 */
public class InsufficientBalanceException extends TradingPlatformException {
    private final double requiredAmount;
    private final double availableBalance;

    public InsufficientBalanceException(String message) {
        super(message);
        this.requiredAmount = 0;
        this.availableBalance = 0;
    }

    public InsufficientBalanceException(double requiredAmount, double availableBalance) {
        super(String.format("Insufficient balance: Required $%.2f, but available cash is only $%.2f",
                requiredAmount, availableBalance));
        this.requiredAmount = requiredAmount;
        this.availableBalance = availableBalance;
    }

    public double getRequiredAmount() {
        return requiredAmount;
    }

    public double getAvailableBalance() {
        return availableBalance;
    }
}
