package service;

import dao.UserDAO;
import exception.DuplicateUserException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.Admin;
import model.Trader;
import model.User;
import model.UserRole;
import util.PasswordHasher;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative User Management Service backed by JDBC (UserDAO).
 * An optional in-memory constructor is preserved solely for isolated unit tests
 * that do not target relational database persistence.
 */
public class UserService implements IUserService {
    private final boolean jdbcAuthoritative;
    private final Map<String, User> isolatedTestMap;
    private final UserDAO userDAO = new UserDAO();

    /**
     * Production JDBC-authoritative constructor. Every read and write queries UserDAO directly.
     */
    public UserService() {
        this.jdbcAuthoritative = true;
        this.isolatedTestMap = null;
    }

    /**
     * Isolated unit-test constructor when explicitly supplied an in-memory map.
     */
    public UserService(Map<String, User> initialUsers) {
        this.jdbcAuthoritative = false;
        this.isolatedTestMap = (initialUsers != null) ? new HashMap<>(initialUsers) : new HashMap<>();
    }

    @Override
    public synchronized void createUser(User user) throws DuplicateUserException, InvalidInputException {
        if (user == null) {
            throw new InvalidInputException("User cannot be null.");
        }
        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            throw new InvalidInputException("User ID cannot be blank.");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new InvalidInputException("A valid email address is required.");
        }
        if (user.getPassword() == null || user.getPassword().length() < 4) {
            throw new InvalidInputException("Password must be at least 4 characters long.");
        }

        String uid = user.getUserId().trim();
        user.setUserId(uid);
        user.setEmail(user.getEmail().trim());

        // Hash raw password with BCrypt unless already hashed or explicitly marked legacy for migration tests
        if (!PasswordHasher.isBCryptHash(user.getPassword()) &&
            !PasswordHasher.isLegacyMarkedPassword(user.getPassword())) {
            user.setPassword(PasswordHasher.hashPassword(user.getPassword()));
        }

        if (!jdbcAuthoritative) {
            if (isolatedTestMap.containsKey(uid)) {
                throw new DuplicateUserException("User with ID '" + uid + "' already exists.");
            }
            for (User existing : isolatedTestMap.values()) {
                if (existing.getEmail().equalsIgnoreCase(user.getEmail())) {
                    throw new DuplicateUserException("User with email '" + user.getEmail() + "' already exists.");
                }
            }
            isolatedTestMap.put(uid, user);
            return;
        }

