package exception;

/**
 * Thrown when a Trader attempts to sell more shares of a stock than they currently own.
 */
public class InsufficientHoldingsException extends TradingPlatformException {
    public InsufficientHoldingsException(String message) {
        super(message);
    }
}
