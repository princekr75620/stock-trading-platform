package exception;

/**
 * Base custom exception for the Online Stock Trading Platform.
 */
public class TradingPlatformException extends Exception {
    public TradingPlatformException(String message) {
        super(message);
    }

    public TradingPlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
