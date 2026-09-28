package service;

import model.MarketUpdate;
import model.Stock;
import model.Trader;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service managing real-time stock quotes, simulated market updates,
 * news events, and trader preference filtering.
 * Designed with adapter hooks so an external REST API can be integrated seamlessly.
 */
public class MarketUpdateService implements IMarketUpdateService {
    private Map<String, Stock> stockMap;
    private List<MarketUpdate> marketUpdates;
    private INotificationService notificationService;
    private Random random;
    private final dao.StockDAO stockDAO = new dao.StockDAO();

    private static final String[] MARKET_HEADLINES = {
            "Q3 net profit beats Dalal Street consensus estimates.",
            "Announced strategic AI cloud expansion partnership with enterprise clients.",
            "Domestic institutional investors (DII) increase allocation post index rebalancing.",
            "Board of Directors approves interim dividend expansion.",
            "Favorable monsoon forecast and GST collections fuel Nifty rally.",
            "RBI monetary policy committee maintains accommodative inflation stance.",
            "Export order book reaches all-time high in quarterly filing.",
            "Operational efficiencies drive 180 bps margin expansion.",
            "Capex outlay approved for new domestic manufacturing hub.",
            "Strategic acquisition strengthens retail and consumer ecosystem."
    };

    public MarketUpdateService(INotificationService notificationService) {
        this.stockMap = new HashMap<>();
        this.marketUpdates = new ArrayList<>();
        this.notificationService = notificationService;
        this.random = new Random();
        loadStocksFromDatabaseOrSeed();
    }

    public MarketUpdateService(Map<String, Stock> stockMap, List<MarketUpdate> marketUpdates, INotificationService notificationService) {
        this.stockMap = (stockMap != null) ? new HashMap<>(stockMap) : new HashMap<>();
        this.marketUpdates = (marketUpdates != null) ? new ArrayList<>(marketUpdates) : new ArrayList<>();
        this.notificationService = notificationService;
        this.random = new Random();
        if (this.stockMap.isEmpty()) {
            loadStocksFromDatabaseOrSeed();
        }
    }

    private void loadStocksFromDatabaseOrSeed() {
        try {
            List<Stock> fromDb = stockDAO.findAll();
            if (fromDb != null && !fromDb.isEmpty()) {
                for (Stock s : fromDb) {
                    stockMap.put(s.getSymbol(), s);
                }
                seedInitialUpdates();
                return;
            }
        } catch (Exception e) {
            System.err.println("[MarketUpdateService] DB stock load notification: " + e.getMessage());
        }
        seedDefaultStocks();
    }

    private void seedInitialUpdates() {
        if (marketUpdates.isEmpty()) {
            marketUpdates.add(new MarketUpdate(
                    "UPD-01", "RELIANCE", "Reliance Industries Limited", 2980.50, 42.50, 1.45,
                    "Record petrochemical and retail revenues reported in quarterly filing.", LocalDateTime.now().minusMinutes(25)
            ));
            marketUpdates.add(new MarketUpdate(
                    "UPD-02", "TCS", "Tata Consultancy Services Limited", 4245.00, -18.00, -0.42,
                    "IT sector consolidates following multi-year order wins.", LocalDateTime.now().minusMinutes(15)
            ));
            marketUpdates.add(new MarketUpdate(
                    "UPD-03", "INFY", "Infosys Limited", 1895.40, 24.10, 1.29,
                    "Strong international cloud consulting pipeline announced.", LocalDateTime.now().minusMinutes(5)
            ));
        }
    }

    private void seedDefaultStocks() {
        stockMap.put("RELIANCE", new Stock("RELIANCE", "Reliance Industries Limited", "NSE", 2980.50, 50000));
        stockMap.put("TCS", new Stock("TCS", "Tata Consultancy Services Limited", "NSE", 4245.00, 35000));
        stockMap.put("HDFCBANK", new Stock("HDFCBANK", "HDFC Bank Limited", "NSE", 1652.80, 60000));
        stockMap.put("INFY", new Stock("INFY", "Infosys Limited", "NSE", 1895.40, 45000));
        stockMap.put("ICICIBANK", new Stock("ICICIBANK", "ICICI Bank Limited", "NSE", 1280.20, 55000));
        stockMap.put("TATAMOTORS", new Stock("TATAMOTORS", "Tata Motors Limited", "NSE", 985.60, 40000));
        stockMap.put("SBIN", new Stock("SBIN", "State Bank of India", "NSE", 842.10, 75000));
        stockMap.put("BHARTIARTL", new Stock("BHARTIARTL", "Bharti Airtel Limited", "NSE", 1540.30, 30000));
        stockMap.put("ITC", new Stock("ITC", "ITC Limited", "NSE", 492.70, 80000));
        stockMap.put("KOTAKBANK", new Stock("KOTAKBANK", "Kotak Mahindra Bank Limited", "NSE", 1795.50, 25000));
        stockMap.put("LT", new Stock("LT", "Larsen & Toubro Limited", "NSE", 3650.00, 20000));
        stockMap.put("HINDUNILVR", new Stock("HINDUNILVR", "Hindustan Unilever Limited", "NSE", 2740.00, 30000));
        stockMap.put("BAJFINANCE", new Stock("BAJFINANCE", "Bajaj Finance Limited", "NSE", 7120.00, 15000));
        stockMap.put("MARUTI", new Stock("MARUTI", "Maruti Suzuki India Limited", "NSE", 12450.00, 8000));
        stockMap.put("SUNPHARMA", new Stock("SUNPHARMA", "Sun Pharmaceutical Industries Ltd.", "NSE", 1780.00, 22000));
        stockMap.put("WIPRO", new Stock("WIPRO", "Wipro Limited", "NSE", 535.80, 40000));
        stockMap.put("TATASTEEL", new Stock("TATASTEEL", "Tata Steel Limited", "NSE", 154.60, 100000));
        stockMap.put("VEDL", new Stock("VEDL", "Vedanta Limited", "BSE", 485.00, 50000));

        seedInitialUpdates();
    }

