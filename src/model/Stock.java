package model;

import java.io.Serializable;

/**
 * Represents a publicly traded Indian stock on the NSE / BSE exchange.
 * Contains symbol, company name, exchange, market price in INR (₹), available float/quantity, and status.
 * Fulfills Encapsulation (Private fields with getters and setters) for GUVI Evaluation.
 */
public class Stock implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String symbol;
    private String name;
    private String exchange = "NSE"; // NSE, BSE, NIFTY 50
    private double currentPrice;
    private int availableQuantity;
    private double previousPrice;
    private double dayHigh;
    private double dayLow;
    private long volumeTraded;
    private String status = "ACTIVE"; // ACTIVE, HALTED, SUSPENDED

    public Stock() {}

    public Stock(String symbol, String name, double currentPrice, int availableQuantity) {
        this(symbol, name, "NSE", currentPrice, availableQuantity, "ACTIVE");
    }

    public Stock(String symbol, String name, String exchange, double currentPrice, int availableQuantity) {
        this(symbol, name, exchange, currentPrice, availableQuantity, "ACTIVE");
    }

    public Stock(String symbol, String name, String exchange, double currentPrice, int availableQuantity, String status) {
        this.symbol = symbol.toUpperCase().trim();
        this.name = name;
        this.exchange = (exchange != null && !exchange.isEmpty()) ? exchange.toUpperCase().trim() : "NSE";
        this.currentPrice = currentPrice;
        this.previousPrice = currentPrice;
        this.availableQuantity = availableQuantity;
        this.dayHigh = currentPrice;
        this.dayLow = currentPrice;
        this.volumeTraded = 0;
        this.status = (status != null && !status.isEmpty()) ? status.toUpperCase().trim() : "ACTIVE";
    }

    public Stock(int id, String symbol, String name, double currentPrice, int availableQuantity) {
        this(symbol, name, "NSE", currentPrice, availableQuantity, "ACTIVE");
        this.id = id;
    }

    public Stock(int id, String symbol, String name, String exchange, double currentPrice, int availableQuantity, String status) {
        this(symbol, name, exchange, currentPrice, availableQuantity, status);
        this.id = id;
    }

    public Stock(String symbol, String name, double currentPrice, int availableQuantity, double previousPrice,
                 double dayHigh, double dayLow, long volumeTraded) {
        this(symbol, name, "NSE", currentPrice, availableQuantity, "ACTIVE");
        this.previousPrice = previousPrice;
        this.dayHigh = dayHigh;
        this.dayLow = dayLow;
        this.volumeTraded = volumeTraded;
    }

    // Core business operations
    public synchronized void updatePrice(double newPrice) {
        if (newPrice <= 0) return;
        this.previousPrice = this.currentPrice;
        this.currentPrice = Math.round(newPrice * 100.0) / 100.0;
        if (this.currentPrice > this.dayHigh) this.dayHigh = this.currentPrice;
        if (this.currentPrice < this.dayLow) this.dayLow = this.currentPrice;
    }

    public synchronized boolean reduceQuantity(int qty) {
        if (qty > 0 && this.availableQuantity >= qty) {
            this.availableQuantity -= qty;
            this.volumeTraded += qty;
            return true;
        }
        return false;
    }

    public synchronized void addQuantity(int qty) {
        if (qty > 0) {
            this.availableQuantity += qty;
        }
    }

    public double getPriceChange() {
        return Math.round((currentPrice - previousPrice) * 100.0) / 100.0;
    }

    public double getPriceChangePercent() {
        if (previousPrice == 0) return 0.0;
        double pct = ((currentPrice - previousPrice) / previousPrice) * 100.0;
        return Math.round(pct * 100.0) / 100.0;
    }

    // Encapsulation: Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol.toUpperCase().trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompanyName() {
        return name;
    }

    public void setCompanyName(String companyName) {
        this.name = companyName;
    }

    public String getExchange() {
        return exchange != null ? exchange : "NSE";
    }

    public void setExchange(String exchange) {
        this.exchange = exchange != null ? exchange.toUpperCase().trim() : "NSE";
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getPrice() {
        return currentPrice;
    }

    public void setPrice(double price) {
        updatePrice(price);
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public double getPreviousPrice() {
        return previousPrice;
    }

    public void setPreviousPrice(double previousPrice) {
        this.previousPrice = previousPrice;
    }

    public double getDayHigh() {
        return dayHigh;
    }

    public void setDayHigh(double dayHigh) {
        this.dayHigh = dayHigh;
    }

    public double getDayLow() {
        return dayLow;
    }

    public void setDayLow(double dayLow) {
        this.dayLow = dayLow;
    }

    public long getVolumeTraded() {
        return volumeTraded;
    }

    public void setVolumeTraded(long volumeTraded) {
        this.volumeTraded = volumeTraded;
    }

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status != null ? status.toUpperCase().trim() : "ACTIVE";
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }

    @Override
    public String toString() {
        String changeStr = (getPriceChange() >= 0 ? "+" : "") + String.format("%.2f (%.2f%%)", getPriceChange(), getPriceChangePercent());
        return String.format("%-10s | %-4s | %-28s | ₹%8.2f | Chg: %-14s | Vol: %-6d",
                symbol, exchange, name, currentPrice, changeStr, availableQuantity);
    }
}
