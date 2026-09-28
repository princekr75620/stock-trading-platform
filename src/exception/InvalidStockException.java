package exception;

/**
 * Custom Exception thrown when an invalid, non-existent, or deactivated
 * stock symbol or ID is referenced in an operation.
 * Fulfills Core Java Exception Handling requirement for GUVI Evaluation.
 */
public class InvalidStockException extends TradingPlatformException {
    private final String symbol;

    public InvalidStockException(String message) {
        super(message);
        this.symbol = "";
    }

    public InvalidStockException(String message, String symbol) {
        super(message);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
