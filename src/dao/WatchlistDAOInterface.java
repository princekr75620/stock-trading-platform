package dao;

import java.sql.SQLException;
import java.util.List;

/**
 * Data Access Object Interface for Trader Stock Watchlists.
 * Encapsulates SQL CRUD operations for user watchlist items.
 */
public interface WatchlistDAOInterface {
    List<String> getWatchlist(String userId) throws SQLException;
    boolean addToWatchlist(String userId, String symbol) throws SQLException;
    boolean removeFromWatchlist(String userId, String symbol) throws SQLException;
    boolean isInWatchlist(String userId, String symbol) throws SQLException;
}
