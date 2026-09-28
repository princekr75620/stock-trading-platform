package exception;

/**
 * Thrown when a queried User ID or email is not found in the user registry.
 */
public class UserNotFoundException extends TradingPlatformException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
