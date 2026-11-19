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
        String uid = user.getUserId() != null ? user.getUserId().trim() : "";
        if (uid.isEmpty() || !uid.matches("^[a-zA-Z0-9_-]{3,40}$")) {
            throw new InvalidInputException("User ID must be 3-40 alphanumeric, underscore, or hyphen characters.");
        }
        String rawEmail = user.getEmail() != null ? user.getEmail().trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (!rawEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new InvalidInputException("A valid email address is required.");
        }
        String rawName = user.getName() != null ? user.getName().trim() : "";
        if (rawName.length() < 2 || rawName.length() > 100) {
            throw new InvalidInputException("Name must be between 2 and 100 characters.");
        }
        String rawPassword = user.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new InvalidInputException("Password is required.");
        }

        boolean alreadyHashedOrLegacy = PasswordHasher.isBCryptHash(rawPassword) || PasswordHasher.isLegacyMarkedPassword(rawPassword);
        if (!alreadyHashedOrLegacy) {
            String strengthError = PasswordHasher.validatePasswordStrength(rawPassword);
            if (strengthError != null) {
                throw new InvalidInputException(strengthError);
            }
            user.setPassword(PasswordHasher.hashPassword(rawPassword));
        }

        user.setUserId(uid);
        user.setName(rawName);
        user.setEmail(rawEmail);

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
            String trimmedName = name.trim();
            if (trimmedName.length() < 2 || trimmedName.length() > 100) {
                throw new InvalidInputException("Name must be between 2 and 100 characters.");
            }
            user.setName(trimmedName);
        }
        if (email != null && !email.trim().isEmpty()) {
            String trimmedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmedEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new InvalidInputException("Invalid email format.");
            }
            if (!jdbcAuthoritative) {
                for (User u : isolatedTestMap.values()) {
                    if (!u.getUserId().equalsIgnoreCase(userId) && u.getEmail().equalsIgnoreCase(trimmedEmail)) {
                        throw new InvalidInputException("Email '" + trimmedEmail + "' is already in use by another user.");
                    }
                }
            } else {
                try {
                    User existingWithEmail = userDAO.findByEmail(trimmedEmail);
                    if (existingWithEmail != null && !existingWithEmail.getUserId().equalsIgnoreCase(user.getUserId())) {
                        throw new InvalidInputException("Email '" + trimmedEmail + "' is already in use by another user.");
                    }
                } catch (SQLException e) {
                    throw new InvalidInputException("Database validation failed: " + e.getMessage());
                }
            }
            user.setEmail(trimmedEmail);
        }
        if (password != null && !password.isEmpty()) {
            if (!PasswordHasher.isBCryptHash(password)) {
                String strengthError = PasswordHasher.validatePasswordStrength(password);
                if (strengthError != null) {
                    throw new InvalidInputException(strengthError);
                }
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
            boolean updated = userDAO.update(user);
            if (!updated) {
                throw new RuntimeException("User update affected 0 rows for user '" + user.getUserId() + "'.");
            }
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
            boolean updated = userDAO.update(converted);
            if (!updated) {
                throw new RuntimeException("User role update affected 0 rows for '" + userId + "'.");
            }
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
