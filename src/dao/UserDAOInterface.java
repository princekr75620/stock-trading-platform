package dao;

import model.User;
import model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface defining contract for User database operations.
 */
public interface UserDAOInterface {
    User findById(String userId) throws SQLException;
    User findById(Connection conn, String userId) throws SQLException;
    User findByIdForUpdate(Connection conn, String userId) throws SQLException;
    User findByEmail(String email) throws SQLException;
    List<User> findAll() throws SQLException;
    List<User> search(String query) throws SQLException;
    boolean insert(User user) throws SQLException;
    boolean update(User user) throws SQLException;
    boolean updatePasswordConditionally(String userId, String expectedOldPassword, String newHashedPassword) throws SQLException;
    boolean updateBalance(Connection conn, String userId, double newBalance) throws SQLException;
    boolean updateBalance(String userId, double newBalance) throws SQLException;
    boolean adjustBalanceAtomically(Connection conn, String userId, BigDecimal delta, BigDecimal requiredMinBalance) throws SQLException;
    boolean delete(String userId) throws SQLException;
    int countAll() throws SQLException;
    int countByRole(UserRole role) throws SQLException;
}
