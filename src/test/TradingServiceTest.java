package test;

import dao.*;
import exception.*;
import model.*;
import service.*;
import util.DBConnection;
import util.MoneyUtil;
import util.PasswordHasher;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Unit and Database Concurrency Test Suite verifying:
 * - TradingValidator BigDecimal fee & status enforcement
 * - MoneyUtil exact scale/rounding math
 * - TradingService JDBC buy/sell settlement, idempotency deduplication, and halted stock protection
 * - Concurrent buy and concurrent sell thread safety
 */
public class TradingServiceTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        if (!runAll()) {
            System.exit(1);
        }
    }

    public static boolean runAll() {
        testsRun = 0;
        testsPassed = 0;

        System.out.println(">>> Running TradingServiceTest Suite...");
        DBConnection.initializeDatabase();

        testBuyValidationSuccess();
        testInsufficientBalanceThrowsException();
        testInsufficientStockQuantityThrowsException();
        testSellHoldingValidationSuccess();
        testOversellingThrowsException();
        testCircuitBreakerLimits();
        testHaltedStockBlockedOnBuyAndSell();
        testOversizedQuantityBlocked();
        testMoneyUtilPrecisionAndFees();
        testDatabaseBuySellIdempotencyAndFees();
        testConcurrentBuyAndSellThreadSafety();
        testConcurrentLimitedBalanceBuyRaceCondition();
        testRealDatabaseFailureRollbackOnBuyAndSell();
        testBigDecimalDaoAndTransactionValidation();

        System.out.printf("TradingServiceTest Results: %d/%d Passed.\n\n", testsPassed, testsRun);
        return testsPassed == testsRun;
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

    private static void cleanupTestEntities(String userId, String symbol) {
        try (Connection conn = DBConnection.getConnection()) {
            for (String sql : new String[]{
                    "DELETE FROM trade_idempotency WHERE user_id = ?",
                    "DELETE FROM transactions WHERE user_id = ?",
                    "DELETE FROM portfolio WHERE user_id = ?",
                    "DELETE FROM watchlist WHERE user_id = ?",
                    "DELETE FROM users WHERE user_id = ?"
            }) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.executeUpdate();
                }
            }
            if (symbol != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM stocks WHERE symbol = ?")) {
                    ps.setString(1, symbol);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    public static void testBuyValidationSuccess() {
        User trader = new Trader("trader1", "Indian Trader", "trader1@trade.in", "pass", 100000.0);
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
        User trader = new Trader("trader1", "Indian Trader", "trader1@trade.in", "pass", 1000.0);
        Stock reliance = new Stock(1, "RELIANCE", "Reliance Industries", "NSE", 2980.50, 50000);
        reliance.setStatus("ACTIVE");

        boolean threwExpected = false;
        try {
            TradingValidator.validateBuyOrder(trader, reliance, 10);
        } catch (InsufficientBalanceException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testInsufficientBalanceThrowsException - Caught InsufficientBalanceException");
    }

    public static void testInsufficientStockQuantityThrowsException() {
        User trader = new Trader("trader1", "Indian Trader", "trader1@trade.in", "pass", 10000000.0);
        Stock tcs = new Stock(2, "TCS", "Tata Consultancy Services", "NSE", 4245.00, 5);
        tcs.setStatus("ACTIVE");

        boolean threwExpected = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 10);
        } catch (InsufficientStockQuantityException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testInsufficientStockQuantityThrowsException - Caught InsufficientStockQuantityException");
    }

    public static void testSellHoldingValidationSuccess() {
        Stock infy = new Stock(3, "INFY", "Infosys Limited", "NSE", 1895.40, 50000);
        infy.setStatus("ACTIVE");
        boolean success = true;
        try {
            TradingValidator.validateSellOrder(infy, 25, 10);
        } catch (Exception e) {
            success = false;
        }
        assertTrue(success, "testSellHoldingValidationSuccess - Selling shares within owned quantity");
    }

    public static void testOversellingThrowsException() {
        Stock infy = new Stock(3, "INFY", "Infosys Limited", "NSE", 1895.40, 50000);
        infy.setStatus("ACTIVE");
        boolean threwExpected = false;
        try {
            TradingValidator.validateSellOrder(infy, 5, 20);
        } catch (InsufficientHoldingsException e) {
            threwExpected = true;
        } catch (Exception e) {
            threwExpected = false;
        }
        assertTrue(threwExpected, "testOversellingThrowsException - Caught InsufficientHoldingsException");
    }

    public static void testCircuitBreakerLimits() {
        boolean withinBand = TradingValidator.isWithinCircuitBreaker(105.0, 100.0);
        boolean outsideBand = TradingValidator.isWithinCircuitBreaker(125.0, 100.0);
        assertTrue(withinBand && !outsideBand, "testCircuitBreakerLimits - Normal price accepted, >10% surge blocked");
    }

    public static void testHaltedStockBlockedOnBuyAndSell() {
        User trader = new Trader("t_halt", "Halt Tester", "halt@trade.in", "pass", 500000.0);
        Stock halted = new Stock("HALTED", "Halted Corp", "NSE", 500.0, 1000);
        halted.setStatus("HALTED");

        boolean buyBlocked = false;
        try {
            TradingValidator.validateBuyOrder(trader, halted, 1);
        } catch (InvalidInputException e) {
            buyBlocked = e.getMessage().toLowerCase().contains("halted");
        } catch (Exception ignored) {}

        boolean sellBlocked = false;
        try {
            TradingValidator.validateSellOrder(halted, 10, 1);
        } catch (InvalidInputException e) {
            sellBlocked = e.getMessage().toLowerCase().contains("halted");
        } catch (Exception ignored) {}

        assertTrue(buyBlocked && sellBlocked, "testHaltedStockBlockedOnBuyAndSell - HALTED stock rejected for both BUY and SELL");
    }

    public static void testOversizedQuantityBlocked() {
        User trader = new Trader("t_big", "Big Tester", "big@trade.in", "pass", 100000000.0);
        Stock tcs = new Stock("TCS", "TCS", "NSE", 100.0, 1000000);
        tcs.setStatus("ACTIVE");
        boolean rejected = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 50000);
        } catch (InvalidInputException e) {
            rejected = true;
        } catch (Exception ignored) {}
        assertTrue(rejected, "testOversizedQuantityBlocked - Single order > 25,000 shares blocked");
    }

    public static void testMoneyUtilPrecisionAndFees() {
        BigDecimal price = new BigDecimal("123.45");
        BigDecimal subtotal = MoneyUtil.calculateSubtotal(price, 3); // 370.35
        BigDecimal fee = MoneyUtil.calculateFee(subtotal); // 370.35 * 0.0015 = 0.555525 -> 0.56
        BigDecimal buyTotal = MoneyUtil.calculateBuyTotal(price, 3); // 370.91
        BigDecimal sellNet = MoneyUtil.calculateSellNetProceeds(price, 3); // 369.79

        assertTrue(subtotal.compareTo(new BigDecimal("370.35")) == 0
                        && fee.compareTo(new BigDecimal("0.56")) == 0
                        && buyTotal.compareTo(new BigDecimal("370.91")) == 0
                        && sellNet.compareTo(new BigDecimal("369.79")) == 0,
                "testMoneyUtilPrecisionAndFees - Exact BigDecimal 0.15% HALF_UP fee calculation");
    }

    public static void testDatabaseBuySellIdempotencyAndFees() {
        String uid = "ts_unit_" + (System.currentTimeMillis() % 100000);
        String sym = "TST" + (System.currentTimeMillis() % 10000);
        try {
            UserDAO userDAO = new UserDAO();
            StockDAO stockDAO = new StockDAO();
            PortfolioDAO portfolioDAO = new PortfolioDAO();
            NotificationService notif = new NotificationService();
            TradingService service = new TradingService(new MarketUpdateService(notif), new PortfolioService(), notif, new UserService());

            userDAO.insert(new Trader(uid, "TS Trader", uid + "@test.in", PasswordHasher.hashPassword("Secret123!"), 10000.00));
            stockDAO.insert(new Stock(sym, "Unit Test Equity", "NSE", 100.00, 100));

            // 1. Buy 10 shares @ 100.00 => subtotal 1000.00, fee 1.50, total 1001.50
            Transaction buyTx = service.executeBuyTransaction(uid, sym, 10, "buy-key-1");
            User afterBuy = userDAO.findById(uid);
            Stock stockAfterBuy = stockDAO.findBySymbol(sym);
            Map<String, Integer> holdings = portfolioDAO.getHoldings(uid);

            assertTrue(Math.abs(buyTx.getFeeAmount() - 1.50) < 0.001
                            && Math.abs(buyTx.getTotalAmount() - 1001.50) < 0.001
                            && Math.abs(afterBuy.getBalance() - 8998.50) < 0.001
                            && stockAfterBuy.getAvailableQuantity() == 90
                            && holdings.getOrDefault(sym, 0) == 10,
                    "testDatabaseBuySellIdempotencyAndFees - Buy applies 0.15% fee, updates float & portfolio");

            // 2. Replay same Buy Idempotency-Key -> DuplicateOrderException & no extra deduction
            boolean dupBuyBlocked = false;
            try {
                service.executeBuyTransaction(uid, sym, 10, "buy-key-1");
            } catch (DuplicateOrderException e) {
                dupBuyBlocked = true;
            }
            User afterDupBuy = userDAO.findById(uid);
            assertTrue(dupBuyBlocked && Math.abs(afterDupBuy.getBalance() - 8998.50) < 0.001,
                    "testDatabaseBuySellIdempotencyAndFees - Duplicate BUY Idempotency-Key blocked without double debit");

            // 3. Sell 4 shares @ 100.00 => subtotal 400.00, fee 0.60, net 399.40 => balance 9397.90
            Transaction sellTx = service.executeSellTransaction(uid, sym, 4, "sell-key-1");
            User afterSell = userDAO.findById(uid);
            Map<String, Integer> holdingsAfterSell = portfolioDAO.getHoldings(uid);

            assertTrue(Math.abs(sellTx.getFeeAmount() - 0.60) < 0.001
                            && Math.abs(sellTx.getTotalAmount() - 399.40) < 0.001
                            && Math.abs(afterSell.getBalance() - 9397.90) < 0.001
                            && holdingsAfterSell.getOrDefault(sym, 0) == 6,
                    "testDatabaseBuySellIdempotencyAndFees - Sell deducts 0.15% fee and reduces holding");

            // 4. Replay same Sell Idempotency-Key -> DuplicateOrderException
            boolean dupSellBlocked = false;
            try {
                service.executeSellTransaction(uid, sym, 4, "sell-key-1");
            } catch (DuplicateOrderException e) {
                dupSellBlocked = true;
            }
            assertTrue(dupSellBlocked && portfolioDAO.getHoldings(uid).getOrDefault(sym, 0) == 6,
                    "testDatabaseBuySellIdempotencyAndFees - Duplicate SELL Idempotency-Key blocked cleanly");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "testDatabaseBuySellIdempotencyAndFees - Unexpected exception: " + e.getMessage());
        } finally {
            cleanupTestEntities(uid, sym);
        }
    }

    public static void testConcurrentBuyAndSellThreadSafety() {
        String uid = "ts_conc_" + (System.currentTimeMillis() % 100000);
        String sym = "CON" + (System.currentTimeMillis() % 10000);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            UserDAO userDAO = new UserDAO();
            StockDAO stockDAO = new StockDAO();
            PortfolioDAO portfolioDAO = new PortfolioDAO();
            NotificationService notif = new NotificationService();
            TradingService service = new TradingService(new MarketUpdateService(notif), new PortfolioService(), notif, new UserService());

            // 30 shares float, enough cash for all, 5 concurrent threads trying to buy 10 each -> max 3 succeed
            userDAO.insert(new Trader(uid, "Concurrent Trader", uid + "@test.in", PasswordHasher.hashPassword("Secret123!"), 100000.00));
            stockDAO.insert(new Stock(sym, "Concurrency Equity", "NSE", 100.00, 30));

            List<Future<Boolean>> buyFutures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final String key = "conc-buy-" + i;
                buyFutures.add(pool.submit(() -> {
                    try {
                        service.executeBuyTransaction(uid, sym, 10, key);
                        return true;
                    } catch (InsufficientStockException | InsufficientBalanceException e) {
                        return false;
                    }
                }));
            }
            int buySuccesses = 0;
            for (Future<Boolean> f : buyFutures) {
                if (Boolean.TRUE.equals(f.get(10, TimeUnit.SECONDS))) buySuccesses++;
            }
            Stock afterBuys = stockDAO.findBySymbol(sym);
            int heldAfterBuys = portfolioDAO.getHoldings(uid).getOrDefault(sym, 0);

            assertTrue(buySuccesses == 3 && afterBuys.getAvailableQuantity() == 0 && heldAfterBuys == 30,
                    "testConcurrentBuyAndSellThreadSafety - Concurrent BUY capped strictly at available float (3/5 succeeded, 0 float left)");

            // Now 5 concurrent threads trying to sell 10 shares each on 30 owned -> exactly 3 succeed, 0 oversold
            List<Future<Boolean>> sellFutures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final String key = "conc-sell-" + i;
                sellFutures.add(pool.submit(() -> {
                    try {
                        service.executeSellTransaction(uid, sym, 10, key);
                        return true;
                    } catch (InsufficientHoldingsException e) {
                        return false;
                    }
                }));
            }
            int sellSuccesses = 0;
            for (Future<Boolean> f : sellFutures) {
                if (Boolean.TRUE.equals(f.get(10, TimeUnit.SECONDS))) sellSuccesses++;
            }
            int heldAfterSells = portfolioDAO.getHoldings(uid).getOrDefault(sym, 0);
            Stock afterSells = stockDAO.findBySymbol(sym);

            assertTrue(sellSuccesses == 3 && heldAfterSells == 0 && afterSells.getAvailableQuantity() == 30,
                    "testConcurrentBuyAndSellThreadSafety - Concurrent SELL prevented overselling (3/5 succeeded, 0 shares held)");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "testConcurrentBuyAndSellThreadSafety - Unexpected error: " + e.getMessage());
        } finally {
            pool.shutdownNow();
            cleanupTestEntities(uid, sym);
        }
    }

    public static void testConcurrentLimitedBalanceBuyRaceCondition() {
        String uid = "ts_balrace_" + (System.currentTimeMillis() % 100000);
        String sym = "BALR" + (System.currentTimeMillis() % 10000);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            UserDAO userDAO = new UserDAO();
            StockDAO stockDAO = new StockDAO();
            PortfolioDAO portfolioDAO = new PortfolioDAO();

            // Cash = ₹1000.00; 5 threads each on a DIFFERENT TradingService instance attempt BUY 5 shares @ ₹120
            // Cost per order = 600.00 + 0.90 fee = 600.90 -> strictly 1 succeeds, 4 fail InsufficientBalanceException, balance = 399.10
            userDAO.insert(new Trader(uid, "Balance Race Trader", uid + "@test.in", PasswordHasher.hashPassword("Secret123!"), 1000.00));
            stockDAO.insert(new Stock(sym, "Balance Race Equity", "NSE", 120.00, 500));

            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final String key = "bal-race-" + i;
                futures.add(pool.submit(() -> {
                    NotificationService notif = new NotificationService();
                    TradingService separateServiceInstance = new TradingService(
                            new MarketUpdateService(notif), new PortfolioService(), notif, new UserService());
                    startGate.await(5, TimeUnit.SECONDS);
                    try {
                        separateServiceInstance.executeBuyTransaction(uid, sym, 5, key);
                        return "OK";
                    } catch (InsufficientBalanceException e) {
                        return "NO_BAL";
                    }
                }));
            }
            startGate.countDown();

            int okCount = 0;
            int noBalCount = 0;
            for (Future<String> f : futures) {
                String res = f.get(15, TimeUnit.SECONDS);
                if ("OK".equals(res)) okCount++;
                else if ("NO_BAL".equals(res)) noBalCount++;
            }

            User finalUser = userDAO.findById(uid);
            int heldQty = portfolioDAO.getHoldings(uid).getOrDefault(sym, 0);
            Stock finalStock = stockDAO.findBySymbol(sym);

            assertTrue(okCount == 1 && noBalCount == 4
                            && finalUser != null && Math.abs(finalUser.getBalance() - 399.10) < 0.001
                            && heldQty == 5 && finalStock != null && finalStock.getAvailableQuantity() == 495,
                    "testConcurrentLimitedBalanceBuyRaceCondition - Strictly 1/5 concurrent BUYs succeeded across separate instances; balance=₹399.10, float=495");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "testConcurrentLimitedBalanceBuyRaceCondition - Unexpected error: " + e.getMessage());
        } finally {
            pool.shutdownNow();
            cleanupTestEntities(uid, sym);
        }
    }

    public static void testRealDatabaseFailureRollbackOnBuyAndSell() {
        String uid = "ts_rb_" + (System.currentTimeMillis() % 100000);
        String sym = "RBT" + (System.currentTimeMillis() % 10000);
        String triggerName = "trg_fail_tx_" + (System.currentTimeMillis() % 100000);
        try {
            UserDAO userDAO = new UserDAO();
            StockDAO stockDAO = new StockDAO();
            PortfolioDAO portfolioDAO = new PortfolioDAO();
            TransactionDAO txDAO = new TransactionDAO();
            NotificationService notif = new NotificationService();
            TradingService service = new TradingService(new MarketUpdateService(notif), new PortfolioService(), notif, new UserService());

            userDAO.insert(new Trader(uid, "Rollback Trader", uid + "@test.in", PasswordHasher.hashPassword("Secret123!"), 10000.00));
            stockDAO.insert(new Stock(sym, "Rollback Equity", "NSE", 200.00, 100));

            // Seed 5 shares cleanly: 5 * 200 = 1000, fee = 1.50, total = 1001.50 -> remaining balance = 8998.50, float = 95
            service.executeBuyTransaction(uid, sym, 5, "rb-seed-buy");
            double balanceBeforeFault = userDAO.findById(uid).getBalance();
            int floatBeforeFault = stockDAO.findBySymbol(sym).getAvailableQuantity();
            int heldBeforeFault = portfolioDAO.getHoldings(uid).getOrDefault(sym, 0);
            int txCountBeforeFault = txDAO.findByUserId(uid).size();

            // Inject real DB failure at Step 6 (transactions INSERT) AFTER balance, stock float, and portfolio have updated inside TX
            if (!DBConnection.isMySQL()) {
                try (Connection c = DBConnection.getConnection();
                     java.sql.Statement st = c.createStatement()) {
                    st.execute("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON transactions " +
                               "WHEN NEW.user_id = '" + uid + "' BEGIN SELECT RAISE(ABORT, 'Injected mid-transaction ledger fault'); END;");
                }

                // 1. Mid-BUY DB fault must roll back balance debit, stock float decrement, portfolio upsert, and ledger write
                boolean buyRolledBack = false;
                try {
                    service.executeBuyTransaction(uid, sym, 3, "rb-fault-buy");
                } catch (SQLException expected) {
                    buyRolledBack = true;
                }
                assertTrue(buyRolledBack
                                && Math.abs(userDAO.findById(uid).getBalance() - balanceBeforeFault) < 0.001
                                && stockDAO.findBySymbol(sym).getAvailableQuantity() == floatBeforeFault
                                && portfolioDAO.getHoldings(uid).getOrDefault(sym, 0) == heldBeforeFault
                                && txDAO.findByUserId(uid).size() == txCountBeforeFault,
                        "testRealDatabaseFailureRollbackOnBuyAndSell - Real DB failure rolled back entire BUY transaction (balance, float, holdings, ledger)");

                // 2. Mid-SELL DB fault must roll back portfolio decrement, balance credit, stock float return, and ledger write
                boolean sellRolledBack = false;
                try {
                    service.executeSellTransaction(uid, sym, 2, "rb-fault-sell");
                } catch (SQLException expected) {
                    sellRolledBack = true;
                }
                assertTrue(sellRolledBack
                                && Math.abs(userDAO.findById(uid).getBalance() - balanceBeforeFault) < 0.001
                                && stockDAO.findBySymbol(sym).getAvailableQuantity() == floatBeforeFault
                                && portfolioDAO.getHoldings(uid).getOrDefault(sym, 0) == heldBeforeFault
                                && txDAO.findByUserId(uid).size() == txCountBeforeFault,
                        "testRealDatabaseFailureRollbackOnBuyAndSell - Real DB failure rolled back entire SELL transaction (holdings, balance, float, ledger)");
            }

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "testRealDatabaseFailureRollbackOnBuyAndSell - Unexpected error: " + e.getMessage());
        } finally {
            try (Connection c = DBConnection.getConnection();
                 java.sql.Statement st = c.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS " + triggerName);
            } catch (SQLException ignored) {}
            cleanupTestEntities(uid, sym);
        }
    }

    public static void testBigDecimalDaoAndTransactionValidation() {
        PortfolioDAO portfolioDAO = new PortfolioDAO();
        TransactionDAO transactionDAO = new TransactionDAO();
        try (Connection conn = DBConnection.getConnection()) {
            boolean badBuyRejected = !portfolioDAO.recordBuy(conn, "trader1", 1, "RELIANCE", 5, BigDecimal.ZERO);
            boolean badTxRejected = false;
            try {
                Transaction invalidTx = new Transaction("trader1", 1, "RELIANCE", "BUY", 0,
                        new BigDecimal("100.00"), MoneyUtil.ZERO, MoneyUtil.ZERO, null);
                transactionDAO.insert(conn, invalidTx);
            } catch (SQLException expected) {
                badTxRejected = true;
            }
            assertTrue(badBuyRejected && badTxRejected,
                    "testBigDecimalDaoAndTransactionValidation - DAO guards reject zero price and zero quantity cleanly");
        } catch (Exception e) {
            assertTrue(false, "testBigDecimalDaoAndTransactionValidation - Unexpected exception: " + e.getMessage());
        }
    }
}
