package service;

import exception.DuplicateUserException;
import exception.InvalidInputException;
import exception.UserNotFoundException;
import model.Admin;
import model.Trader;
import model.User;
import model.UserRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service implementation managing user registration, lookup, role changes, and deletion.
 * Demonstrates Collections (HashMap, ArrayList) and Encapsulation.
 */
public class UserService implements IUserService {
    // Primary storage: Map of User ID -> User
    private final Map<String, User> userMap;
    private final dao.UserDAO userDAO = new dao.UserDAO();

    public UserService() {
        this.userMap = new HashMap<>();
        loadFromDB();
    }

    public UserService(Map<String, User> initialUsers) {
        this.userMap = (initialUsers != null) ? new HashMap<>(initialUsers) : new HashMap<>();
        loadFromDB();
    }

    private void loadFromDB() {
        try {
            List<User> fromDb = userDAO.findAll();
            if (fromDb != null) {
                for (User u : fromDb) {
                    if (!userMap.containsKey(u.getUserId())) {
                        userMap.put(u.getUserId(), u);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UserService] Note: loading DB users skipped: " + e.getMessage());
        }
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
        if (userMap.containsKey(uid)) {
            throw new DuplicateUserException("User with ID '" + uid + "' already exists.");
        }

        // Check for duplicate email
        for (User existing : userMap.values()) {
            if (existing.getEmail().equalsIgnoreCase(user.getEmail().trim())) {
                throw new DuplicateUserException("User with email '" + user.getEmail() + "' already exists.");
            }
        }

        userMap.put(uid, user);
        try {
            userDAO.insert(user);
        } catch (Exception e) {
            System.err.println("[UserService] User persisted in memory, DB sync notice: " + e.getMessage());
        }
    }

    @Override
    public User getUserById(String userId) throws UserNotFoundException {
        if (userId == null || !userMap.containsKey(userId.trim())) {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
        return userMap.get(userId.trim());
    }

    @Override
    public User getUserByEmail(String email) throws UserNotFoundException {
        if (email == null) {
            throw new UserNotFoundException("Email cannot be null.");
        }
        for (User u : userMap.values()) {
            if (u.getEmail().equalsIgnoreCase(email.trim())) {
                return u;
            }
        }
        throw new UserNotFoundException("User not found with email: " + email);
    }

    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    @Override
    public List<User> searchUsers(String query) {
        List<User> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        String q = query.toLowerCase().trim();
        for (User u : userMap.values()) {
            if (u.getUserId().toLowerCase().contains(q) ||
                u.getName().toLowerCase().contains(q) ||
                u.getEmail().toLowerCase().contains(q) ||
                u.getRole().name().toLowerCase().contains(q)) {
                results.add(u);
            }
        }
        return results;
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
            // Check uniqueness if email changed
            for (User u : userMap.values()) {
                if (!u.getUserId().equals(userId) && u.getEmail().equalsIgnoreCase(email.trim())) {
                    throw new InvalidInputException("Email '" + email + "' is already in use by another user.");
                }
            }
            user.setEmail(email.trim());
        }
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 4) {
                throw new InvalidInputException("Password must be at least 4 characters.");
            }
            user.setPassword(password);
        }
        try {
            userDAO.update(user);
        } catch (Exception e) {
            System.err.println("[UserService] Error updating user in DB: " + e.getMessage());
        }
    }

    @Override
    public synchronized void updateUserRole(String userId, UserRole newRole) throws UserNotFoundException {
        User existing = getUserById(userId);
        if (existing.getRole() == newRole) {
            return; // Already has role
        }

        User converted;
        if (newRole == UserRole.ADMIN) {
            converted = new Admin(existing.getUserId(), existing.getName(), existing.getEmail(), existing.getPassword());
        } else {
            converted = new Trader(existing.getUserId(), existing.getName(), existing.getEmail(), existing.getPassword(), 10000.0);
        }
        converted.setCreatedAt(existing.getCreatedAt());
        userMap.put(userId, converted);
    }

    @Override
    public synchronized void deleteUser(String userId) throws UserNotFoundException {
        if (userId == null || !userMap.containsKey(userId.trim())) {
            throw new UserNotFoundException("Cannot delete: user ID '" + userId + "' does not exist.");
        }
        userMap.remove(userId.trim());
        try {
            userDAO.delete(userId.trim());
        } catch (Exception e) {
            System.err.println("[UserService] Error deleting user in DB: " + e.getMessage());
        }
    }

    @Override
    public int getUserCount() {
        return userMap.size();
    }

    @Override
    public int getTraderCount() {
        int count = 0;
        for (User u : userMap.values()) {
            if (u.getRole() == UserRole.TRADER) count++;
        }
        return count;
    }

    @Override
    public int getAdminCount() {
        int count = 0;
        for (User u : userMap.values()) {
            if (u.getRole() == UserRole.ADMIN) count++;
        }
        return count;
    }

    public Map<String, User> getUserMap() {
        return userMap;
    }
}
