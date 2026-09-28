package exception;

/**
 * Thrown when a queried stock symbol does not exist in the exchange registry.
 */
public class StockNotFoundException extends TradingPlatformException {
    public StockNotFoundException(String message) {
        super(message);
    }
}
