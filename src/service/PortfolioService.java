package service;

import dao.PortfolioDAO;
import model.Portfolio;
import model.Stock;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Portfolio Management Service backed by authoritative JDBC persistence (PortfolioDAO).
 * Ensures holdings and weighted average cost basis always reflect committed database records.
 */
public class PortfolioService implements IPortfolioService {
    private final boolean jdbcAuthoritative;
    private final Map<String, Portfolio> isolatedTestMap;
    private final PortfolioDAO portfolioDAO = new PortfolioDAO();

    public PortfolioService() {
        this.jdbcAuthoritative = true;
        this.isolatedTestMap = null;
    }

    public PortfolioService(Map<String, Portfolio> initialPortfolios) {
        this.jdbcAuthoritative = false;
        this.isolatedTestMap = (initialPortfolios != null) ? new HashMap<>(initialPortfolios) : new HashMap<>();
    }

    @Override
    public Portfolio getOrCreatePortfolio(String traderId) {
        if (traderId == null || traderId.trim().isEmpty()) return null;
        String tid = traderId.trim();
        if (!jdbcAuthoritative) {
            return isolatedTestMap.computeIfAbsent(tid, Portfolio::new);
        }
        try {
            return portfolioDAO.findByUserId(tid);
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading portfolio for '" + tid + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateOnBuy(String traderId, String symbol, int quantity, double price) {
        if (!jdbcAuthoritative) {
            Portfolio portfolio = getOrCreatePortfolio(traderId);
            portfolio.recordBuy(symbol, quantity, price);
        }
        // In JDBC mode, TradingService executes atomic PortfolioDAO.recordBuy within transaction.
    }

    @Override
    public synchronized double updateOnSell(String traderId, String symbol, int quantity, double price) {
        if (!jdbcAuthoritative) {
            Portfolio portfolio = getOrCreatePortfolio(traderId);
            return portfolio.recordSell(symbol, quantity, price);
        }
        // In JDBC mode, TradingService executes atomic PortfolioDAO.recordSell within transaction.
        return 0.0;
    }

    @Override
    public Map<String, Portfolio> getAllPortfolios() {
        if (!jdbcAuthoritative) {
            return Collections.unmodifiableMap(isolatedTestMap);
        }
        try {
            return Collections.unmodifiableMap(portfolioDAO.findAll());
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading all portfolios: " + e.getMessage(), e);
        }
    }

    @Override
    public double calculateTotalPlatformAUM(Map<String, Stock> marketStocks) {
        double totalAUM = 0.0;
        for (Portfolio p : getAllPortfolios().values()) {
            totalAUM += p.getCurrentPortfolioValue(marketStocks);
        }
        return Math.round(totalAUM * 100.0) / 100.0;
    }
}
