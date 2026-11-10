package service;

import model.Stock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live Market Data Provider integrating real-time NSE/BSE stock quotes
 * from the live financial market data feed.
 * Features:
 * - Fetches real regularMarketPrice, previousClose, dayHigh, dayLow, volume
 * - Resilient connection handling with fallback to cached database quotes
 * - Concurrency-safe in-memory cache to respect API rate limits
 */
public class LiveMarketDataService {

    // NSE Ticker mapping for Indian equities
    private static final Map<String, String> SYMBOL_MAPPING = new HashMap<>();

    static {
        SYMBOL_MAPPING.put("RELIANCE", "RELIANCE.NS");
        SYMBOL_MAPPING.put("TCS", "TCS.NS");
        SYMBOL_MAPPING.put("HDFCBANK", "HDFCBANK.NS");
        SYMBOL_MAPPING.put("INFY", "INFY.NS");
        SYMBOL_MAPPING.put("ICICIBANK", "ICICIBANK.NS");
        SYMBOL_MAPPING.put("TATAMOTORS", "TMCV.NS");
        SYMBOL_MAPPING.put("SBIN", "SBIN.NS");
        SYMBOL_MAPPING.put("BHARTIARTL", "BHARTIARTL.NS");
        SYMBOL_MAPPING.put("ITC", "ITC.NS");
        SYMBOL_MAPPING.put("KOTAKBANK", "KOTAKBANK.NS");
        SYMBOL_MAPPING.put("LT", "LT.NS");
        SYMBOL_MAPPING.put("HINDUNILVR", "HINDUNILVR.NS");
        SYMBOL_MAPPING.put("BAJFINANCE", "BAJFINANCE.NS");
        SYMBOL_MAPPING.put("MARUTI", "MARUTI.NS");
        SYMBOL_MAPPING.put("SUNPHARMA", "SUNPHARMA.NS");
        SYMBOL_MAPPING.put("ASIANPAINT", "ASIANPAINT.NS");
        SYMBOL_MAPPING.put("TITAN", "TITAN.NS");
        SYMBOL_MAPPING.put("ADANIENT", "ADANIENT.NS");
        SYMBOL_MAPPING.put("NTPC", "NTPC.NS");
        SYMBOL_MAPPING.put("POWERGRID", "POWERGRID.NS");
        SYMBOL_MAPPING.put("WIPRO", "WIPRO.NS");
        SYMBOL_MAPPING.put("HCLTECH", "HCLTECH.NS");
        SYMBOL_MAPPING.put("TATASTEEL", "TATASTEEL.NS");
        SYMBOL_MAPPING.put("AXISBANK", "AXISBANK.NS");
        SYMBOL_MAPPING.put("ONGC", "ONGC.NS");
        SYMBOL_MAPPING.put("COALINDIA", "COALINDIA.NS");
        SYMBOL_MAPPING.put("ULTRACEMCO", "ULTRACEMCO.NS");
        SYMBOL_MAPPING.put("BAJAJFINSV", "BAJAJFINSV.NS");
        SYMBOL_MAPPING.put("NESTLEIND", "NESTLEIND.NS");
        SYMBOL_MAPPING.put("TECHM", "TECHM.NS");
        SYMBOL_MAPPING.put("JSWSTEEL", "JSWSTEEL.NS");
        SYMBOL_MAPPING.put("GRASIM", "GRASIM.NS");
        SYMBOL_MAPPING.put("INDUSINDBK", "INDUSINDBK.NS");
        SYMBOL_MAPPING.put("CIPLA", "CIPLA.NS");
        SYMBOL_MAPPING.put("HEROMOTOCO", "HEROMOTOCO.NS");
        SYMBOL_MAPPING.put("DRREDDY", "DRREDDY.NS");
        SYMBOL_MAPPING.put("EICHERMOT", "EICHERMOT.NS");
        SYMBOL_MAPPING.put("DIVISLAB", "DIVISLAB.NS");
        SYMBOL_MAPPING.put("BRITANNIA", "BRITANNIA.NS");
        SYMBOL_MAPPING.put("TATACONSUM", "TATACONSUM.NS");
        SYMBOL_MAPPING.put("APOLLOHOSP", "APOLLOHOSP.NS");
        SYMBOL_MAPPING.put("SBILIFE", "SBILIFE.NS");
        SYMBOL_MAPPING.put("HDFCLIFE", "HDFCLIFE.NS");
        SYMBOL_MAPPING.put("BPCL", "BPCL.NS");
        SYMBOL_MAPPING.put("HINDALCO", "HINDALCO.NS");
        SYMBOL_MAPPING.put("ETERNAL", "ETERNAL.NS");
        SYMBOL_MAPPING.put("ZOMATO", "ETERNAL.NS");
        SYMBOL_MAPPING.put("JIOFIN", "JIOFIN.NS");
        SYMBOL_MAPPING.put("VEDL", "VEDL.NS");
        SYMBOL_MAPPING.put("BEL", "BEL.NS");
    }

