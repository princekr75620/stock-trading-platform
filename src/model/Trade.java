package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents an executed trade transaction.
 * Contains Trade ID, Trader ID, Stock details, Quantity, Type (BUY/SELL), Price, Total Amount, and LocalDateTime.
 */
public class Trade implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String tradeId;
    private String traderId;
    private String stockSymbol;
    private String stockName;
    private int quantity;
    private TradeType tradeType;
    private double price;
    private double totalAmount;
    private LocalDateTime dateTime;

    public Trade(String tradeId, String traderId, String stockSymbol, String stockName,
                 int quantity, TradeType tradeType, double price, double totalAmount, LocalDateTime dateTime) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
        this.quantity = quantity;
        this.tradeType = tradeType;
        this.price = price;
        this.totalAmount = totalAmount;
        this.dateTime = dateTime;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getTraderId() {
        return traderId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public String getStockName() {
        return stockName;
    }

    public int getQuantity() {
        return quantity;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public double getPrice() {
        return price;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getFormattedDateTime() {
        return dateTime != null ? dateTime.format(FORMATTER) : "N/A";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return tradeId != null ? tradeId.equals(trade.tradeId) : trade.tradeId == null;
    }

    @Override
    public int hashCode() {
        return tradeId != null ? tradeId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return String.format("Trade #%s | %-4s | %-5s (%s) | Qty: %-4d | @$%.2f | Total: $%.2f | %s",
                tradeId, tradeType, stockSymbol, stockName, quantity, price, totalAmount, getFormattedDateTime());
    }
}
