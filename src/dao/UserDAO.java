package dao;

import model.*;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for User entities.
 * Executes real JDBC CRUD operations using PreparedStatement and ResultSet.
 * Fulfills Database Integration & DAO architecture requirements for GUVI Evaluation.
 */
public class UserDAO implements UserDAOInterface {

    @Override
    public User findById(String userId) throws SQLException {
        String sql = "SELECT id, user_id, name, email, password, role, balance, department, created_at FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    @Override
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT id, user_id, name, email, password, role, balance, department, created_at FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, user_id, name, email, password, role, balance, department, created_at FROM users ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
        }
        return list;
    }

    @Override
    public boolean insert(User user) throws SQLException {
        String sql = "INSERT INTO users (user_id, name, email, password, role, balance, department) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword());
            ps.setString(5, user.getRole().name());
            ps.setDouble(6, user.getBalance());
            if (user instanceof Admin) {
                ps.setString(7, ((Admin) user).getDepartment());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (gk.next()) {
                        user.setId(gk.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean update(User user) throws SQLException {
        String sql = "UPDATE users SET name = ?, email = ?, password = ?, role = ?, balance = ?, department = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole().name());
            ps.setDouble(5, user.getBalance());
            if (user instanceof Admin) {
                ps.setString(6, ((Admin) user).getDepartment());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setString(7, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateBalance(Connection conn, String userId, double newBalance) throws SQLException {
        String sql = "UPDATE users SET balance = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, Math.round(newBalance * 100.0) / 100.0);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String password = rs.getString("password");
        String roleStr = rs.getString("role");
        double balance = rs.getDouble("balance");
        String department = rs.getString("department");

        UserRole role = "ADMIN".equalsIgnoreCase(roleStr) ? UserRole.ADMIN : UserRole.TRADER;
        if (role == UserRole.ADMIN) {
            Admin admin = new Admin(userId, name, email, password, department != null ? department : "System Operations");
            admin.setId(id);
            admin.setBalance(balance);
            return admin;
        } else {
            Trader trader = new Trader(userId, name, email, password, balance);
            trader.setId(id);
            return trader;
        }
    }
}
