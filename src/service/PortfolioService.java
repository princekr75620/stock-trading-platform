package service;

import model.Portfolio;
import model.Stock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service managing investment portfolios for all registered traders.
 * Demonstrates Map collection handling and financial calculations.
 */
public class PortfolioService implements IPortfolioService {
    // Map of Trader ID -> Portfolio
    private Map<String, Portfolio> portfolioMap;

    public PortfolioService() {
        this.portfolioMap = new HashMap<>();
    }

    public PortfolioService(Map<String, Portfolio> initialPortfolios) {
        this.portfolioMap = (initialPortfolios != null) ? new HashMap<>(initialPortfolios) : new HashMap<>();
    }

    @Override
    public synchronized Portfolio getOrCreatePortfolio(String traderId) {
        if (traderId == null) return null;
        String tid = traderId.trim();
        Portfolio p = portfolioMap.get(tid);
        if (p == null || p.getHoldings().isEmpty()) {
            try {
                dao.PortfolioDAO pdao = new dao.PortfolioDAO();
                Portfolio dbPortfolio = pdao.findByUserId(tid);
                if (dbPortfolio != null && !dbPortfolio.getHoldings().isEmpty()) {
                    portfolioMap.put(tid, dbPortfolio);
                    return dbPortfolio;
                }
            } catch (Exception ignored) {}
            if (p == null) {
                p = new Portfolio(tid);
                portfolioMap.put(tid, p);
            }
        }
        return p;
    }

    @Override
    public synchronized void updateOnBuy(String traderId, String symbol, int quantity, double price) {
        Portfolio portfolio = getOrCreatePortfolio(traderId);
        portfolio.recordBuy(symbol, quantity, price);
    }

    @Override
    public synchronized double updateOnSell(String traderId, String symbol, int quantity, double price) {
        Portfolio portfolio = getOrCreatePortfolio(traderId);
        return portfolio.recordSell(symbol, quantity, price);
    }

    @Override
    public Map<String, Portfolio> getAllPortfolios() {
        return Collections.unmodifiableMap(portfolioMap);
    }

    @Override
    public double calculateTotalPlatformAUM(Map<String, Stock> marketStocks) {
        double totalAUM = 0.0;
        for (Portfolio p : portfolioMap.values()) {
            totalAUM += p.getCurrentPortfolioValue(marketStocks);
        }
        return Math.round(totalAUM * 100.0) / 100.0;
    }
}
