package dao;

import model.Transaction;
import util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Trade Transactions.
 * Uses real PreparedStatement to log audit records into SQL relational table.
 * Demonstrates Collections (ArrayList<Transaction>, List<Transaction>) & Generics for GUVI Evaluation.
 */
public class TransactionDAO implements TransactionDAOInterface {

    @Override
    public boolean insert(Connection conn, Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, stock_id, symbol, transaction_type, quantity, price, total_amount, transaction_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
            ps.setDouble(6, transaction.getPrice());
            ps.setDouble(7, transaction.getTotalAmount());
            ps.setTimestamp(8, Timestamp.valueOf(transaction.getTransactionDate() != null ?
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
        String sql = "SELECT id, user_id, stock_id, symbol, transaction_type, quantity, price, total_amount, transaction_date " +
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
        String sql = "SELECT id, user_id, stock_id, symbol, transaction_type, quantity, price, total_amount, transaction_date " +
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
        double price = rs.getDouble("price");
        double total = rs.getDouble("total_amount");
        Timestamp ts = rs.getTimestamp("transaction_date");
        LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Transaction(id, userId, stockId, symbol, type, quantity, price, total, dt);
    }
}
