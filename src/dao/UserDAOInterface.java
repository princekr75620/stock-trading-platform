package dao;

import model.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface defining contract for User database operations.
 * Fulfills Core Java Interfaces & DAO architecture requirements for GUVI Evaluation.
 */
public interface UserDAOInterface {
    User findById(String userId) throws SQLException;
    User findByEmail(String email) throws SQLException;
    List<User> findAll() throws SQLException;
    boolean insert(User user) throws SQLException;
    boolean update(User user) throws SQLException;
    boolean updateBalance(Connection conn, String userId, double newBalance) throws SQLException;
    boolean delete(String userId) throws SQLException;
}
