package service;

import exception.InvalidLoginException;
import model.SecurityIncident;
import model.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles user authentication, credential validation, failed login tracking, and session state.
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

        if (securityService != null && securityService.getSecuritySettings().isAutomaticAccountLockout()) {
            int maxAttempts = securityService.getSecuritySettings().getMaxFailedLoginAttempts();
            if (attempts >= maxAttempts) {
                if (securityService != null) {
                    securityService.recordIncident(new SecurityIncident(
                            "SEC-" + System.currentTimeMillis() % 10000,
                            "ACCOUNT_LOCKOUT",
                            "Exceeded maximum failed login attempts (" + maxAttempts + ")",
                            SecurityIncident.Severity.HIGH,
                            usernameOrEmail
                    ));
                }
                throw new InvalidLoginException("Account is temporarily locked due to " + attempts +
                        " consecutive failed login attempts. Contact an Administrator.");
            }
        }

        User foundUser = null;
        for (User u : userService.getAllUsers()) {
            if (u.getUserId().equalsIgnoreCase(usernameOrEmail.trim()) ||
                u.getEmail().equalsIgnoreCase(usernameOrEmail.trim())) {
                foundUser = u;
                break;
            }
        }

        if (foundUser == null) {
            failedAttemptsMap.put(key, attempts + 1);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + System.currentTimeMillis() % 10000,
                        "FAILED_LOGIN",
                        "Attempted login with unknown username/email: " + usernameOrEmail,
                        SecurityIncident.Severity.LOW,
                        usernameOrEmail
                ));
            }
            throw new InvalidLoginException("Invalid credentials: user not found.");
        }

        if (!foundUser.getPassword().equals(password)) {
            failedAttemptsMap.put(key, attempts + 1);
            if (securityService != null) {
                securityService.recordIncident(new SecurityIncident(
                        "SEC-" + System.currentTimeMillis() % 10000,
                        "FAILED_PASSWORD",
                        "Incorrect password provided for user: " + foundUser.getUserId(),
                        SecurityIncident.Severity.MEDIUM,
                        foundUser.getUserId()
                ));
            }
            throw new InvalidLoginException("Invalid credentials: password does not match.");
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
