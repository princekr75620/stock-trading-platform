package exception;

/**
 * Thrown when attempting to purchase more stock than available in the market float.
 */
public class InsufficientStockQuantityException extends TradingPlatformException {
    public InsufficientStockQuantityException(String message) {
        super(message);
    }
}
