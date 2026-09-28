package service;

import model.MarketUpdate;
import model.Stock;
import model.Trader;

import java.util.List;
import java.util.Map;

/**
 * Interface defining market update operations, real-time simulated feed,
 * and preference-filtered views.
 */
public interface IMarketUpdateService {
    void addStock(Stock stock);
    void updateStock(Stock stock);
    boolean deleteStock(String symbol);
    Stock getStock(String symbol);
    List<Stock> getAllStocks();
    void recordMarketUpdate(MarketUpdate update);
    List<MarketUpdate> getAllMarketUpdates();
    List<MarketUpdate> getMarketUpdatesForTrader(Trader trader);
    void simulateMarketTick();
    void setStocks(Map<String, Stock> stocks);
    Map<String, Stock> getStockMap();
}
