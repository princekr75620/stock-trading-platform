package dao;

import model.Portfolio;
import util.DBConnection;
import util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for Trader Portfolio records.
 * Uses BigDecimal weighted average calculations, MySQL row locking, and atomic conditional updates.
 */
public class PortfolioDAO implements PortfolioDAOInterface {

    @Override
    public Portfolio findByUserId(String userId) throws SQLException {
        Portfolio portfolio = new Portfolio(userId);
        String sql = "SELECT symbol, quantity, average_price FROM portfolio WHERE user_id = ? AND quantity > 0";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String symbol = rs.getString("symbol");
                    int qty = rs.getInt("quantity");
                    BigDecimal avgDec = rs.getBigDecimal("average_price");
                    if (symbol == null || symbol.trim().isEmpty() || qty <= 0
                            || avgDec == null || avgDec.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new SQLException("Corrupted portfolio record detected for user '" + userId + "' (symbol=" + symbol + ").");
                    }
                    portfolio.recordBuy(symbol, qty, MoneyUtil.round(avgDec));
                }
            }
        }
        return portfolio;
    }

    @Override
    public Map<String, Portfolio> findAll() throws SQLException {
        Map<String, Portfolio> result = new HashMap<>();
        String sql = "SELECT user_id, symbol, quantity, average_price FROM portfolio WHERE quantity > 0 ORDER BY user_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String userId = rs.getString("user_id");
                String symbol = rs.getString("symbol");
                int qty = rs.getInt("quantity");
                BigDecimal avgDec = rs.getBigDecimal("average_price");
                if (userId == null || userId.trim().isEmpty() || symbol == null || symbol.trim().isEmpty()
                        || qty <= 0 || avgDec == null || avgDec.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new SQLException("Corrupted portfolio record detected in batch load for user '" + userId + "'.");
                }
                Portfolio p = result.computeIfAbsent(userId, Portfolio::new);
                p.recordBuy(symbol, qty, MoneyUtil.round(avgDec));
            }
        }
        return result;
    }

    @Override
    public Map<String, Integer> getHoldings(String userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return getHoldings(conn, userId);
        }
    }

    @Override
    public Map<String, Integer> getHoldings(Connection conn, String userId) throws SQLException {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT symbol, quantity FROM portfolio WHERE user_id = ? AND quantity > 0";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("symbol"), rs.getInt("quantity"));
                }
            }
        }
        return map;
    }

    @Override
    public boolean recordBuy(Connection conn, String userId, int stockId, String symbol, int quantity, double price) throws SQLException {
        if (!Double.isFinite(price) || price <= 0.0) {
            return false;
        }
        return recordBuy(conn, userId, stockId, symbol, quantity, MoneyUtil.ofExact(price));
    }

    @Override
    public boolean recordBuy(Connection conn, String userId, int stockId, String symbol, int quantity, BigDecimal price) throws SQLException {
        if (userId == null || userId.trim().isEmpty() || symbol == null || symbol.trim().isEmpty()
                || quantity <= 0 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        symbol = symbol.toUpperCase().trim();
        String checkSql = "SELECT id, quantity, average_price FROM portfolio WHERE user_id = ? AND symbol = ?"
                + (DBConnection.isMySQL() ? " FOR UPDATE" : "");
        int existingQty = 0;
        BigDecimal existingAvgPrice = MoneyUtil.ZERO;
        boolean exists = false;

        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, userId);
            ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    exists = true;
                    existingQty = rs.getInt("quantity");
                    BigDecimal avgDec = rs.getBigDecimal("average_price");
                    if (existingQty <= 0 || avgDec == null || avgDec.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new SQLException("Corrupted existing portfolio row for user '" + userId + "' symbol '" + symbol + "'.");
                    }
                    existingAvgPrice = MoneyUtil.round(avgDec);
                }
            }
        }

        BigDecimal buyPrice = MoneyUtil.round(price);
        if (exists) {
            int newTotalQty = existingQty + quantity;
            BigDecimal newAvgPrice = MoneyUtil.calculateWeightedAveragePrice(existingQty, existingAvgPrice, quantity, buyPrice);
            String updateSql = "UPDATE portfolio SET quantity = ?, average_price = ? WHERE user_id = ? AND symbol = ? AND quantity = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, newTotalQty);
                ps.setBigDecimal(2, newAvgPrice);
                ps.setString(3, userId);
                ps.setString(4, symbol);
                ps.setInt(5, existingQty);
                return ps.executeUpdate() > 0;
            }
        } else {
            String insertSql = "INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, userId);
                ps.setInt(2, stockId);
                ps.setString(3, symbol);
                ps.setInt(4, quantity);
                ps.setBigDecimal(5, buyPrice);
                return ps.executeUpdate() > 0;
            }
        }
    }

    @Override
    public boolean recordSell(Connection conn, String userId, String symbol, int quantity) throws SQLException {
        if (userId == null || userId.trim().isEmpty() || symbol == null || symbol.trim().isEmpty() || quantity <= 0) {
            return false;
        }
        symbol = symbol.toUpperCase().trim();
        String checkSql = "SELECT quantity FROM portfolio WHERE user_id = ? AND symbol = ?"
                + (DBConnection.isMySQL() ? " FOR UPDATE" : "");
        int existingQty = 0;

        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, userId);
            ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existingQty = rs.getInt("quantity");
                } else {
                    return false;
                }
            }
        }

        if (existingQty < quantity) {
            return false;
        } else if (existingQty == quantity) {
            String delSql = "DELETE FROM portfolio WHERE user_id = ? AND symbol = ? AND quantity = ?";
            try (PreparedStatement ps = conn.prepareStatement(delSql)) {
                ps.setString(1, userId);
                ps.setString(2, symbol);
                ps.setInt(3, quantity);
                return ps.executeUpdate() > 0;
            }
        } else {
            String updSql = "UPDATE portfolio SET quantity = quantity - ? WHERE user_id = ? AND symbol = ? AND quantity >= ?";
            try (PreparedStatement ps = conn.prepareStatement(updSql)) {
                ps.setInt(1, quantity);
                ps.setString(2, userId);
                ps.setString(3, symbol);
                ps.setInt(4, quantity);
                return ps.executeUpdate() > 0;
            }
        }
    }
}
