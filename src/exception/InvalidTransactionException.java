package exception;

/**
 * Custom Exception thrown when a trade transaction is invalid, such as
 * non-positive share quantity, selling shares the user does not own,
 * or violating exchange transaction constraints.
 * Fulfills Core Java Exception Handling requirement for GUVI Evaluation.
 */
public class InvalidTransactionException extends TradingPlatformException {
    public InvalidTransactionException(String message) {
        super(message);
    }

    public InvalidTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
