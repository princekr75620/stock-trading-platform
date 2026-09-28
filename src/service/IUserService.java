package service;

import exception.DuplicateUserException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.User;
import model.UserRole;

import java.util.List;

/**
 * Interface defining user management operations for Administrators.
 * Demonstrates Abstraction and Interface contracts.
 */
public interface IUserService {
    void createUser(User user) throws DuplicateUserException, InvalidInputException;
    User getUserById(String userId) throws UserNotFoundException;
    User getUserByEmail(String email) throws UserNotFoundException;
    List<User> getAllUsers();
    List<User> searchUsers(String query);
    void updateUserDetails(String userId, String name, String email, String password) throws UserNotFoundException, InvalidInputException;
    void updateUserRole(String userId, UserRole newRole) throws UserNotFoundException;
    void deleteUser(String userId) throws UserNotFoundException;
    int getUserCount();
    int getTraderCount();
    int getAdminCount();
}
