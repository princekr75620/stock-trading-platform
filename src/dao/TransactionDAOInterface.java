package dao;

import model.Transaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface defining contract for Trade Transaction and Idempotency database operations.
 */
public interface TransactionDAOInterface {
    boolean insert(Connection conn, Transaction transaction) throws SQLException;
    boolean existsIdempotencyKey(Connection conn, String userId, String idempotencyKey) throws SQLException;
    boolean recordIdempotencyKey(Connection conn, String userId, String idempotencyKey, int transactionId) throws SQLException;
    ArrayList<Transaction> findByUserId(String userId) throws SQLException;
    List<Transaction> findAll() throws SQLException;
}
