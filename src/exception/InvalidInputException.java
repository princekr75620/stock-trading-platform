package exception;

/**
 * Thrown when user input violates validation criteria (e.g. negative numbers, empty strings, invalid formats).
 */
public class InvalidInputException extends TradingPlatformException {
    public InvalidInputException(String message) {
        super(message);
    }
}
