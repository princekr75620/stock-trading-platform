package test;

import exception.*;
import model.*;
import service.TradingValidator;

/**
 * Pure Java Unit Test Suite evaluating core trading business logic,
 * balance validations, fee calculations, and domain exceptions.
 */
public class TradingServiceTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println(">>> Running TradingServiceTest Suite...");
        testBuyValidationSuccess();
        testInsufficientBalanceThrowsException();
        testInsufficientStockQuantityThrowsException();
        testSellHoldingValidationSuccess();
        testOversellingThrowsException();
        testCircuitBreakerLimits();

        System.out.printf("TradingServiceTest Results: %d/%d Passed.\n\n", testsPassed, testsRun);
    }

    private static void assertTrue(boolean condition, String testName) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  ✓ PASS: " + testName);
        } else {
            System.err.println("  ✗ FAIL: " + testName);
        }
    }

    public static void testBuyValidationSuccess() {
        User trader = new Trader("trader1", "Alex Morgan", "alex@apex.com", "pass", 100000.0);
        Stock reliance = new Stock(1, "RELIANCE", "Reliance Industries", "NSE", 2980.50, 50000);
        reliance.setStatus("ACTIVE");

        boolean success = true;
        try {
            TradingValidator.validateBuyOrder(trader, reliance, 10);
        } catch (Exception e) {
            success = false;
        }
        assertTrue(success, "testBuyValidationSuccess - Sufficient balance & float");
    }

    public static void testInsufficientBalanceThrowsException() {
        User trader = new Trader("trader1", "Alex Morgan", "alex@apex.com", "pass", 1000.0);
        Stock reliance = new Stock(1, "RELIANCE", "Reliance Industries", "NSE", 2980.50, 50000);
        reliance.setStatus("ACTIVE");

        boolean threwExpected = false;
        try {
            TradingValidator.validateBuyOrder(trader, reliance, 10); // costs ~29,849
        } catch (InsufficientBalanceException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testInsufficientBalanceThrowsException - Caught InsufficientBalanceException");
    }

    public static void testInsufficientStockQuantityThrowsException() {
        User trader = new Trader("trader1", "Alex Morgan", "alex@apex.com", "pass", 10000000.0);
        Stock tcs = new Stock(2, "TCS", "Tata Consultancy Services", "NSE", 4245.00, 5);
        tcs.setStatus("ACTIVE");

        boolean threwExpected = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 10); // requested 10, only 5 available
        } catch (InsufficientStockQuantityException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testInsufficientStockQuantityThrowsException - Caught InsufficientStockQuantityException");
    }

    public static void testSellHoldingValidationSuccess() {
        Stock infy = new Stock(3, "INFY", "Infosys Limited", "NSE", 1895.40, 50000);
        boolean success = true;
        try {
            TradingValidator.validateSellOrder(infy, 25, 10); // owns 25, sells 10
        } catch (Exception e) {
            success = false;
        }
        assertTrue(success, "testSellHoldingValidationSuccess - Selling shares within owned quantity");
    }

    public static void testOversellingThrowsException() {
        Stock infy = new Stock(3, "INFY", "Infosys Limited", "NSE", 1895.40, 50000);
        boolean threwExpected = false;
        try {
            TradingValidator.validateSellOrder(infy, 5, 20); // owns 5, attempts to sell 20
        } catch (InsufficientHoldingsException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testOversellingThrowsException - Caught InsufficientHoldingsException");
    }

    public static void testCircuitBreakerLimits() {
        boolean withinBand = TradingValidator.isWithinCircuitBreaker(105.0, 100.0); // +5%
        boolean outsideBand = TradingValidator.isWithinCircuitBreaker(125.0, 100.0); // +25%
        assertTrue(withinBand && !outsideBand, "testCircuitBreakerLimits - Normal price accepted, >10% surge blocked");
    }
}
