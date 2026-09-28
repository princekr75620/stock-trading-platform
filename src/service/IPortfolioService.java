package service;

import model.Portfolio;
import model.Stock;

import java.util.Map;

/**
 * Interface defining operations for managing Trader portfolios.
 */
public interface IPortfolioService {
    Portfolio getOrCreatePortfolio(String traderId);
    void updateOnBuy(String traderId, String symbol, int quantity, double price);
    double updateOnSell(String traderId, String symbol, int quantity, double price);
    Map<String, Portfolio> getAllPortfolios();
    double calculateTotalPlatformAUM(Map<String, Stock> marketStocks);
}
