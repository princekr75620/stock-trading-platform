package model;

import util.MoneyUtil;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing an executed financial trade transaction.
 * Stores monetary price, brokerage fee, and net total using BigDecimal precision
 * while preserving double getters/setters for seamless compatibility.
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
    private BigDecimal price = MoneyUtil.ZERO;
    private BigDecimal feeAmount = MoneyUtil.ZERO;
    private BigDecimal totalAmount = MoneyUtil.ZERO;
    private String idempotencyKey;
    private LocalDateTime transactionDate;

    public Transaction() {
        this.transactionDate = LocalDateTime.now();
    }

    public Transaction(String userId, int stockId, String symbol, String transactionType,
                       int quantity, double price, double totalAmount) {
        this(0, userId, stockId, symbol, transactionType, quantity,
                MoneyUtil.ofExact(price), MoneyUtil.ZERO, MoneyUtil.of(totalAmount),
                null, LocalDateTime.now());
    }

    public Transaction(int id, String userId, int stockId, String symbol, String transactionType,
                       int quantity, double price, double totalAmount, LocalDateTime transactionDate) {
        this(id, userId, stockId, symbol, transactionType, quantity,
                MoneyUtil.ofExact(price), MoneyUtil.ZERO, MoneyUtil.of(totalAmount),
                null, transactionDate);
    }

    public Transaction(String userId, int stockId, String symbol, String transactionType,
                       int quantity, BigDecimal price, BigDecimal feeAmount,
                       BigDecimal totalAmount, String idempotencyKey) {
        this(0, userId, stockId, symbol, transactionType, quantity,
                price, feeAmount, totalAmount, idempotencyKey, LocalDateTime.now());
    }

    public Transaction(int id, String userId, int stockId, String symbol, String transactionType,
                       int quantity, BigDecimal price, BigDecimal feeAmount,
                       BigDecimal totalAmount, String idempotencyKey, LocalDateTime transactionDate) {
        this.id = id;
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase() : "";
        this.transactionType = transactionType != null ? transactionType.toUpperCase() : "BUY";
        this.quantity = quantity;
        this.price = price != null ? MoneyUtil.round(price) : MoneyUtil.ZERO;
        this.feeAmount = feeAmount != null ? MoneyUtil.round(feeAmount) : MoneyUtil.ZERO;
        this.totalAmount = totalAmount != null ? MoneyUtil.round(totalAmount) : MoneyUtil.ZERO;
        this.idempotencyKey = (idempotencyKey != null && !idempotencyKey.trim().isEmpty())
                ? idempotencyKey.trim() : null;
        this.transactionDate = transactionDate != null ? transactionDate : LocalDateTime.now();
    }

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
        return price != null ? price.doubleValue() : 0.0;
    }

    public BigDecimal getPriceDecimal() {
        return price != null ? price : MoneyUtil.ZERO;
    }

    public void setPrice(double price) {
        this.price = MoneyUtil.of(price);
    }

    public void setPriceDecimal(BigDecimal price) {
        this.price = price != null ? MoneyUtil.round(price) : MoneyUtil.ZERO;
    }

    public double getFeeAmount() {
        return feeAmount != null ? feeAmount.doubleValue() : 0.0;
    }

    public BigDecimal getFeeAmountDecimal() {
        return feeAmount != null ? feeAmount : MoneyUtil.ZERO;
    }

    public void setFeeAmountDecimal(BigDecimal feeAmount) {
        this.feeAmount = feeAmount != null ? MoneyUtil.round(feeAmount) : MoneyUtil.ZERO;
    }

    public double getTotalAmount() {
        return totalAmount != null ? totalAmount.doubleValue() : 0.0;
    }

    public BigDecimal getTotalAmountDecimal() {
        return totalAmount != null ? totalAmount : MoneyUtil.ZERO;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = MoneyUtil.of(totalAmount);
    }

    public void setTotalAmountDecimal(BigDecimal totalAmount) {
        this.totalAmount = totalAmount != null ? MoneyUtil.round(totalAmount) : MoneyUtil.ZERO;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = (idempotencyKey != null && !idempotencyKey.trim().isEmpty())
                ? idempotencyKey.trim() : null;
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

    public Trade toTrade(String stockName) {
        TradeType type = "SELL".equalsIgnoreCase(transactionType) ? TradeType.SELL : TradeType.BUY;
        return new Trade(
                String.valueOf(id > 0 ? id : System.currentTimeMillis()),
                userId,
                symbol,
                stockName != null ? stockName : symbol,
                quantity,
                type,
                getPrice(),
                getTotalAmount(),
                transactionDate
        );
    }

    @Override
    public String toString() {
        return String.format("Transaction #%d | User: %s | %s %d %s @ ₹%.2f (Fee: ₹%.2f) | Total: ₹%.2f | %s",
                id, userId, transactionType, quantity, symbol, getPrice(), getFeeAmount(), getTotalAmount(), getFormattedDate());
    }
}
