package exception;

/**
 * Thrown when attempting to register a user with an already existing User ID or email.
 */
public class DuplicateUserException extends TradingPlatformException {
    public DuplicateUserException(String message) {
        super(message);
    }
}
