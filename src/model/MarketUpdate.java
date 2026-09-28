package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a market update event or news alert for a specific stock.
 * Contains symbol, stock name, current price, price change, percentage change, market message, and timestamp.
 */
public class MarketUpdate implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String updateId;
    private String stockSymbol;
    private String stockName;
    private double currentPrice;
    private double priceChange;
    private double priceChangePercent;
    private String marketMessage;
    private LocalDateTime dateTime;

    public MarketUpdate(String updateId, String stockSymbol, String stockName, double currentPrice,
                        double priceChange, double priceChangePercent, String marketMessage, LocalDateTime dateTime) {
        this.updateId = updateId;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
        this.currentPrice = currentPrice;
        this.priceChange = priceChange;
        this.priceChangePercent = priceChangePercent;
        this.marketMessage = marketMessage;
        this.dateTime = dateTime;
    }

    public String getUpdateId() {
        return updateId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public String getStockName() {
        return stockName;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getPriceChange() {
        return priceChange;
    }

    public double getPriceChangePercent() {
        return priceChangePercent;
    }

    public String getMarketMessage() {
        return marketMessage;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getFormattedDateTime() {
        return dateTime != null ? dateTime.format(FORMATTER) : "N/A";
    }

    @Override
    public String toString() {
        String sign = priceChange >= 0 ? "+" : "";
        return String.format("[%s] %s (%s): $%.2f (%s%.2f / %s%.2f%%) | News: %s",
                getFormattedDateTime(), stockSymbol, stockName, currentPrice, sign, priceChange, sign, priceChangePercent, marketMessage);
    }
}
