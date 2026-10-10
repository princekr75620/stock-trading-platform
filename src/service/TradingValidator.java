package service;

import exception.*;
import model.Stock;
import model.User;

/**
 * Enterprise Trading Business Rule Validator for Indian Equities (NSE/BSE).
 * Enforces circuit breaker limits, maximum order quantities, and balance requirements.
 */
public class TradingValidator {

    private static final int MAX_SINGLE_ORDER_QUANTITY = 25000;
    private static final double BROKERAGE_FEE_RATE = 0.0015; // 0.15% brokerage
    private static final double CIRCUIT_BREAKER_PERCENT = 0.10; // +/- 10% daily price band

    /**
     * Validates buy order parameters against exchange limits and trader solvency.
     */
    public static void validateBuyOrder(User user, Stock stock, int quantity)
            throws InvalidInputException, InsufficientStockQuantityException, InsufficientBalanceException {
        if (quantity <= 0) {
            throw new InvalidInputException("Order quantity must be greater than zero. Received: " + quantity);
        }

        if (quantity > MAX_SINGLE_ORDER_QUANTITY) {
            throw new InvalidInputException("Order exceeds maximum permissible single-order lot size of " + MAX_SINGLE_ORDER_QUANTITY);
        }

        if (stock == null) {
            throw new InvalidInputException("Stock entity cannot be null.");
        }

        if (!"ACTIVE".equalsIgnoreCase(stock.getStatus())) {
            throw new InvalidInputException("Trading halted or suspended for equity: " + stock.getSymbol());
        }

        if (stock.getAvailableQuantity() < quantity) {
            throw new InsufficientStockQuantityException(String.format(
                    "Insufficient market float for %s. Requested: %d, Available: %d",
                    stock.getSymbol(), quantity, stock.getAvailableQuantity()));
        }

        double subtotal = stock.getCurrentPrice() * quantity;
        double fee = subtotal * BROKERAGE_FEE_RATE;
        double totalRequiredCost = subtotal + fee;

        if (user.getBalance() < totalRequiredCost) {
            throw new InsufficientBalanceException(totalRequiredCost, user.getBalance());
        }
    }

    /**
     * Validates sell order parameters against trader portfolio holdings.
     */
    public static void validateSellOrder(Stock stock, int ownedQuantity, int sellQuantity)
            throws InvalidInputException, InsufficientHoldingsException {
        if (sellQuantity <= 0) {
            throw new InvalidInputException("Order quantity must be greater than zero. Received: " + sellQuantity);
        }

        if (stock == null) {
            throw new InvalidInputException("Stock entity cannot be null.");
        }

        if (ownedQuantity < sellQuantity) {
            throw new InsufficientHoldingsException(stock.getSymbol(), sellQuantity, ownedQuantity);
        }
    }

    /**
     * Checks if price movement exceeds circuit breaker limits (10% daily band).
     */
    public static boolean isWithinCircuitBreaker(double currentPrice, double previousClose) {
        if (previousClose <= 0) return true;
        double changePercent = Math.abs((currentPrice - previousClose) / previousClose);
        return changePercent <= CIRCUIT_BREAKER_PERCENT;
    }
}
