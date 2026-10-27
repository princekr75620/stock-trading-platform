package dao;

import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object implementation for Trader Stock Watchlists.
 * Uses parameterized PreparedStatements to prevent SQL injection.
 * Fully supports SQLite and MySQL.
 */
public class WatchlistDAO implements WatchlistDAOInterface {

    @Override
    public List<String> getWatchlist(String userId) throws SQLException {
        List<String> list = new ArrayList<>();
        if (userId == null || userId.trim().isEmpty()) {
            return list;
        }

        String sql = "SELECT symbol FROM watchlist WHERE user_id = ? ORDER BY added_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("symbol"));
                }
            }
        }
        return list;
    }

    @Override
    public boolean addToWatchlist(String userId, String symbol) throws SQLException {
        if (userId == null || symbol == null) return false;
        String sym = symbol.toUpperCase().trim();
        String uid = userId.trim();

        if (isInWatchlist(uid, sym)) {
            return true; // Idempotent
        }

        String sql = "INSERT INTO watchlist (user_id, symbol) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, sym);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean removeFromWatchlist(String userId, String symbol) throws SQLException {
        if (userId == null || symbol == null) return false;
        String sql = "DELETE FROM watchlist WHERE user_id = ? AND symbol = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, symbol.toUpperCase().trim());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean isInWatchlist(String userId, String symbol) throws SQLException {
        if (userId == null || symbol == null) return false;
        String sql = "SELECT COUNT(*) FROM watchlist WHERE user_id = ? AND symbol = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, symbol.toUpperCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
