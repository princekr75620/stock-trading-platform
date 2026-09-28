package server;

import model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lightweight pure-Java JSON serializer and parser for REST API communication.
 * Requires zero external dependencies, running cleanly on any Java SE 17+ runtime.
 */
public class JsonHelper {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        String clean = json.trim();
        if (clean.startsWith("{")) clean = clean.substring(1);
        if (clean.endsWith("}")) clean = clean.substring(0, clean.length() - 1);

        boolean inQuotes = false;
        StringBuilder curKey = new StringBuilder();
        StringBuilder curVal = new StringBuilder();
        boolean parsingKey = true;

        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '"' && (i == 0 || clean.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes) {
                if (c == ':') {
                    parsingKey = false;
                    continue;
                } else if (c == ',') {
                    String k = curKey.toString().trim().replace("\"", "");
                    String v = curVal.toString().trim().replace("\"", "");
                    if (!k.isEmpty()) {
                        map.put(k, v);
                    }
                    curKey.setLength(0);
                    curVal.setLength(0);
                    parsingKey = true;
                    continue;
                }
            }

            if (parsingKey) {
                curKey.append(c);
            } else {
                curVal.append(c);
            }
        }

        String k = curKey.toString().trim().replace("\"", "");
        String v = curVal.toString().trim().replace("\"", "");
        if (!k.isEmpty()) {
            map.put(k, v);
        }

        return map;
    }

    public static String userToJson(User u) {
        if (u == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"userId\":\"").append(escape(u.getUserId())).append("\",");
        sb.append("\"name\":\"").append(escape(u.getName())).append("\",");
        sb.append("\"email\":\"").append(escape(u.getEmail())).append("\",");
        sb.append("\"role\":\"").append(u.getRole().name()).append("\",");
        sb.append("\"createdAt\":\"").append(u.getCreatedAt().format(DTF)).append("\"");

        if (u instanceof Trader) {
            Trader t = (Trader) u;
            sb.append(",\"cashBalance\":").append(String.format(Locale.US, "%.2f", t.getCashBalance()));
            sb.append(",\"emailAlertsEnabled\":").append(t.isEmailAlertsEnabled());
            sb.append(",\"alertThresholdPercent\":").append(t.getAlertThresholdPercent());
            sb.append(",\"watchlist\":[");
            int idx = 0;
            for (String sym : t.getWatchlist()) {
                if (idx++ > 0) sb.append(",");
                sb.append("\"").append(escape(sym)).append("\"");
            }
            sb.append("]");
        } else if (u instanceof Admin) {
            Admin a = (Admin) u;
            sb.append(",\"department\":\"").append(escape(a.getDepartment())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String stockToJson(Stock s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"symbol\":\"").append(escape(s.getSymbol())).append("\",");
        sb.append("\"name\":\"").append(escape(s.getName())).append("\",");
        sb.append("\"exchange\":\"").append(escape(s.getExchange())).append("\",");
        sb.append("\"status\":\"").append(escape(s.getStatus())).append("\",");
        sb.append("\"currentPrice\":").append(String.format(Locale.US, "%.2f", s.getCurrentPrice())).append(",");
        sb.append("\"previousPrice\":").append(String.format(Locale.US, "%.2f", s.getPreviousPrice())).append(",");
        sb.append("\"priceChange\":").append(String.format(Locale.US, "%.2f", s.getPriceChange())).append(",");
        sb.append("\"priceChangePercent\":").append(String.format(Locale.US, "%.2f", s.getPriceChangePercent())).append(",");
        sb.append("\"availableQuantity\":").append(s.getAvailableQuantity()).append(",");
        sb.append("\"dayHigh\":").append(String.format(Locale.US, "%.2f", s.getDayHigh())).append(",");
        sb.append("\"dayLow\":").append(String.format(Locale.US, "%.2f", s.getDayLow())).append(",");
        sb.append("\"volumeTraded\":").append(s.getVolumeTraded());
        sb.append("}");
        return sb.toString();
    }

    public static String tradeToJson(Trade t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tradeId\":\"").append(escape(t.getTradeId())).append("\",");
        sb.append("\"traderId\":\"").append(escape(t.getTraderId())).append("\",");
        sb.append("\"stockSymbol\":\"").append(escape(t.getStockSymbol())).append("\",");
        sb.append("\"stockName\":\"").append(escape(t.getStockName())).append("\",");
        sb.append("\"quantity\":").append(t.getQuantity()).append(",");
        sb.append("\"tradeType\":\"").append(t.getTradeType().name()).append("\",");
        sb.append("\"price\":").append(String.format(Locale.US, "%.2f", t.getPrice())).append(",");
        sb.append("\"totalAmount\":").append(String.format(Locale.US, "%.2f", t.getTotalAmount())).append(",");
        sb.append("\"dateTime\":\"").append(t.getFormattedDateTime()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String notificationToJson(Notification n) {
        if (n == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"notificationId\":\"").append(escape(n.getNotificationId())).append("\",");
        sb.append("\"recipientUserId\":\"").append(escape(n.getRecipientId())).append("\",");
        sb.append("\"title\":\"").append(escape(n.getTitle())).append("\",");
        sb.append("\"message\":\"").append(escape(n.getMessage())).append("\",");
        sb.append("\"type\":\"").append(n.getType().name()).append("\",");
        sb.append("\"isRead\":").append(n.isRead()).append(",");
        sb.append("\"timestamp\":\"").append(n.getFormattedTimestamp()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String marketUpdateToJson(MarketUpdate u) {
        if (u == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"updateId\":\"").append(escape(u.getUpdateId())).append("\",");
        sb.append("\"stockSymbol\":\"").append(escape(u.getStockSymbol())).append("\",");
        sb.append("\"stockName\":\"").append(escape(u.getStockName())).append("\",");
        sb.append("\"headline\":\"").append(escape(u.getMarketMessage())).append("\",");
        sb.append("\"currentPrice\":").append(String.format(Locale.US, "%.2f", u.getCurrentPrice())).append(",");
        sb.append("\"priceDelta\":").append(String.format(Locale.US, "%.2f", u.getPriceChange())).append(",");
        sb.append("\"percentChange\":").append(String.format(Locale.US, "%.2f", u.getPriceChangePercent())).append(",");
        sb.append("\"timestamp\":\"").append(u.getDateTime().format(DTF)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String securitySettingsToJson(SecuritySettings s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"minimumPasswordLength\":").append(s.getMinPasswordLength()).append(",");
        sb.append("\"requireSpecialCharacters\":").append(s.isRequireSpecialChar()).append(",");
        sb.append("\"passwordExpiryDays\":").append(s.getPasswordExpiryDays()).append(",");
        sb.append("\"maxFailedLoginAttempts\":").append(s.getMaxFailedLoginAttempts()).append(",");
        sb.append("\"sessionTimeoutMinutes\":").append(s.getSessionTimeoutMinutes()).append(",");
        sb.append("\"autoLockoutEnabled\":").append(s.isAutomaticAccountLockout()).append(",");
        sb.append("\"highValueTradeThreshold\":").append(String.format(Locale.US, "%.2f", s.getHighValueTradeThreshold())).append(",");
        sb.append("\"requireTwoStepForHighValue\":").append(s.isRequireTwoStepForHighValueTrades()).append(",");
        sb.append("\"ipAnomalyDetectionEnabled\":").append(s.isIpAnomalyDetection());
        sb.append("}");
        return sb.toString();
    }

    public static String securityIncidentToJson(SecurityIncident i) {
        if (i == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"incidentId\":\"").append(escape(i.getIncidentId())).append("\",");
        sb.append("\"userId\":\"").append(escape(i.getAffectedUserOrIp())).append("\",");
        sb.append("\"incidentType\":\"").append(escape(i.getIncidentType())).append("\",");
        sb.append("\"description\":\"").append(escape(i.getDescription())).append("\",");
        sb.append("\"severity\":\"").append(i.getSeverity().name()).append("\",");
        sb.append("\"status\":\"").append(i.getStatus().name()).append("\",");
        sb.append("\"resolved\":").append(i.getStatus() == SecurityIncident.Status.RESOLVED).append(",");
        sb.append("\"timestamp\":\"").append(i.getTimestamp().format(DTF)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String systemSettingsToJson(SystemSettings s, Map<String, String> metrics) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"exchangeName\":\"").append(escape(s.getExchangeName())).append("\",");
        sb.append("\"tradingFeePercent\":").append(String.format(Locale.US, "%.2f", s.getTradingFeePercentage())).append(",");
        sb.append("\"tradingStatus\":\"").append(s.getMarketStatus().name()).append("\",");
        sb.append("\"maintenanceMode\":").append(s.isMaintenanceMode()).append(",");
        sb.append("\"marketUpdateIntervalSeconds\":").append(s.getMarketUpdateIntervalSeconds()).append(",");
        sb.append("\"systemVersion\":\"").append(escape(s.getSystemVersion())).append("\",");
        sb.append("\"maxConcurrentUsers\":").append(s.getMaxConcurrentUsers()).append(",");
        sb.append("\"systemStatus\":\"").append(s.isMaintenanceMode() ? "MAINTENANCE" : (s.getMarketStatus() == SystemSettings.MarketStatus.OPEN ? "OPERATIONAL" : s.getMarketStatus().name())).append("\"");

        if (metrics != null) {
            sb.append(",\"metrics\":{");
            int idx = 0;
            for (Map.Entry<String, String> e : metrics.entrySet()) {
                if (idx++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append("\"");
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String portfolioToJson(Portfolio p, Map<String, Stock> marketStocks) {
        return portfolioToJson(p, null, marketStocks);
    }

    public static String stocksToJson(List<Stock> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(stockToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String tradesToJson(List<Trade> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(tradeToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String usersToJson(List<User> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(userToJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String portfolioToJson(Portfolio p, Trader trader, Map<String, Stock> marketStocks) {
        if (p == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"traderId\":\"").append(escape(p.getTraderId())).append("\",");
        double cash = (trader != null) ? trader.getCashBalance() : 0.0;
        double equity = p.getCurrentPortfolioValue(marketStocks);
        double costBasis = p.getTotalInvestmentValue();
        double unrealizedPL = p.getUnrealizedProfitLoss(marketStocks);
        double unrealizedPLPct = p.getUnrealizedProfitLossPercent(marketStocks);
        double realizedPL = p.getRealizedProfitLoss();
        double netWorth = cash + equity;

        sb.append("\"cashBalance\":").append(String.format(Locale.US, "%.2f", cash)).append(",");
        sb.append("\"currentPortfolioValue\":").append(String.format(Locale.US, "%.2f", equity)).append(",");
        sb.append("\"totalInvestmentValue\":").append(String.format(Locale.US, "%.2f", costBasis)).append(",");
        sb.append("\"unrealizedProfitLoss\":").append(String.format(Locale.US, "%.2f", unrealizedPL)).append(",");
        sb.append("\"unrealizedProfitLossPercent\":").append(String.format(Locale.US, "%.2f", unrealizedPLPct)).append(",");
        sb.append("\"realizedProfitLoss\":").append(String.format(Locale.US, "%.2f", realizedPL)).append(",");
        sb.append("\"totalNetWorth\":").append(String.format(Locale.US, "%.2f", netWorth)).append(",");
        sb.append("\"holdingsCount\":").append(p.getHoldings().size()).append(",");

        sb.append("\"holdings\":[");
        int idx = 0;
        for (Map.Entry<String, Integer> entry : p.getHoldings().entrySet()) {
            String sym = entry.getKey();
            int qty = entry.getValue();
            if (qty <= 0) continue;
            if (idx++ > 0) sb.append(",");

            Stock st = marketStocks.get(sym);
            if (st == null) {
                st = marketStocks.get(sym.toUpperCase().trim());
            }
            if (st == null) {
                for (Stock item : marketStocks.values()) {
                    if (item.getSymbol().equalsIgnoreCase(sym)) {
                        st = item;
                        break;
                    }
                }
            }
            double curPrice = (st != null) ? st.getCurrentPrice() : 0.0;
            String name = (st != null) ? st.getName() : sym;
            double avgCost = p.getAverageBuyPrice(sym);
            double invValue = avgCost * qty;
            double curVal = curPrice * qty;
            double pl = curVal - invValue;
            double plPct = (invValue > 0) ? (pl / invValue) * 100.0 : 0.0;

            sb.append("{");
            sb.append("\"symbol\":\"").append(escape(sym)).append("\",");
            sb.append("\"name\":\"").append(escape(name)).append("\",");
            sb.append("\"quantity\":").append(qty).append(",");
            sb.append("\"avgPurchasePrice\":").append(String.format(Locale.US, "%.2f", avgCost)).append(",");
            sb.append("\"currentPrice\":").append(String.format(Locale.US, "%.2f", curPrice)).append(",");
            sb.append("\"investmentValue\":").append(String.format(Locale.US, "%.2f", invValue)).append(",");
            sb.append("\"currentValue\":").append(String.format(Locale.US, "%.2f", curVal)).append(",");
            sb.append("\"profitLoss\":").append(String.format(Locale.US, "%.2f", pl)).append(",");
            sb.append("\"profitLossPercent\":").append(String.format(Locale.US, "%.2f", plPct));
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }
}
