package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import util.MoneyUtil;

/**
 * Trader user who buys/sells stocks, manages portfolio, and configures alert preferences.
 * Demonstrates Inheritance, Polymorphism, and Collections usage (HashSet).
 */
public class Trader extends User {
    private static final long serialVersionUID = 1L;

    private double cashBalance;
    private Set<String> watchlist; // Symbols watched by trader
    private double alertThresholdPercent; // Alert threshold for price swings
    private boolean emailAlertsEnabled;

    public Trader(String userId, String name, String email, String password, double initialBalance) {
        super(userId, name, email, password, UserRole.TRADER);
        this.cashBalance = initialBalance;
        super.setBalance(initialBalance);
        this.watchlist = new HashSet<>();
        this.alertThresholdPercent = 2.0; // Default 2% threshold
        this.emailAlertsEnabled = true;
    }

    public Trader(String userId, String name, String email, String password, double initialBalance,
                  Set<String> watchlist, double alertThresholdPercent, boolean emailAlertsEnabled,
                  LocalDateTime createdAt) {
        super(userId, name, email, password, UserRole.TRADER, createdAt);
        this.cashBalance = initialBalance;
        super.setBalance(initialBalance);
        this.watchlist = (watchlist != null) ? watchlist : new HashSet<>();
        this.alertThresholdPercent = alertThresholdPercent;
        this.emailAlertsEnabled = emailAlertsEnabled;
    }

    @Override
    public String getRoleDescription() {
        return "Trader: Executes equity trades, monitors real-time market updates, tracks portfolio value, and manages alerts.";
    }

    // Cash balance management
    @Override
    public double getBalance() {
        return cashBalance;
    }

    @Override
    public BigDecimal getBalanceDecimal() {
        return super.getBalanceDecimal();
    }

    @Override
    public void setBalance(double balance) {
        this.cashBalance = balance;
        super.setBalance(balance);
    }

    @Override
    public void setBalanceDecimal(BigDecimal balanceDecimal) {
        super.setBalanceDecimal(balanceDecimal);
        this.cashBalance = super.getBalance();
    }

    public double getCashBalance() {
        return cashBalance;
    }

    public void deposit(double amount) {
        if (amount > 0 && Double.isFinite(amount)) {
            BigDecimal updated = getBalanceDecimal().add(MoneyUtil.of(amount));
            setBalanceDecimal(updated);
        }
    }

    public boolean deductCash(double amount) {
        if (amount > 0 && Double.isFinite(amount)) {
            BigDecimal decAmt = MoneyUtil.of(amount);
            BigDecimal current = getBalanceDecimal();
            if (current.compareTo(decAmt) >= 0) {
                setBalanceDecimal(current.subtract(decAmt));
                return true;
            }
        }
        return false;
    }

    public void addCash(double amount) {
        if (amount > 0 && Double.isFinite(amount)) {
            BigDecimal updated = getBalanceDecimal().add(MoneyUtil.of(amount));
            setBalanceDecimal(updated);
        }
    }

    // Watchlist and Preferences
    public Set<String> getWatchlist() {
        return watchlist;
    }

    public void setWatchlist(Set<String> watchlist) {
        this.watchlist = watchlist;
    }

    public void addToWatchlist(String symbol) {
        this.watchlist.add(symbol.toUpperCase());
    }

    public void removeFromWatchlist(String symbol) {
        this.watchlist.remove(symbol.toUpperCase());
    }

    public double getAlertThresholdPercent() {
        return alertThresholdPercent;
    }

    public void setAlertThresholdPercent(double alertThresholdPercent) {
        this.alertThresholdPercent = alertThresholdPercent;
    }

    public boolean isEmailAlertsEnabled() {
        return emailAlertsEnabled;
    }

    public void setEmailAlertsEnabled(boolean emailAlertsEnabled) {
        this.emailAlertsEnabled = emailAlertsEnabled;
    }
}
