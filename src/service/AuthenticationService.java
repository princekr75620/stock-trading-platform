package service;

import dao.LoginRateLimitDAO;
import exception.AuthenticationThrottledException;
import exception.InvalidLoginException;
import model.SecurityIncident;
import model.User;
import util.PasswordHasher;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Stateless Authentication Service providing:
 * - Non-blocking concurrent BCrypt credential verification
 * - Isolated $LEGACY$ password migration upon successful login
 * - Persistent database-backed rate limiting (normalized identifier + IP) using progressive temporary throttling
 *   with zero permanent account lockout and zero account-existence leakage
 *
 * Session state is owned strictly per HTTP request by HttpSession / ServletBridgeSession;
 * no shared mutable currentUser field exists on this singleton service.
 */
public class AuthenticationService implements IAuthenticationService {

    public static final long DEFAULT_WINDOW_RESET_MS = 15 * 60_000L; // 15 minutes inactivity reset
    public static final long DEFAULT_BASE_BACKOFF_MS = 15_000L;       // 15s base cooldown, exponential progression
    public static final long DEFAULT_MAX_BACKOFF_MS = 15 * 60_000L;  // 15 minutes cap (never permanent lockout)
    public static final int MIN_IP_MAX_ATTEMPTS = 15;

    private final IUserService userService;
    private final ISecurityService securityService;
    private final LoginRateLimitDAO rateLimitDAO;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final long windowResetMs;

    public AuthenticationService(IUserService userService, ISecurityService securityService) {
        this(userService, securityService, new LoginRateLimitDAO(),
                DEFAULT_BASE_BACKOFF_MS, DEFAULT_MAX_BACKOFF_MS, DEFAULT_WINDOW_RESET_MS);
    }

    /**
     * Test/configuration constructor supporting deterministic short backoff durations.
     */
    public AuthenticationService(IUserService userService, ISecurityService securityService, long backoffDurationMs) {
        this(userService, securityService, new LoginRateLimitDAO(),
                backoffDurationMs > 0 ? backoffDurationMs : DEFAULT_BASE_BACKOFF_MS,
                backoffDurationMs > 0 ? Math.max(backoffDurationMs, backoffDurationMs * 4L) : DEFAULT_MAX_BACKOFF_MS,
                backoffDurationMs > 0 ? Math.max(backoffDurationMs * 8L, 10_000L) : DEFAULT_WINDOW_RESET_MS);
    }

    public AuthenticationService(
            IUserService userService,
            ISecurityService securityService,
            LoginRateLimitDAO rateLimitDAO,
            long baseBackoffMs,
            long maxBackoffMs,
            long windowResetMs) {
        this.userService = userService;
        this.securityService = securityService;
        this.rateLimitDAO = rateLimitDAO != null ? rateLimitDAO : new LoginRateLimitDAO();
        this.baseBackoffMs = baseBackoffMs > 0 ? baseBackoffMs : DEFAULT_BASE_BACKOFF_MS;
        this.maxBackoffMs = Math.max(this.baseBackoffMs, maxBackoffMs > 0 ? maxBackoffMs : DEFAULT_MAX_BACKOFF_MS);
        this.windowResetMs = Math.max(this.baseBackoffMs, windowResetMs > 0 ? windowResetMs : DEFAULT_WINDOW_RESET_MS);
    }