    public static class LiveQuote {
        public double price;
        public double previousClose;
        public double dayHigh;
        public double dayLow;
        public long volume;
        public long timestamp;

        public LiveQuote(double price, double previousClose, double dayHigh, double dayLow, long volume) {
            this.price = Math.round(price * 100.0) / 100.0;
            this.previousClose = Math.round(previousClose * 100.0) / 100.0;
            this.dayHigh = Math.round(dayHigh * 100.0) / 100.0;
            this.dayLow = Math.round(dayLow * 100.0) / 100.0;
            this.volume = volume;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // Cache to prevent hitting rate limits
    private static final Map<String, LiveQuote> CACHE = new HashMap<>();
    private static final long CACHE_TTL_MS = 15000; // 15 seconds

    /**
     * Resolves the market ticker for a given symbol.
     */
    public static String getMarketTicker(String symbol) {
        if (symbol == null) return null;
        String upper = symbol.toUpperCase().trim();
        return SYMBOL_MAPPING.getOrDefault(upper, upper + ".NS");
    }

    /**
     * Fetches real-time quote for an Indian stock symbol.
     * Returns LiveQuote object or null if network error / not found.
     */
    public static synchronized LiveQuote fetchLiveQuote(String symbol) {
        if (symbol == null) return null;
        String upper = symbol.toUpperCase().trim();

        // Check cache
        LiveQuote cached = CACHE.get(upper);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            return cached;
        }

        String ticker = getMarketTicker(upper);
        try {
            String urlStr = "https://query2.finance.yahoo.com/v8/finance/chart/" + ticker + "?interval=1d&range=1d";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int status = conn.getResponseCode();
            if (status != 200) {
                conn.disconnect();
                return cached;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();

            String json = sb.toString();

            // Extract values using regex without heavy dependencies
            double price = extractDouble(json, "\"regularMarketPrice\":([0-9.]+)");
            if (price <= 0) {
                return cached;
            }

            double prevClose = extractDouble(json, "\"chartPreviousClose\":([0-9.]+)");
            if (prevClose <= 0) prevClose = price;

            double dayHigh = extractDouble(json, "\"regularMarketDayHigh\":([0-9.]+)");
            if (dayHigh <= 0) dayHigh = price;

            double dayLow = extractDouble(json, "\"regularMarketDayLow\":([0-9.]+)");
            if (dayLow <= 0) dayLow = price;

            long vol = extractLong(json, "\"regularMarketVolume\":([0-9]+)");

            LiveQuote quote = new LiveQuote(price, prevClose, dayHigh, dayLow, vol);
            CACHE.put(upper, quote);
            return quote;

        } catch (Exception e) {
            // Silently return cached or null on rate limit or network offline
            return cached;
        }
    }

    private static double extractDouble(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private static long extractLong(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0L;
    }
}
