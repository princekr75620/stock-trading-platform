package dao;

import model.Transaction;
import util.DBConnection;
import util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Trade Transactions & Idempotency Tracking.
 * Persists fee_amount, BigDecimal monetary totals, and trade_idempotency records atomically.
 */
public class TransactionDAO implements TransactionDAOInterface {

    @Override
    public boolean existsIdempotencyKey(Connection conn, String userId, String idempotencyKey) throws SQLException {
        if (userId == null || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM trade_idempotency WHERE user_id = ? AND idempotency_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, idempotencyKey.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean recordIdempotencyKey(Connection conn, String userId, String idempotencyKey, int transactionId) throws SQLException {
        if (userId == null || idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return true;
        }
        String sql = "INSERT INTO trade_idempotency (user_id, idempotency_key, transaction_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, idempotencyKey.trim());
            ps.setInt(3, transactionId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean insert(Connection conn, Transaction transaction) throws SQLException {
        if (transaction == null
                || transaction.getUserId() == null || transaction.getUserId().trim().isEmpty()
                || transaction.getSymbol() == null || transaction.getSymbol().trim().isEmpty()
                || transaction.getQuantity() <= 0
                || transaction.getPriceDecimal().compareTo(BigDecimal.ZERO) <= 0
                || transaction.getFeeAmountDecimal().compareTo(BigDecimal.ZERO) < 0
                || transaction.getTotalAmountDecimal().compareTo(BigDecimal.ZERO) < 0) {
            throw new SQLException("Invalid transaction ledger record: positive quantity/price and non-negative fee/total required.");
        }

        String sql = "INSERT INTO transactions (user_id, stock_id, symbol, transaction_type, quantity, price, fee_amount, total_amount, idempotency_key, transaction_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        boolean autoClose = false;
        if (conn == null) {
            conn = DBConnection.getConnection();
            autoClose = true;
        }

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, transaction.getUserId());
            ps.setInt(2, transaction.getStockId());
            ps.setString(3, transaction.getSymbol());
            ps.setString(4, transaction.getTransactionType());
            ps.setInt(5, transaction.getQuantity());
            ps.setBigDecimal(6, transaction.getPriceDecimal());
            ps.setBigDecimal(7, transaction.getFeeAmountDecimal());
            ps.setBigDecimal(8, transaction.getTotalAmountDecimal());
            if (transaction.getIdempotencyKey() != null && !transaction.getIdempotencyKey().isEmpty()) {
                ps.setString(9, transaction.getIdempotencyKey());
            } else {
                ps.setNull(9, Types.VARCHAR);
            }
            ps.setTimestamp(10, Timestamp.valueOf(transaction.getTransactionDate() != null ?
                    transaction.getTransactionDate() : LocalDateTime.now()));

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (gk.next()) {
                        transaction.setId(gk.getInt(1));
                    }
                }
                return true;
            }
            return false;
        } finally {
            if (autoClose && conn != null) {
                conn.close();
            }
        }
    }

    @Override
    public ArrayList<Transaction> findByUserId(String userId) throws SQLException {
        ArrayList<Transaction> list = new ArrayList<>();
        String sql = "SELECT id, user_id, stock_id, symbol, transaction_type, quantity, price, fee_amount, total_amount, idempotency_key, transaction_date " +
                     "FROM transactions WHERE user_id = ? ORDER BY id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Transaction> findAll() throws SQLException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT id, user_id, stock_id, symbol, transaction_type, quantity, price, fee_amount, total_amount, idempotency_key, transaction_date " +
                     "FROM transactions ORDER BY id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToTransaction(rs));
            }
        }
        return list;
    }

    private Transaction mapRowToTransaction(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String userId = rs.getString("user_id");
        int stockId = rs.getInt("stock_id");
        String symbol = rs.getString("symbol");
        String type = rs.getString("transaction_type");
        int quantity = rs.getInt("quantity");
        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal fee = null;
        try { fee = rs.getBigDecimal("fee_amount"); } catch (SQLException ignored) {}
        BigDecimal total = rs.getBigDecimal("total_amount");
        String idemKey = null;
        try { idemKey = rs.getString("idempotency_key"); } catch (SQLException ignored) {}
        Timestamp ts = rs.getTimestamp("transaction_date");
        LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Transaction(
                id, userId, stockId, symbol, type, quantity,
                price != null ? MoneyUtil.round(price) : MoneyUtil.ZERO,
                fee != null ? MoneyUtil.round(fee) : MoneyUtil.ZERO,
                total != null ? MoneyUtil.round(total) : MoneyUtil.ZERO,
                idemKey, dt
        );
    }
}
