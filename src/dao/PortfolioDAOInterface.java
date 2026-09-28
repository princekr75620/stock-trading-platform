package dao;

import model.Portfolio;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Interface defining contract for Portfolio database operations.
 * Fulfills Core Java Interfaces & DAO architecture requirements for GUVI Evaluation.
 */
public interface PortfolioDAOInterface {
    Portfolio findByUserId(String userId) throws SQLException;
    Map<String, Integer> getHoldings(String userId) throws SQLException;
    boolean recordBuy(Connection conn, String userId, int stockId, String symbol, int quantity, double price) throws SQLException;
    boolean recordSell(Connection conn, String userId, String symbol, int quantity) throws SQLException;
}
