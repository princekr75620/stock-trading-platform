package service;

import exception.*;
import model.Stock;
import model.User;
import util.MoneyUtil;

import java.math.BigDecimal;

/**
 * Enterprise Trading Business Rule Validator for Indian Equities (NSE/BSE).
 * Uses BigDecimal financial arithmetic via MoneyUtil to prevent floating-point rounding drift.
 */
public class TradingValidator {

    private static final int MAX_SINGLE_ORDER_QUANTITY = 25000;
    private static final BigDecimal CIRCUIT_BREAKER_PERCENT = new BigDecimal("0.10"); // +/- 10% daily price band

    /**
     * Validates buy order parameters against exchange limits, ACTIVE status, and trader solvency.
     */
    public static void validateBuyOrder(User user, Stock stock, int quantity)
            throws InvalidInputException, InsufficientStockQuantityException, InsufficientBalanceException {
        if (user == null) {
            throw new InvalidInputException("Authenticated user account is required.");
        }
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
        if (stock.getCurrentPrice() <= 0) {
            throw new InvalidInputException("Invalid market price for equity: " + stock.getSymbol());
        }
        if (stock.getAvailableQuantity() < quantity) {
            throw new InsufficientStockQuantityException(String.format(
                    "Insufficient market float for %s. Requested: %d, Available: %d",
                    stock.getSymbol(), quantity, stock.getAvailableQuantity()));
        }

        BigDecimal unitPrice = MoneyUtil.of(stock.getCurrentPrice());
        BigDecimal totalRequiredCost = MoneyUtil.calculateBuyTotal(unitPrice, quantity);
        BigDecimal availableBalance = MoneyUtil.of(user.getBalance());

        if (availableBalance.compareTo(totalRequiredCost) < 0) {
            throw new InsufficientBalanceException(totalRequiredCost.doubleValue(), availableBalance.doubleValue());
        }
    }

    /**
     * Validates sell order parameters against exchange limits, ACTIVE status, and trader portfolio holdings.
     */
    public static void validateSellOrder(Stock stock, int ownedQuantity, int sellQuantity)
            throws InvalidInputException, InsufficientHoldingsException {
        if (sellQuantity <= 0) {
            throw new InvalidInputException("Order quantity must be greater than zero. Received: " + sellQuantity);
        }
        if (sellQuantity > MAX_SINGLE_ORDER_QUANTITY) {
            throw new InvalidInputException("Order exceeds maximum permissible single-order lot size of " + MAX_SINGLE_ORDER_QUANTITY);
        }
        if (stock == null) {
            throw new InvalidInputException("Stock entity cannot be null.");
        }
        if (!"ACTIVE".equalsIgnoreCase(stock.getStatus())) {
            throw new InvalidInputException("Trading halted or suspended for equity: " + stock.getSymbol());
        }
        if (stock.getCurrentPrice() <= 0) {
            throw new InvalidInputException("Invalid market price for equity: " + stock.getSymbol());
        }
        if (ownedQuantity < sellQuantity) {
            throw new InsufficientHoldingsException(stock.getSymbol(), sellQuantity, ownedQuantity);
        }
    }

    /**
     * Checks if price movement exceeds circuit breaker limits (10% daily band).
     */
    public static boolean isWithinCircuitBreaker(double currentPrice, double previousClose) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0) return false;
        if (!Double.isFinite(previousClose) || previousClose <= 0) return true;
        BigDecimal cur = MoneyUtil.ofExact(currentPrice);
        BigDecimal prev = MoneyUtil.ofExact(previousClose);
        BigDecimal changeRatio = cur.subtract(prev).abs().divide(prev, 6, MoneyUtil.ROUNDING);
        return changeRatio.compareTo(CIRCUIT_BREAKER_PERCENT) <= 0;
    }
}