        try {
            if (userDAO.findById(uid) != null) {
                throw new DuplicateUserException("User with ID '" + uid + "' already exists.");
            }
            if (userDAO.findByEmail(user.getEmail()) != null) {
                throw new DuplicateUserException("User with email '" + user.getEmail() + "' already exists.");
            }
            boolean inserted = userDAO.insert(user);
            if (!inserted) {
                throw new InvalidInputException("Failed to persist user record to database.");
            }
        } catch (SQLException e) {
            throw new InvalidInputException("Database error creating user: " + e.getMessage());
        }
    }

    @Override
    public User getUserById(String userId) throws UserNotFoundException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserNotFoundException("User ID cannot be blank.");
        }
        String uid = userId.trim();
        if (!jdbcAuthoritative) {
            User found = isolatedTestMap.get(uid);
            if (found == null) {
                for (User u : isolatedTestMap.values()) {
                    if (u.getUserId().equalsIgnoreCase(uid)) {
                        found = u;
                        break;
                    }
                }
            }
            if (found == null) {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
            return found;
        }

        try {
            User u = userDAO.findById(uid);
            if (u == null) {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
            return u;
        } catch (SQLException e) {
            throw new UserNotFoundException("Database error looking up user ID '" + userId + "': " + e.getMessage());
        }
    }

    @Override
    public User getUserByEmail(String email) throws UserNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new UserNotFoundException("Email cannot be null.");
        }
        String em = email.trim();
        if (!jdbcAuthoritative) {
            for (User u : isolatedTestMap.values()) {
                if (u.getEmail().equalsIgnoreCase(em)) {
                    return u;
                }
            }
            throw new UserNotFoundException("User not found with email: " + email);
        }

        try {
            User u = userDAO.findByEmail(em);
            if (u == null) {
                throw new UserNotFoundException("User not found with email: " + email);
            }
            return u;
        } catch (SQLException e) {
            throw new UserNotFoundException("Database error looking up email '" + email + "': " + e.getMessage());
        }
    }

    @Override
    public List<User> getAllUsers() {
        if (!jdbcAuthoritative) {
            return new ArrayList<>(isolatedTestMap.values());
        }
        try {
            return userDAO.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Database error retrieving users: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> searchUsers(String query) {
        if (!jdbcAuthoritative) {
            List<User> results = new ArrayList<>();
            if (query == null || query.trim().isEmpty()) return results;
            String q = query.toLowerCase().trim();
            for (User u : isolatedTestMap.values()) {
                if (u.getUserId().toLowerCase().contains(q) ||
                    u.getName().toLowerCase().contains(q) ||
                    u.getEmail().toLowerCase().contains(q) ||
                    u.getRole().name().toLowerCase().contains(q)) {
                    results.add(u);
                }
            }
            return results;
        }
        try {
            return userDAO.search(query);
        } catch (SQLException e) {
            throw new RuntimeException("Database error searching users: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateUserDetails(String userId, String name, String email, String password)
            throws UserNotFoundException, InvalidInputException {
        User user = getUserById(userId);

        if (name != null && !name.trim().isEmpty()) {
            user.setName(name.trim());
        }
        if (email != null && !email.trim().isEmpty()) {
            if (!email.contains("@")) {
                throw new InvalidInputException("Invalid email format.");
            }
            String trimmedEmail = email.trim();
            if (!jdbcAuthoritative) {
                for (User u : isolatedTestMap.values()) {
                    if (!u.getUserId().equalsIgnoreCase(userId) && u.getEmail().equalsIgnoreCase(trimmedEmail)) {
                        throw new InvalidInputException("Email '" + email + "' is already in use by another user.");
                    }
                }
            } else {
                try {
                    User existingWithEmail = userDAO.findByEmail(trimmedEmail);
                    if (existingWithEmail != null && !existingWithEmail.getUserId().equalsIgnoreCase(user.getUserId())) {
                        throw new InvalidInputException("Email '" + email + "' is already in use by another user.");
                    }
                } catch (SQLException e) {
                    throw new InvalidInputException("Database validation failed: " + e.getMessage());
                }
            }
            user.setEmail(trimmedEmail);
        }
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 4) {
                throw new InvalidInputException("Password must be at least 4 characters.");
            }
            if (!PasswordHasher.isBCryptHash(password)) {
                user.setPassword(PasswordHasher.hashPassword(password));
            } else {
                user.setPassword(password);
            }
        }
        updateUser(user);
    }

    @Override
    public synchronized void updateUser(User user) {
        if (user == null || user.getUserId() == null) return;
        if (!jdbcAuthoritative) {
            isolatedTestMap.put(user.getUserId(), user);
            return;
        }
        try {
            userDAO.update(user);
        } catch (SQLException e) {
            throw new RuntimeException("Database error updating user '" + user.getUserId() + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized boolean migrateLegacyPassword(String userId, String expectedLegacyValue, String newBcryptHash) {
        if (userId == null || expectedLegacyValue == null || newBcryptHash == null) return false;
        if (!PasswordHasher.isLegacyMarkedPassword(expectedLegacyValue) || !PasswordHasher.isBCryptHash(newBcryptHash)) {
            return false;
        }
        if (!jdbcAuthoritative) {
            User u = isolatedTestMap.get(userId);
            if (u != null && expectedLegacyValue.equals(u.getPassword())) {
                u.setPassword(newBcryptHash);
                return true;
            }
            return false;
        }
        try {
            return userDAO.updatePasswordConditionally(userId, expectedLegacyValue, newBcryptHash);
        } catch (SQLException e) {
            throw new RuntimeException("Database error migrating legacy password for '" + userId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateUserRole(String userId, UserRole newRole) throws UserNotFoundException {
        User existing = getUserById(userId);
        if (existing.getRole() == newRole) {
            return;
        }

        User converted;
        if (newRole == UserRole.ADMIN) {
            converted = new Admin(existing.getUserId(), existing.getName(), existing.getEmail(), existing.getPassword());
        } else {
            double balance = existing.getBalance() > 0 ? existing.getBalance() : 10000.0;
            converted = new Trader(existing.getUserId(), existing.getName(), existing.getEmail(), existing.getPassword(), balance);
        }
        converted.setId(existing.getId());
        converted.setCreatedAt(existing.getCreatedAt());

        if (!jdbcAuthoritative) {
            isolatedTestMap.put(converted.getUserId(), converted);
            return;
        }
        try {
            userDAO.update(converted);
        } catch (SQLException e) {
            throw new RuntimeException("Database error updating user role: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteUser(String userId) throws UserNotFoundException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new UserNotFoundException("Cannot delete: user ID cannot be blank.");
        }
        String uid = userId.trim();
        if (!jdbcAuthoritative) {
            if (!isolatedTestMap.containsKey(uid)) {
                throw new UserNotFoundException("Cannot delete: user ID '" + userId + "' does not exist.");
            }
            isolatedTestMap.remove(uid);
            return;
        }
        try {
            boolean deleted = userDAO.delete(uid);
            if (!deleted) {
                throw new UserNotFoundException("Cannot delete: user ID '" + userId + "' does not exist.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error deleting user '" + userId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public int getUserCount() {
        if (!jdbcAuthoritative) return isolatedTestMap.size();
        try {
            return userDAO.countAll();
        } catch (SQLException e) {
            throw new RuntimeException("Database error counting users: " + e.getMessage(), e);
        }
    }

    @Override
    public int getTraderCount() {
        if (!jdbcAuthoritative) {
            int count = 0;
            for (User u : isolatedTestMap.values()) {
                if (u.getRole() == UserRole.TRADER) count++;
            }
            return count;
        }
        try {
            return userDAO.countByRole(UserRole.TRADER);
        } catch (SQLException e) {
            throw new RuntimeException("Database error counting traders: " + e.getMessage(), e);
        }
    }

    @Override
    public int getAdminCount() {
        if (!jdbcAuthoritative) {
            int count = 0;
            for (User u : isolatedTestMap.values()) {
                if (u.getRole() == UserRole.ADMIN) count++;
            }
            return count;
        }
        try {
            return userDAO.countByRole(UserRole.ADMIN);
        } catch (SQLException e) {
            throw new RuntimeException("Database error counting admins: " + e.getMessage(), e);
        }
    }
}
