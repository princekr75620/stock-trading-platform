package exception;

/**
 * Thrown when an order submission repeats an already processed idempotency key.
 */
public class DuplicateOrderException extends InvalidTransactionException {
    private static final long serialVersionUID = 1L;
    private final String idempotencyKey;

    public DuplicateOrderException(String message, String idempotencyKey) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
