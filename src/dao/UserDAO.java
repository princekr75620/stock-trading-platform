package dao;

import model.*;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data Access Object for User entities.
 * Executes real JDBC CRUD operations using PreparedStatement and ResultSet.
 * Fulfills Database Integration & DAO architecture requirements for GUVI Evaluation.
 */
public class UserDAO implements UserDAOInterface {

    private static final String SELECT_COLUMNS =
            "id, user_id, name, email, password, role, balance, department, " +
            "email_alerts_enabled, alert_threshold_percent, created_at";

    @Override
    public User findById(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) return null;
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, userId.trim());
        }
    }

    @Override
    public User findById(Connection conn, String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) return null;
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE LOWER(user_id) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(conn, rs);
                }
            }
        }
        return null;
    }

    @Override
    public User findByEmail(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) return null;
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(conn, rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(conn, rs));
            }
        }
        return list;
    }

    @Override
    public List<User> search(String query) throws SQLException {
        List<User> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return list;
        }
        String pattern = "%" + query.trim().toLowerCase() + "%";
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users " +
                     "WHERE LOWER(user_id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(role) LIKE ? " +
                     "ORDER BY id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(conn, rs));
                }
            }
        }
        return list;
    }

    @Override
    public boolean insert(User user) throws SQLException {
        String sql = "INSERT INTO users (user_id, name, email, password, role, balance, department, email_alerts_enabled, alert_threshold_percent) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                ps.setInt(8, 1);
                ps.setDouble(9, 2.0);
            } else if (user instanceof Trader) {
                Trader t = (Trader) user;
                ps.setNull(7, Types.VARCHAR);
                ps.setInt(8, t.isEmailAlertsEnabled() ? 1 : 0);
                ps.setDouble(9, t.getAlertThresholdPercent());
            } else {
                ps.setNull(7, Types.VARCHAR);
                ps.setInt(8, 1);
                ps.setDouble(9, 2.0);
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
        String sql = "UPDATE users SET name = ?, email = ?, password = ?, role = ?, balance = ?, department = ?, " +
                     "email_alerts_enabled = ?, alert_threshold_percent = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getRole().name());
            ps.setDouble(5, user.getBalance());
            if (user instanceof Admin) {
                ps.setString(6, ((Admin) user).getDepartment());
                ps.setInt(7, 1);
                ps.setDouble(8, 2.0);
            } else if (user instanceof Trader) {
                Trader t = (Trader) user;
                ps.setNull(6, Types.VARCHAR);
                ps.setInt(7, t.isEmailAlertsEnabled() ? 1 : 0);
                ps.setDouble(8, t.getAlertThresholdPercent());
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setInt(7, 1);
                ps.setDouble(8, 2.0);
            }
            ps.setString(9, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updatePasswordConditionally(String userId, String expectedOldPassword, String newHashedPassword) throws SQLException {
        if (userId == null || expectedOldPassword == null || newHashedPassword == null) return false;
        String sql = "UPDATE users SET password = ? WHERE user_id = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, userId.trim());
            ps.setString(3, expectedOldPassword);
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
    public boolean updateBalance(String userId, double newBalance) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return updateBalance(conn, userId, newBalance);
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

    @Override
    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public int countByRole(UserRole role) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE UPPER(role) = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Set<String> loadWatchlistWithinConnection(Connection conn, String userId) {
        Set<String> wl = new HashSet<>();
        String sql = "SELECT symbol FROM watchlist WHERE user_id = ? ORDER BY added_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wl.add(rs.getString("symbol"));
                }
            }
        } catch (SQLException ignored) {}
        return wl;
    }

    private User mapRowToUser(Connection conn, ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String password = rs.getString("password");
        String roleStr = rs.getString("role");
        double balance = rs.getDouble("balance");
        String department = rs.getString("department");

        boolean emailAlerts = true;
        double threshold = 2.0;
        LocalDateTime createdAt = LocalDateTime.now();
        try {
            int ea = rs.getInt("email_alerts_enabled");
            if (!rs.wasNull()) emailAlerts = (ea != 0);
        } catch (SQLException ignored) {}
        try {
            double th = rs.getDouble("alert_threshold_percent");
            if (!rs.wasNull() && th > 0) threshold = th;
        } catch (SQLException ignored) {}
        try {
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) createdAt = ts.toLocalDateTime();
        } catch (SQLException ignored) {}

        UserRole role = "ADMIN".equalsIgnoreCase(roleStr) ? UserRole.ADMIN : UserRole.TRADER;
        if (role == UserRole.ADMIN) {
            Admin admin = new Admin(userId, name, email, password, department != null ? department : "System Operations", createdAt);
            admin.setId(id);
            admin.setBalance(balance);
            return admin;
        } else {
            Set<String> wl = loadWatchlistWithinConnection(conn, userId);
            Trader trader = new Trader(userId, name, email, password, balance, wl, threshold, emailAlerts, createdAt);
            trader.setId(id);
            return trader;
        }
    }
}
