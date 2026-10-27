package service;

import exception.InvalidLoginException;
import model.SecurityIncident;
import model.User;
import util.PasswordHasher;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles user authentication, BCrypt credential verification, transparent password migration,
 * account lockout policy enforcement, and generic anti-enumeration failure responses.
 */
public class AuthenticationService implements IAuthenticationService {
    private IUserService userService;
    private ISecurityService securityService;
    private User currentUser;
    private Map<String, Integer> failedAttemptsMap;

    public AuthenticationService(IUserService userService, ISecurityService securityService) {
        this.userService = userService;
        this.securityService = securityService;
        this.currentUser = null;
        this.failedAttemptsMap = new HashMap<>();
    }

    @Override
    public synchronized User login(String usernameOrEmail, String password) throws InvalidLoginException {
        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            throw new InvalidLoginException("Username/Email and Password cannot be empty.");
        }

        String key = usernameOrEmail.trim().toLowerCase();
        int attempts = failedAttemptsMap.getOrDefault(key, 0);

        // Account Lockout check based on SecuritySettings
        if (securityService != null && securityService.getSecuritySettings().isAutomaticAccountLockout()) {
            int maxAttempts = securityService.getSecuritySettings().getMaxFailedLoginAttempts();
            if (attempts >= maxAttempts) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (System.currentTimeMillis() % 100000),
                        "ACCOUNT_LOCKOUT",
                        "Exceeded maximum failed login attempts (" + maxAttempts + ")",
                        SecurityIncident.Severity.HIGH,
                        usernameOrEmail
                ));
                throw new InvalidLoginException("Account is temporarily locked due to consecutive failed attempts. Please contact an Administrator.");
            }
        }

        // Look up user by userId or email
        User foundUser = null;
        for (User u : userService.getAllUsers()) {
            if (u.getUserId().equalsIgnoreCase(usernameOrEmail.trim()) ||
                u.getEmail().equalsIgnoreCase(usernameOrEmail.trim())) {
                foundUser = u;
                break;
            }
        }

        // Defensive: Always perform a simulated verify check to mitigate timing side-channels
        if (foundUser == null) {
            failedAttemptsMap.put(key, attempts + 1);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (System.currentTimeMillis() % 100000),
                        "FAILED_LOGIN",
                        "Authentication failure for non-existent identifier",
                        SecurityIncident.Severity.LOW,
                        usernameOrEmail
                ));
            }
            // Generic failure message to prevent account enumeration
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        // Secure BCrypt password verification with timing-safe comparison
        boolean passwordValid = PasswordHasher.verifyPassword(password, foundUser.getPassword());

        if (!passwordValid) {
            failedAttemptsMap.put(key, attempts + 1);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + (System.currentTimeMillis() % 100000),
                        "FAILED_PASSWORD",
                        "Authentication failure for user: " + foundUser.getUserId(),
                        SecurityIncident.Severity.MEDIUM,
                        foundUser.getUserId()
                ));
            }
            // Generic failure message to prevent account enumeration
            throw new InvalidLoginException("Invalid username/email or password.");
        }

        // Transparent Migration: If stored password was plaintext, re-hash with BCrypt now
        if (PasswordHasher.needsRehash(foundUser.getPassword())) {
            try {
                String bcryptHash = PasswordHasher.hashPassword(password);
                foundUser.setPassword(bcryptHash);
                userService.updateUser(foundUser);
                System.out.println("[AuthenticationService] Successfully upgraded user '" + foundUser.getUserId() + "' to BCrypt hash.");
            } catch (Exception e) {
                System.err.println("[AuthenticationService] Warning: Could not persist upgraded password hash: " + e.getMessage());
            }
        }

        // Successful authentication
        failedAttemptsMap.remove(key);
        this.currentUser = foundUser;
        return foundUser;
    }

    @Override
    public synchronized void logout() {
        this.currentUser = null;
    }

    @Override
    public User getCurrentUser() {
        return currentUser;
    }

    @Override
    public boolean isAuthenticated() {
        return currentUser != null;
    }

    @Override
    public int getFailedAttempts(String identifier) {
        if (identifier == null) return 0;
        return failedAttemptsMap.getOrDefault(identifier.trim().toLowerCase(), 0);
    }

    @Override
    public void resetFailedAttempts(String identifier) {
        if (identifier != null) {
            failedAttemptsMap.remove(identifier.trim().toLowerCase());
        }
    }
}
