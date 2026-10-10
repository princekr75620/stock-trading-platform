package model.dto;

import model.TradeType;
import java.io.Serializable;

/**
 * Data Transfer Object (DTO) for incoming equity trade orders.
 * Encapsulates parameters for order execution between Controller and Service layers.
 */
public class TradeOrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traderId;
    private String symbol;
    private TradeType tradeType;
    private int quantity;
    private double targetPrice;
    private long timestamp;

    public TradeOrderDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public TradeOrderDTO(String traderId, String symbol, TradeType tradeType, int quantity, double targetPrice) {
        this.traderId = traderId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : "";
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.targetPrice = targetPrice;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.toUpperCase().trim() : ""; }

    public TradeType getTradeType() { return tradeType; }
    public void setTradeType(TradeType tradeType) { this.tradeType = tradeType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getTargetPrice() { return targetPrice; }
    public void setTargetPrice(double targetPrice) { this.targetPrice = targetPrice; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double calculateEstimatedCost() {
        double subtotal = quantity * targetPrice;
        double fee = subtotal * 0.0015;
        return tradeType == TradeType.BUY ? subtotal + fee : subtotal - fee;
    }

    @Override
    public String toString() {
        return "TradeOrderDTO{" +
                "traderId='" + traderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", tradeType=" + tradeType +
                ", quantity=" + quantity +
                ", targetPrice=" + targetPrice +
                ", timestamp=" + timestamp +
                '}';
    }
}
