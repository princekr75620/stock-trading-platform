package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Manages financial platform security policies:
 * - Password policy (min length, requires special characters, expiry days)
 * - Login security (max failed login attempts, session timeout in minutes, 2FA enforcement)
 * - Account protection (suspicious IP lockout, suspicious withdrawal freeze)
 * - Transaction security (two-step trade authorization above threshold, max single trade limit)
 */
public class SecuritySettings implements Serializable {
    private static final long serialVersionUID = 1L;

    // Password Policy
    private int minPasswordLength;
    private boolean requireSpecialChar;
    private boolean requireNumbers;
    private int passwordExpiryDays;

    // Login Security
    private int maxFailedLoginAttempts;
    private int sessionTimeoutMinutes;
    private boolean twoFactorAuthRequired;

    // Account Protection
    private boolean automaticAccountLockout;
    private boolean ipAnomalyDetection;

    // Transaction Security
    private double highValueTradeThreshold;
    private boolean requireTwoStepForHighValueTrades;
    private double dailyTradingLimitPerTrader;

    private LocalDateTime lastUpdated;

    public SecuritySettings() {
        // Safe default settings
        this.minPasswordLength = 8;
        this.requireSpecialChar = true;
        this.requireNumbers = true;
        this.passwordExpiryDays = 90;

        this.maxFailedLoginAttempts = 5;
        this.sessionTimeoutMinutes = 30;
        this.twoFactorAuthRequired = false;

        this.automaticAccountLockout = true;
        this.ipAnomalyDetection = true;

        this.highValueTradeThreshold = 50000.0;
        this.requireTwoStepForHighValueTrades = true;
        this.dailyTradingLimitPerTrader = 250000.0;

        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters
    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    public void setMinPasswordLength(int minPasswordLength) {
        this.minPasswordLength = minPasswordLength;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isRequireSpecialChar() {
        return requireSpecialChar;
    }

    public void setRequireSpecialChar(boolean requireSpecialChar) {
        this.requireSpecialChar = requireSpecialChar;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isRequireNumbers() {
        return requireNumbers;
    }

    public void setRequireNumbers(boolean requireNumbers) {
        this.requireNumbers = requireNumbers;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getPasswordExpiryDays() {
        return passwordExpiryDays;
    }

    public void setPasswordExpiryDays(int passwordExpiryDays) {
        this.passwordExpiryDays = passwordExpiryDays;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getMaxFailedLoginAttempts() {
        return maxFailedLoginAttempts;
    }

    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lastUpdated = LocalDateTime.now();
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isTwoFactorAuthRequired() {
        return twoFactorAuthRequired;
    }

    public void setTwoFactorAuthRequired(boolean twoFactorAuthRequired) {
        this.twoFactorAuthRequired = twoFactorAuthRequired;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isAutomaticAccountLockout() {
        return automaticAccountLockout;
    }

    public void setAutomaticAccountLockout(boolean automaticAccountLockout) {
        this.automaticAccountLockout = automaticAccountLockout;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isIpAnomalyDetection() {
        return ipAnomalyDetection;
    }

    public void setIpAnomalyDetection(boolean ipAnomalyDetection) {
        this.ipAnomalyDetection = ipAnomalyDetection;
        this.lastUpdated = LocalDateTime.now();
    }

    public double getHighValueTradeThreshold() {
        return highValueTradeThreshold;
    }

    public void setHighValueTradeThreshold(double highValueTradeThreshold) {
        this.highValueTradeThreshold = highValueTradeThreshold;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isRequireTwoStepForHighValueTrades() {
        return requireTwoStepForHighValueTrades;
    }

    public void setRequireTwoStepForHighValueTrades(boolean requireTwoStepForHighValueTrades) {
        this.requireTwoStepForHighValueTrades = requireTwoStepForHighValueTrades;
        this.lastUpdated = LocalDateTime.now();
    }

    public double getDailyTradingLimitPerTrader() {
        return dailyTradingLimitPerTrader;
    }

    public void setDailyTradingLimitPerTrader(double dailyTradingLimitPerTrader) {
        this.dailyTradingLimitPerTrader = dailyTradingLimitPerTrader;
        this.lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
