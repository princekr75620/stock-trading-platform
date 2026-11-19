package service;

import exception.InvalidLoginException;
import model.SecurityIncident;
import model.User;
import util.PasswordHasher;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateless Authentication Service providing:
 * - Non-blocking concurrent BCrypt credential verification
 * - Isolated $LEGACY$ password migration upon successful login
 * - Bounded, concurrency-safe failed-attempt tracking with automatic time-based lockout expiry
 * - Generic anti-enumeration failure responses
 *
 * Session state is owned strictly per HTTP request by HttpSession / ServletBridgeSession;
 * no shared mutable currentUser field exists on this singleton service.
 */
public class AuthenticationService implements IAuthenticationService {

    public static final long DEFAULT_LOCKOUT_DURATION_MS = 15 * 60_000L; // 15 minutes
    public static final int MAX_TRACKED_IDENTIFIERS = 10_000;

    private static final class FailedAttemptState {
        final int count;
        final long lastFailureAtMs;
        final long lockedUntilMs;

        FailedAttemptState(int count, long lastFailureAtMs, long lockedUntilMs) {
            this.count = count;
            this.lastFailureAtMs = lastFailureAtMs;
            this.lockedUntilMs = lockedUntilMs;
        }
    }

    private final IUserService userService;
    private final ISecurityService securityService;
    private final long lockoutDurationMs;
    private final ConcurrentHashMap<String, FailedAttemptState> failedAttemptsMap = new ConcurrentHashMap<>();

    public AuthenticationService(IUserService userService, ISecurityService securityService) {
        this(userService, securityService, DEFAULT_LOCKOUT_DURATION_MS);
    }

    public AuthenticationService(IUserService userService, ISecurityService securityService, long lockoutDurationMs) {
        this.userService = userService;
        this.securityService = securityService;
        this.lockoutDurationMs = lockoutDurationMs > 0 ? lockoutDurationMs : DEFAULT_LOCKOUT_DURATION_MS;
    }

