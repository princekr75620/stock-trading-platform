package exception;

/**
 * Thrown when user login credentials fail validation.
 */
public class InvalidLoginException extends TradingPlatformException {
    public InvalidLoginException(String message) {
        super(message);
    }
}
