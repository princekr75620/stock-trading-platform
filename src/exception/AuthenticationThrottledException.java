package exception;

/**
 * Thrown when authentication requests exceed the progressive temporary throttling threshold
 * for an identifier or client IP. Extends InvalidLoginException so service callers
 * handling login failures catch it uniformly while servlets may emit HTTP 429 cleanly
 * without revealing account existence or permanent status.
 */
public class AuthenticationThrottledException extends InvalidLoginException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public AuthenticationThrottledException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
