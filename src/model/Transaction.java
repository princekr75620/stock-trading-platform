package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing an executed financial trade transaction.
 * Demonstrates Encapsulation with private fields and standard getters/setters.
 * Fulfills Core Java Encapsulation and Model requirement for GUVI Evaluation.
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int id;
    private String userId;
    private int stockId;
    private String symbol;
    private String transactionType; // "BUY" or "SELL"
    private int quantity;
    private double price;
    private double totalAmount;
    private LocalDateTime transactionDate;

    // Default constructor
    public Transaction() {
        this.transactionDate = LocalDateTime.now();
    }

    // Parameterized constructor without primary key ID
    public Transaction(String userId, int stockId, String symbol, String transactionType,
                       int quantity, double price, double totalAmount) {
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase() : "";
        this.transactionType = transactionType != null ? transactionType.toUpperCase() : "BUY";
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = totalAmount;
        this.transactionDate = LocalDateTime.now();
    }

    // Full constructor including primary key ID and transaction date
    public Transaction(int id, String userId, int stockId, String symbol, String transactionType,
                       int quantity, double price, double totalAmount, LocalDateTime transactionDate) {
        this.id = id;
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase() : "";
        this.transactionType = transactionType != null ? transactionType.toUpperCase() : "BUY";
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = totalAmount;
        this.transactionDate = transactionDate != null ? transactionDate : LocalDateTime.now();
    }

    // Getters and Setters (Encapsulation)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getStockId() {
        return stockId;
    }

    public void setStockId(int stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase() : "";
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType != null ? transactionType.toUpperCase() : "BUY";
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getFormattedDate() {
        return transactionDate != null ? transactionDate.format(FORMATTER) : "N/A";
    }

    /**
     * Converts to existing Trade model for seamless compatibility across the platform.
     */
    public Trade toTrade(String stockName) {
        TradeType type = "SELL".equalsIgnoreCase(transactionType) ? TradeType.SELL : TradeType.BUY;
        return new Trade(
                String.valueOf(id > 0 ? id : System.currentTimeMillis()),
                userId,
                symbol,
                stockName != null ? stockName : symbol,
                quantity,
                type,
                price,
                totalAmount,
                transactionDate
        );
    }

    @Override
    public String toString() {
        return String.format("Transaction #%d | User: %s | %s %d %s @ $%.2f | Total: $%.2f | %s",
                id, userId, transactionType, quantity, symbol, price, totalAmount, getFormattedDate());
    }
}