    private void pruneExpiredOrExcessEntries(long nowMs) {
        if (failedAttemptsMap.size() < MAX_TRACKED_IDENTIFIERS) {
            return;
        }
        Iterator<Map.Entry<String, FailedAttemptState>> it = failedAttemptsMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FailedAttemptState> entry = it.next();
            FailedAttemptState state = entry.getValue();
            boolean expired = (nowMs - state.lastFailureAtMs) >= lockoutDurationMs;
            if (expired || failedAttemptsMap.size() >= MAX_TRACKED_IDENTIFIERS) {
                it.remove();
            }
        }
    }

    private boolean isCurrentlyLocked(String key, int maxAttempts, long nowMs) {
        FailedAttemptState state = failedAttemptsMap.get(key);
        if (state == null) return false;
        if (state.lockedUntilMs > 0) {
            if (nowMs < state.lockedUntilMs) {
                return true;
            }
            // Lockout window elapsed cleanly: expire previous counter automatically
            failedAttemptsMap.remove(key, state);
            return false;
        }
        if ((nowMs - state.lastFailureAtMs) >= lockoutDurationMs) {
            failedAttemptsMap.remove(key, state);
            return false;
        }
        return state.count >= maxAttempts;
    }

    private void recordFailureAttempt(String key, boolean lockoutEnabled, int maxAttempts, long nowMs) {
        pruneExpiredOrExcessEntries(nowMs);
        failedAttemptsMap.compute(key, (k, prev) -> {
            int nextCount;
            if (prev == null || (nowMs - prev.lastFailureAtMs) >= lockoutDurationMs
                    || (prev.lockedUntilMs > 0 && nowMs >= prev.lockedUntilMs)) {
                nextCount = 1;
            } else {
                nextCount = prev.count + 1;
            }
            long lockedUntil = (lockoutEnabled && nextCount >= maxAttempts) ? (nowMs + lockoutDurationMs) : 0L;
            return new FailedAttemptState(nextCount, nowMs, lockedUntil);
        });
    }

    @Override
    public User login(String usernameOrEmail, String password) throws InvalidLoginException {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            throw new InvalidLoginException("Username/Email and Password cannot be empty.");
        }

        String key = usernameOrEmail.trim().toLowerCase(java.util.Locale.ROOT);
        long nowMs = System.currentTimeMillis();
        boolean lockoutEnabled = securityService != null && securityService.getSecuritySettings().isAutomaticAccountLockout();
        int maxAttempts = lockoutEnabled ? Math.max(1, securityService.getSecuritySettings().getMaxFailedLoginAttempts()) : Integer.MAX_VALUE;

        // Time-bounded Account Lockout check
        if (lockoutEnabled && isCurrentlyLocked(key, maxAttempts, nowMs)) {
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (nowMs % 100000),
                        "ACCOUNT_LOCKOUT",
                        "Exceeded maximum failed login attempts (" + maxAttempts + ")",
                        SecurityIncident.Severity.HIGH,
                        usernameOrEmail
                ));
            }
            throw new InvalidLoginException("Account is temporarily locked due to consecutive failed attempts. Please try again later or contact an Administrator.");
        }

        // Look up user directly from authoritative UserService (by userId or email)
        String identifier = usernameOrEmail.trim();
        User foundUser = null;
        try {
            foundUser = userService.getUserById(identifier);
        } catch (exception.UserNotFoundException ignored) {}
        if (foundUser == null) {
            try {
                foundUser = userService.getUserByEmail(identifier);
            } catch (exception.UserNotFoundException ignored) {}
        }

        // Defensive: Reject unknown user with generic message
        if (foundUser == null) {
            recordFailureAttempt(key, lockoutEnabled, maxAttempts, nowMs);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (nowMs % 100000),
                        "FAILED_LOGIN",
                        "Authentication failure for non-existent identifier",
                        SecurityIncident.Severity.LOW,
                        usernameOrEmail
                ));
            }
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        String storedPassword = foundUser.getPassword();
        boolean passwordValid = false;

        if (PasswordHasher.isBCryptHash(storedPassword)) {
            // Standard BCrypt verification (never falls back to plaintext)
            passwordValid = PasswordHasher.verifyPassword(password, storedPassword);
        } else if (PasswordHasher.isLegacyMarkedPassword(storedPassword)) {
            // Isolated temporary legacy migration path ONLY for explicitly $LEGACY$-marked accounts
            passwordValid = PasswordHasher.verifyLegacyPassword(password, storedPassword);
            if (passwordValid) {
                try {
                    String bcryptHash = PasswordHasher.hashPassword(password);
                    boolean migrated = userService.migrateLegacyPassword(foundUser.getUserId(), storedPassword, bcryptHash);
                    if (migrated) {
                        foundUser.setPassword(bcryptHash);
                        System.out.println("[AuthenticationService] Migrated explicitly marked legacy account '" + foundUser.getUserId() + "' to BCrypt hash.");
                    } else {
                        foundUser = userService.getUserById(foundUser.getUserId());
                        passwordValid = PasswordHasher.verifyPassword(password, foundUser.getPassword());
                    }
                } catch (Exception e) {
                    System.err.println("[AuthenticationService] Legacy password migration failed: " + e.getMessage());
                    passwordValid = false;
                }
            }
        } else {
            // Unflagged plaintext or invalid hash format is strictly rejected
            passwordValid = false;
        }

        if (!passwordValid) {
            recordFailureAttempt(key, lockoutEnabled, maxAttempts, nowMs);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (nowMs % 100000),
                        "FAILED_PASSWORD",
                        "Authentication failure for user: " + foundUser.getUserId(),
                        SecurityIncident.Severity.MEDIUM,
                        foundUser.getUserId()
                ));
            }
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        // Successful authentication clears tracked failures for this key
        failedAttemptsMap.remove(key);
        return foundUser;
    }

    /**
     * Stateless web auth no-op; active session invalidation occurs on HttpSession.
     */
    @Override
    public void logout() {
        // Stateless: HTTP sessions are managed per-request via HttpSession / ServletBridgeSession
    }

    /**
     * Web auth does not hold global mutable currentUser state across concurrent requests.
     */
    @Override
    public User getCurrentUser() {
        return null;
    }

    /**
     * Web auth does not hold global mutable authentication state across concurrent requests.
     */
    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public int getFailedAttempts(String identifier) {
        if (identifier == null) return 0;
        String key = identifier.trim().toLowerCase(java.util.Locale.ROOT);
        FailedAttemptState state = failedAttemptsMap.get(key);
        if (state == null) return 0;
        long nowMs = System.currentTimeMillis();
        if ((nowMs - state.lastFailureAtMs) >= lockoutDurationMs
                || (state.lockedUntilMs > 0 && nowMs >= state.lockedUntilMs)) {
            failedAttemptsMap.remove(key, state);
            return 0;
        }
        return state.count;
    }

    @Override
    public void resetFailedAttempts(String identifier) {
        if (identifier != null) {
            failedAttemptsMap.remove(identifier.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
