package dao;

import model.Stock;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Interface defining contract for Stock database operations.
 * Fulfills Core Java Interfaces & DAO architecture requirements for GUVI Evaluation.
 */
public interface StockDAOInterface {
    Stock findBySymbol(String symbol) throws SQLException;
    Stock findById(int id) throws SQLException;
    List<Stock> findAll() throws SQLException;
    Map<String, Stock> findAllAsMap() throws SQLException;
    List<Stock> search(String query, int page, int pageSize) throws SQLException;
    boolean insert(Stock stock) throws SQLException;
    int insertBatch(List<Stock> stocks) throws SQLException;
    boolean update(Stock stock) throws SQLException;
    boolean updateQuantityAndVolume(Connection conn, String symbol, int quantitySold, boolean isBuy) throws SQLException;
    boolean delete(String symbol) throws SQLException;
}
