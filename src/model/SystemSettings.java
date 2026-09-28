package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Encapsulates global system configuration and operating parameters for the trading platform.
 */
public class SystemSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MarketStatus {
        OPEN,
        CLOSED,
        PRE_MARKET,
        AFTER_HOURS
    }

    private String exchangeName;
    private double tradingFeePercentage; // e.g. 0.1% per trade
    private MarketStatus marketStatus;
    private int marketUpdateIntervalSeconds;
    private boolean maintenanceMode;
    private int backupFrequencyHours;
    private String systemVersion;
    private int maxConcurrentUsers;
    private LocalDateTime lastUpdated;

    public SystemSettings() {
        this.exchangeName = "Apex Global Equities Exchange";
        this.tradingFeePercentage = 0.15; // 0.15% fee
        this.marketStatus = MarketStatus.OPEN;
        this.marketUpdateIntervalSeconds = 5;
        this.maintenanceMode = false;
        this.backupFrequencyHours = 12;
        this.systemVersion = "v2.5.0-LTS";
        this.maxConcurrentUsers = 5000;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters
    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
        this.lastUpdated = LocalDateTime.now();
    }

    public double getTradingFeePercentage() {
        return tradingFeePercentage;
    }

    public void setTradingFeePercentage(double tradingFeePercentage) {
        this.tradingFeePercentage = tradingFeePercentage;
        this.lastUpdated = LocalDateTime.now();
    }

    public MarketStatus getMarketStatus() {
        return marketStatus;
    }

    public void setMarketStatus(MarketStatus marketStatus) {
        this.marketStatus = marketStatus;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getMarketUpdateIntervalSeconds() {
        return marketUpdateIntervalSeconds;
    }

    public void setMarketUpdateIntervalSeconds(int marketUpdateIntervalSeconds) {
        this.marketUpdateIntervalSeconds = marketUpdateIntervalSeconds;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getBackupFrequencyHours() {
        return backupFrequencyHours;
    }

    public void setBackupFrequencyHours(int backupFrequencyHours) {
        this.backupFrequencyHours = backupFrequencyHours;
        this.lastUpdated = LocalDateTime.now();
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getMaxConcurrentUsers() {
        return maxConcurrentUsers;
    }

    public void setMaxConcurrentUsers(int maxConcurrentUsers) {
        this.maxConcurrentUsers = maxConcurrentUsers;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
