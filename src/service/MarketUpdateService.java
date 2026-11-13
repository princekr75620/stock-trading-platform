package service;

import dao.StockDAO;
import model.MarketUpdate;
import model.Stock;
import model.Trader;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service managing live market quote updates, news feed cache, and authoritative Stock catalog persistence via StockDAO.
 * Financial equity master records live in the JDBC 'stocks' table; only transient news headlines
 * and quote simulation ticks are cached in memory and flushed back to StockDAO.
 */
public class MarketUpdateService implements IMarketUpdateService {
    private final boolean jdbcAuthoritative;
    private final Map<String, Stock> isolatedTestStocks;
    // In-memory transient market-ticker headline feed cache (non-financial audit stream)
    private final List<MarketUpdate> marketUpdates;
    private final INotificationService notificationService;
    private final Random random;
    private final StockDAO stockDAO = new StockDAO();

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
        this.jdbcAuthoritative = true;
        this.isolatedTestStocks = null;
        this.marketUpdates = new ArrayList<>();
        this.notificationService = notificationService;
        this.random = new Random();
        seedInitialUpdates();
    }

    public MarketUpdateService(Map<String, Stock> stockMap, List<MarketUpdate> marketUpdates, INotificationService notificationService) {
        this.jdbcAuthoritative = false;
        this.isolatedTestStocks = (stockMap != null) ? new HashMap<>(stockMap) : new HashMap<>();
        this.marketUpdates = (marketUpdates != null) ? new ArrayList<>(marketUpdates) : new ArrayList<>();
        this.notificationService = notificationService;
        this.random = new Random();
        seedInitialUpdates();
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

    @Override
    public synchronized void addStock(Stock stock) {
        if (stock == null) return;
        String sym = stock.getSymbol().toUpperCase().trim();
        stock.setSymbol(sym);
        if (!jdbcAuthoritative) {
            isolatedTestStocks.put(sym, stock);
            return;
        }
        try {
            if (!stockDAO.insert(stock)) {
                throw new RuntimeException("Failed to insert stock into database: " + sym);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error adding stock '" + sym + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void updateStock(Stock stock) {
        if (stock == null) return;
        String sym = stock.getSymbol().toUpperCase().trim();
        stock.setSymbol(sym);
        if (!jdbcAuthoritative) {
            isolatedTestStocks.put(sym, stock);
            return;
        }
        try {
            if (!stockDAO.update(stock)) {
                throw new RuntimeException("Failed to update stock in database: " + sym);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error updating stock '" + sym + "': " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized boolean deleteStock(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return false;
        String sym = symbol.toUpperCase().trim();
        if (!jdbcAuthoritative) {
            return isolatedTestStocks.remove(sym) != null;
        }
        try {
            return stockDAO.delete(sym);
        } catch (SQLException e) {
            throw new RuntimeException("Database error deleting stock '" + sym + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Stock getStock(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return null;
        String sym = symbol.toUpperCase().trim();
        if (!jdbcAuthoritative) {
            return isolatedTestStocks.get(sym);
        }
        try {
            return stockDAO.findBySymbol(sym);
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching stock '" + sym + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<Stock> getAllStocks() {
        if (!jdbcAuthoritative) {
            return new ArrayList<>(isolatedTestStocks.values());
        }
        try {
            return stockDAO.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching stocks: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void recordMarketUpdate(MarketUpdate update) {
        if (update != null) {
            marketUpdates.add(0, update);
            if (marketUpdates.size() > 100) {
                marketUpdates.subList(100, marketUpdates.size()).clear();
            }
        }
    }

    @Override
    public List<MarketUpdate> getAllMarketUpdates() {
        return Collections.unmodifiableList(marketUpdates);
    }

    @Override
    public List<MarketUpdate> getMarketUpdatesForTrader(Trader trader) {
        if (trader == null) return getAllMarketUpdates();

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

        if (filtered.isEmpty()) {
            return getAllMarketUpdates();
        }
        return filtered;
    }

    /**
     * Updates market prices using real-time Indian stock market feeds (NSE/BSE)
     * with micro-tick fallback and persists updated spot quotes to StockDAO.
     */
    @Override
    public synchronized void simulateMarketTick() {
        List<Stock> stocks = getAllStocks();
        if (stocks.isEmpty()) return;

        int countToUpdate = Math.min(stocks.size(), 2 + random.nextInt(3));
        Collections.shuffle(stocks);

        for (int i = 0; i < countToUpdate; i++) {
            Stock stock = stocks.get(i);
            double oldPrice = stock.getCurrentPrice();
            double newPrice = oldPrice;

            LiveMarketDataService.LiveQuote liveQuote = LiveMarketDataService.fetchLiveQuote(stock.getSymbol());
            if (liveQuote != null && liveQuote.price > 0) {
                newPrice = liveQuote.price;
                if (liveQuote.previousClose > 0) stock.setPreviousPrice(liveQuote.previousClose);
                if (liveQuote.dayHigh > 0) stock.setDayHigh(liveQuote.dayHigh);
                if (liveQuote.dayLow > 0) stock.setDayLow(liveQuote.dayLow);
                if (liveQuote.volume > 0) stock.setVolumeTraded(liveQuote.volume);
            } else {
                double deltaPercent = (random.nextDouble() * 0.8) - 0.4;
                newPrice = Math.round(Math.max(1.0, oldPrice * (1.0 + (deltaPercent / 100.0))) * 100.0) / 100.0;
            }

            stock.updatePrice(newPrice);
            updateStock(stock);

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

            if (notificationService != null) {
                notificationService.checkAlertsAgainstStock(stock);
            }
        }
    }

    @Override
    public void setStocks(Map<String, Stock> stocks) {
        if (!jdbcAuthoritative && stocks != null) {
            isolatedTestStocks.clear();
            isolatedTestStocks.putAll(stocks);
        }
    }

    @Override
    public Map<String, Stock> getStockMap() {
        if (!jdbcAuthoritative) {
            return isolatedTestStocks;
        }
        try {
            return stockDAO.findAllAsMap();
        } catch (SQLException e) {
            throw new RuntimeException("Database error building stock map: " + e.getMessage(), e);
        }
    }
}
