package model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import util.MoneyUtil;

/**
 * Abstract base class representing a User in the Online Stock Trading Platform.
 * Demonstrates Abstraction and Encapsulation.
 */
public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    protected int dbId;
    protected String userId;
    protected String name;
    protected String email;
    protected String password;
    protected UserRole role;
    protected double balance;
    protected BigDecimal balanceDecimal;
    protected LocalDateTime createdAt;

    public User(String userId, String name, String email, String password, UserRole role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.balance = 0.0;
        this.balanceDecimal = MoneyUtil.ZERO;
        this.createdAt = LocalDateTime.now();
    }

    public User(String userId, String name, String email, String password, UserRole role, LocalDateTime createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.balance = 0.0;
        this.balanceDecimal = MoneyUtil.ZERO;
        this.createdAt = createdAt;
    }

    // Abstract method to be overridden by subclasses (Polymorphism)
    public abstract String getRoleDescription();

    public boolean hasAdminPrivileges() {
        return this.role == UserRole.ADMIN;
    }

    // Getters and Setters (Encapsulation)
    public int getId() {
        return dbId;
    }

    public void setId(int id) {
        this.dbId = id;
    }

    public double getBalance() {
        return balance;
    }

    public BigDecimal getBalanceDecimal() {
        if (balanceDecimal != null) {
            return balanceDecimal;
        }
        return MoneyUtil.of(getBalance());
    }

    public void setBalance(double balance) {
        this.balance = balance;
        this.balanceDecimal = MoneyUtil.of(balance);
    }

    public void setBalanceDecimal(BigDecimal balanceDecimal) {
        this.balanceDecimal = balanceDecimal != null ? MoneyUtil.round(balanceDecimal) : MoneyUtil.ZERO;
        this.balance = this.balanceDecimal.doubleValue();
    }

    // Getters and Setters (Encapsulation)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("[%s] ID: %s | Name: %s | Email: %s | Role: %s",
                role, userId, name, email, role);
    }
}
