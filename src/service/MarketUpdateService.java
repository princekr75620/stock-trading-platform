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
                    "UPD-01", "RELIANCE", "Reliance Industries Limited", 1324.40, 0.40, 0.03,
                    "Record petrochemical and retail revenues reported in quarterly filing.", LocalDateTime.now().minusMinutes(25)
            ));
            marketUpdates.add(new MarketUpdate(
                    "UPD-02", "TCS", "Tata Consultancy Services Limited", 2475.40, -23.40, -0.94,
                    "IT sector consolidates following multi-year order wins.", LocalDateTime.now().minusMinutes(15)
            ));
            marketUpdates.add(new MarketUpdate(
                    "UPD-03", "INFY", "Infosys Limited", 1154.10, -17.50, -1.49,
                    "Strong international cloud consulting pipeline announced.", LocalDateTime.now().minusMinutes(5)
            ));
        }
    }

    private void seedDefaultStocks() {
        stockMap.put("RELIANCE", new Stock("RELIANCE", "Reliance Industries Limited", "NSE", 1324.40, 50000));
        stockMap.put("TCS", new Stock("TCS", "Tata Consultancy Services Limited", "NSE", 2475.40, 35000));
        stockMap.put("HDFCBANK", new Stock("HDFCBANK", "HDFC Bank Limited", "NSE", 798.15, 60000));
        stockMap.put("INFY", new Stock("INFY", "Infosys Limited", "NSE", 1154.10, 45000));
        stockMap.put("ICICIBANK", new Stock("ICICIBANK", "ICICI Bank Limited", "NSE", 1589.00, 55000));
        stockMap.put("TATAMOTORS", new Stock("TATAMOTORS", "Tata Motors Limited", "NSE", 436.65, 40000));
        stockMap.put("SBIN", new Stock("SBIN", "State Bank of India", "NSE", 1042.00, 75000));
        stockMap.put("BHARTIARTL", new Stock("BHARTIARTL", "Bharti Airtel Limited", "NSE", 2072.10, 30000));
        stockMap.put("ITC", new Stock("ITC", "ITC Limited", "NSE", 278.80, 80000));
        stockMap.put("KOTAKBANK", new Stock("KOTAKBANK", "Kotak Mahindra Bank Limited", "NSE", 479.50, 25000));
        stockMap.put("LT", new Stock("LT", "Larsen & Toubro Limited", "NSE", 4185.00, 20000));
        stockMap.put("HINDUNILVR", new Stock("HINDUNILVR", "Hindustan Unilever Limited", "NSE", 1990.30, 30000));
        stockMap.put("BAJFINANCE", new Stock("BAJFINANCE", "Bajaj Finance Limited", "NSE", 1064.85, 15000));
        stockMap.put("MARUTI", new Stock("MARUTI", "Maruti Suzuki India Limited", "NSE", 12928.00, 8000));
        stockMap.put("SUNPHARMA", new Stock("SUNPHARMA", "Sun Pharmaceutical Industries Ltd.", "NSE", 1904.50, 22000));
        stockMap.put("WIPRO", new Stock("WIPRO", "Wipro Limited", "NSE", 199.00, 40000));
        stockMap.put("TATASTEEL", new Stock("TATASTEEL", "Tata Steel Limited", "NSE", 184.85, 100000));
        stockMap.put("VEDL", new Stock("VEDL", "Vedanta Limited", "BSE", 655.40, 50000));

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
     * Updates market prices using real-time Indian stock market feeds (NSE/BSE).
     * Automatically triggers price alerts and logs a market update.
     */
    @Override
    public synchronized void simulateMarketTick() {
        List<Stock> stocks = new ArrayList<>(stockMap.values());
        if (stocks.isEmpty()) return;

        // Select stocks to refresh with real live quotes
        int countToUpdate = Math.min(stocks.size(), 2 + random.nextInt(3));
        Collections.shuffle(stocks);

        for (int i = 0; i < countToUpdate; i++) {
            Stock stock = stocks.get(i);
            double oldPrice = stock.getCurrentPrice();
            double newPrice = oldPrice;

            // Attempt to fetch real live quote from NSE feed
            LiveMarketDataService.LiveQuote liveQuote = LiveMarketDataService.fetchLiveQuote(stock.getSymbol());
            if (liveQuote != null && liveQuote.price > 0) {
                newPrice = liveQuote.price;
                if (liveQuote.previousClose > 0) stock.setPreviousPrice(liveQuote.previousClose);
                if (liveQuote.dayHigh > 0) stock.setDayHigh(liveQuote.dayHigh);
                if (liveQuote.dayLow > 0) stock.setDayLow(liveQuote.dayLow);
                if (liveQuote.volume > 0) stock.setVolumeTraded(liveQuote.volume);
            } else {
                // Micro-tick variation around real market price (±0.4%)
                double deltaPercent = (random.nextDouble() * 0.8) - 0.4;
                newPrice = Math.round(Math.max(1.0, oldPrice * (1.0 + (deltaPercent / 100.0))) * 100.0) / 100.0;
            }

            stock.updatePrice(newPrice);

            // Persist updated real price into SQLite database
            try {
                stockDAO.update(stock);
            } catch (Exception ignored) {}

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
