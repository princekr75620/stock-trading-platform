package exception;

/**
 * Thrown when a Trader attempts to sell more shares of a stock than they currently own.
 */
public class InsufficientHoldingsException extends TradingPlatformException {
    private static final long serialVersionUID = 1L;

    public InsufficientHoldingsException(String message) {
        super(message);
    }

    public InsufficientHoldingsException(String symbol, int requestedQuantity, int ownedQuantity) {
        super(String.format("Insufficient holdings for %s: requested to sell %d shares, but only own %d shares.",
                symbol, requestedQuantity, ownedQuantity));
    }
}
