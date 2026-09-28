package service;

import exception.InvalidLoginException;
import model.User;

/**
 * Interface defining authentication and session operations.
 */
public interface IAuthenticationService {
    User login(String usernameOrEmail, String password) throws InvalidLoginException;
    void logout();
    User getCurrentUser();
    boolean isAuthenticated();
    int getFailedAttempts(String identifier);
    void resetFailedAttempts(String identifier);
}
