package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents a price or market alert configured by a Trader.
 */
public class Alert implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum AlertCondition {
        PRICE_ABOVE,
        PRICE_BELOW,
        PERCENT_CHANGE_ABOVE
    }

    private String alertId;
    private String traderId;
    private String stockSymbol;
    private double targetValue;
    private AlertCondition condition;
    private boolean isTriggered;
    private LocalDateTime createdAt;
    private LocalDateTime triggeredAt;

    public Alert(String alertId, String traderId, String stockSymbol, double targetValue, AlertCondition condition) {
        this.alertId = alertId;
        this.traderId = traderId;
        this.stockSymbol = stockSymbol.toUpperCase().trim();
        this.targetValue = targetValue;
        this.condition = condition;
        this.isTriggered = false;
        this.createdAt = LocalDateTime.now();
    }

    public boolean checkCondition(Stock stock) {
        if (isTriggered || stock == null) return false;
        if (!stock.getSymbol().equalsIgnoreCase(this.stockSymbol)) return false;

        boolean triggered = false;
        switch (condition) {
            case PRICE_ABOVE:
                if (stock.getCurrentPrice() >= targetValue) triggered = true;
                break;
            case PRICE_BELOW:
                if (stock.getCurrentPrice() <= targetValue) triggered = true;
                break;
            case PERCENT_CHANGE_ABOVE:
                if (Math.abs(stock.getPriceChangePercent()) >= targetValue) triggered = true;
                break;
        }

        if (triggered) {
            this.isTriggered = true;
            this.triggeredAt = LocalDateTime.now();
        }
        return triggered;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getTraderId() {
        return traderId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public AlertCondition getCondition() {
        return condition;
    }

    public boolean isTriggered() {
        return isTriggered;
    }

    public void setTriggered(boolean triggered) {
        isTriggered = triggered;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    @Override
    public String toString() {
        return String.format("Alert [%s] Stock: %s | Condition: %s %.2f | Status: %s",
                alertId, stockSymbol, condition, targetValue, (isTriggered ? "TRIGGERED" : "ACTIVE"));
    }
}