    public static String normalizeIdentifier(String rawIdentifier) {
        if (rawIdentifier == null) return "";
        String trimmed = rawIdentifier.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() > 160) {
            trimmed = trimmed.substring(0, 160);
        }
        return trimmed;
    }

    public static String normalizeClientIp(String rawIp) {
        if (rawIp == null) return "";
        String cleaned = rawIp.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) return "";
        if (cleaned.startsWith("[") && cleaned.contains("]")) {
            cleaned = cleaned.substring(1, cleaned.indexOf(']'));
        }
        int zoneIdx = cleaned.indexOf('%');
        if (zoneIdx > 0) {
            cleaned = cleaned.substring(0, zoneIdx);
        }
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        return cleaned;
    }

    private static String maskForLog(String value) {
        if (value == null || value.isEmpty()) return "[empty]";
        if (value.length() <= 2) return "**";
        return value.substring(0, 2) + "***";
    }

    @Override
    public User login(String usernameOrEmail, String password) throws InvalidLoginException {
        return login(usernameOrEmail, password, null);
    }

    @Override
    public User login(String usernameOrEmail, String password, String clientIp) throws InvalidLoginException {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        String normalizedId = normalizeIdentifier(usernameOrEmail);
        String acctKey = "acct:" + normalizedId;
        String normalizedIp = normalizeClientIp(clientIp);
        String ipKey = normalizedIp.isEmpty() ? null : ("ip:" + normalizedIp);

        long nowMs = System.currentTimeMillis();
        boolean rateLimitEnabled = securityService == null || securityService.getSecuritySettings().isAutomaticAccountLockout();
        int maxAccountAttempts = rateLimitEnabled
                ? (securityService != null ? Math.max(1, securityService.getSecuritySettings().getMaxFailedLoginAttempts()) : 5)
                : Integer.MAX_VALUE;
        int maxIpAttempts = rateLimitEnabled ? Math.max(MIN_IP_MAX_ATTEMPTS, maxAccountAttempts * 3) : Integer.MAX_VALUE;

        // 1. Check persistent shared DB temporary throttling BEFORE credential lookup (prevents timing/enumeration divergence)
        if (rateLimitEnabled) {
            try {
                long acctWaitMs = rateLimitDAO.getRemainingThrottleMs(acctKey, windowResetMs, nowMs);
                long ipWaitMs = (ipKey != null) ? rateLimitDAO.getRemainingThrottleMs(ipKey, windowResetMs, nowMs) : 0L;
                long maxWaitMs = Math.max(acctWaitMs, ipWaitMs);
                if (maxWaitMs > 0) {
                    long retryAfterSecs = Math.max(1L, (maxWaitMs + 999L) / 1000L);
                    if (securityService != null) {
                        securityService.recordIncident(new SecurityIncident(
                                "SEC-" + (nowMs % 100000),
                                "LOGIN_THROTTLED",
                                "Temporary authentication cooldown enforced (retryAfter=" + retryAfterSecs + "s)",
                                SecurityIncident.Severity.HIGH,
                                ipKey != null ? normalizedIp : maskForLog(normalizedId)
                        ));
                    }
                    throw new AuthenticationThrottledException(
                            "Too many failed authentication attempts. Please wait and try again later.",
                            retryAfterSecs
                    );
                }
            } catch (AuthenticationThrottledException ate) {
                throw ate;
            } catch (SQLException e) {
                System.err.println("[AuthenticationService] Persistent rate-limit check warning: " + e.getMessage());
            }
        }

        // 2. Look up user directly from authoritative UserService (by userId or email)
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

        // 3. Reject unknown identifier with identical throttling & generic failure message
        if (foundUser == null) {
            recordFailureQuietly(acctKey, ipKey, rateLimitEnabled, maxAccountAttempts, maxIpAttempts, nowMs);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (nowMs % 100000),
                        "FAILED_LOGIN",
                        "Authentication failure on unrecognized identifier",
                        SecurityIncident.Severity.LOW,
                        maskForLog(normalizedId)
                ));
            }
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        // 4. Verify credential (BCrypt strict; isolated $LEGACY$ migration only when tagged)
        String storedPassword = foundUser.getPassword();
        boolean passwordValid = false;

        if (PasswordHasher.isBCryptHash(storedPassword)) {
            passwordValid = PasswordHasher.verifyPassword(password, storedPassword);
        } else if (PasswordHasher.isLegacyMarkedPassword(storedPassword)) {
            passwordValid = PasswordHasher.verifyLegacyPassword(password, storedPassword);
            if (passwordValid) {
                try {
                    String bcryptHash = PasswordHasher.hashPassword(password);
                    boolean migrated = userService.migrateLegacyPassword(foundUser.getUserId(), storedPassword, bcryptHash);
                    if (migrated) {
                        foundUser.setPassword(bcryptHash);
                        System.out.println("[AuthenticationService] Migrated explicitly marked legacy account to BCrypt hash.");
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
            recordFailureQuietly(acctKey, ipKey, rateLimitEnabled, maxAccountAttempts, maxIpAttempts, nowMs);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (nowMs % 100000),
                        "FAILED_PASSWORD",
                        "Authentication credential mismatch",
                        SecurityIncident.Severity.MEDIUM,
                        maskForLog(foundUser.getUserId())
                ));
            }
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        // 5. Clear failure counters upon verified authentication (keeps other users on shared IP unaffected once acct resets)
        resetFailedAttempts(normalizedId);
        if (ipKey != null) {
            try {
                rateLimitDAO.reset(ipKey);
            } catch (SQLException e) {
                System.err.println("[AuthenticationService] IP rate-limit clear warning: " + e.getMessage());
            }
        }
        return foundUser;
    }

    private void recordFailureQuietly(
            String acctKey,
            String ipKey,
            boolean rateLimitEnabled,
            int maxAccountAttempts,
            int maxIpAttempts,
            long nowMs) {
        if (!rateLimitEnabled) return;
        try {
            rateLimitDAO.recordFailure(acctKey, "ACCOUNT", maxAccountAttempts, baseBackoffMs, maxBackoffMs, windowResetMs, nowMs);
            if (ipKey != null) {
                rateLimitDAO.recordFailure(ipKey, "IP", maxIpAttempts, baseBackoffMs, maxBackoffMs, windowResetMs, nowMs);
            }
        } catch (SQLException e) {
            System.err.println("[AuthenticationService] Persistent rate-limit write warning: " + e.getMessage());
        }
    }

    /**
     * Stateless web auth no-op; active session invalidation occurs on HttpSession.
     */
    @Override
    public void logout() {
        // Stateless: HTTP sessions are managed per-request via HttpSession / ServletBridgeSession
    }

    @Override
    public User getCurrentUser() {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public int getFailedAttempts(String identifier) {
        String norm = normalizeIdentifier(identifier);
        if (norm.isEmpty()) return 0;
        try {
            return rateLimitDAO.getActiveAttemptCount("acct:" + norm, windowResetMs, System.currentTimeMillis());
        } catch (SQLException e) {
            System.err.println("[AuthenticationService] Failed to query attempt count: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void resetFailedAttempts(String identifier) {
        String norm = normalizeIdentifier(identifier);
        if (norm.isEmpty()) return;
        try {
            rateLimitDAO.reset("acct:" + norm);
        } catch (SQLException e) {
            System.err.println("[AuthenticationService] Failed to reset attempt count: " + e.getMessage());
        }
    }
}
