package model.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing real-time Indian stock market quote.
 */
public class StockQuoteDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbol;
    private String companyName;
    private String exchange;
    private double currentPrice;
    private double previousClose;
    private double change;
    private double percentChange;
    private double dayHigh;
    private double dayLow;
    private long volume;
    private int availableQuantity;
    private String status;

    public StockQuoteDTO() {}

    public StockQuoteDTO(String symbol, String companyName, String exchange, double currentPrice, double previousClose, int availableQuantity) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.exchange = exchange;
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.change = Math.round((currentPrice - previousClose) * 100.0) / 100.0;
        this.percentChange = previousClose > 0 ? Math.round((change / previousClose) * 10000.0) / 100.0 : 0.0;
        this.dayHigh = currentPrice;
        this.dayLow = currentPrice;
        this.volume = 0;
        this.availableQuantity = availableQuantity;
        this.status = "ACTIVE";
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getPercentChange() { return percentChange; }
    public void setPercentChange(double percentChange) { this.percentChange = percentChange; }

    public double getDayHigh() { return dayHigh; }
    public void setDayHigh(double dayHigh) { this.dayHigh = dayHigh; }

    public double getDayLow() { return dayLow; }
    public void setDayLow(double dayLow) { this.dayLow = dayLow; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