    @Override
    public synchronized void addStock(Stock stock) {
        if (stock != null) {
            String sym = stock.getSymbol().toUpperCase().trim();
            stockMap.put(sym, stock);
            try {
                stockDAO.insert(stock);
            } catch (Exception e) {
                System.err.println("[MarketUpdateService] Note: stock persisted in memory, DB notice: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void updateStock(Stock stock) {
        if (stock != null) {
            String sym = stock.getSymbol().toUpperCase().trim();
            stockMap.put(sym, stock);
            try {
                stockDAO.update(stock);
            } catch (Exception e) {
                System.err.println("[MarketUpdateService] Note: stock updated in memory, DB notice: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized boolean deleteStock(String symbol) {
        if (symbol == null) return false;
        String sym = symbol.toUpperCase().trim();
        Stock removed = stockMap.remove(sym);
        try {
            stockDAO.delete(sym);
        } catch (Exception e) {
            System.err.println("[MarketUpdateService] Note: stock deleted in memory, DB notice: " + e.getMessage());
        }
        return removed != null;
    }

    @Override
    public Stock getStock(String symbol) {
        if (symbol == null) return null;
        return stockMap.get(symbol.toUpperCase().trim());
    }

    @Override
    public List<Stock> getAllStocks() {
        return new ArrayList<>(stockMap.values());
    }

    @Override
    public synchronized void recordMarketUpdate(MarketUpdate update) {
        if (update != null) {
            marketUpdates.add(0, update); // Prepend
        }
    }

    @Override
    public List<MarketUpdate> getAllMarketUpdates() {
        return Collections.unmodifiableList(marketUpdates);
    }

    @Override
    public List<MarketUpdate> getMarketUpdatesForTrader(Trader trader) {
        if (trader == null) return getAllMarketUpdates();

        // If trader has a watchlist or preference, filter accordingly
        List<MarketUpdate> filtered = new ArrayList<>();
        boolean hasWatchlist = trader.getWatchlist() != null && !trader.getWatchlist().isEmpty();
        double minThreshold = trader.getAlertThresholdPercent();

        for (MarketUpdate up : marketUpdates) {
            boolean matchesWatchlist = !hasWatchlist || trader.getWatchlist().contains(up.getStockSymbol().toUpperCase());
            boolean matchesThreshold = Math.abs(up.getPriceChangePercent()) >= minThreshold;

            if (matchesWatchlist || matchesThreshold) {
                filtered.add(up);
            }
        }

        // If filtering results in empty, return all so the user still sees information
        if (filtered.isEmpty()) {
            return getAllMarketUpdates();
        }
        return filtered;
    }

    /**
     * Simulates a live market price tick across stocks with random delta (-2.5% to +2.5%).
     * Automatically triggers price alerts and logs a market update.
     */
    @Override
    public synchronized void simulateMarketTick() {
        List<Stock> stocks = new ArrayList<>(stockMap.values());
        if (stocks.isEmpty()) return;

        // Pick 1 to 3 random stocks to update
        int countToUpdate = Math.min(stocks.size(), 1 + random.nextInt(3));
        Collections.shuffle(stocks);

        for (int i = 0; i < countToUpdate; i++) {
            Stock stock = stocks.get(i);
            // Delta percentage between -2.5% and +2.5%
            double deltaPercent = (random.nextDouble() * 5.0) - 2.4;
            double oldPrice = stock.getCurrentPrice();
            double newPrice = Math.max(1.0, oldPrice * (1.0 + (deltaPercent / 100.0)));
            stock.updatePrice(newPrice);

            String headline = MARKET_HEADLINES[random.nextInt(MARKET_HEADLINES.length)];
            MarketUpdate update = new MarketUpdate(
                    "UPD-" + (marketUpdates.size() + 1),
                    stock.getSymbol(),
                    stock.getName(),
                    stock.getCurrentPrice(),
                    stock.getPriceChange(),
                    stock.getPriceChangePercent(),
                    headline,
                    LocalDateTime.now()
            );
            recordMarketUpdate(update);

            // Check alerts if notification service is present
            if (notificationService != null) {
                notificationService.checkAlertsAgainstStock(stock);
            }
        }
    }

    @Override
    public void setStocks(Map<String, Stock> stocks) {
        if (stocks != null) {
            this.stockMap = new HashMap<>(stocks);
        }
    }

    @Override
    public Map<String, Stock> getStockMap() {
        return stockMap;
    }
}
