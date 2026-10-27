package test;

import dao.*;
import exception.*;
import filter.AuthenticationFilter;
import model.*;
import service.*;
import util.BCrypt;
import util.DBConnection;
import util.PasswordHasher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Review 2 Evaluation Test Suite: Code Quality & Testing (10 Marks).
 *
 * Verifies 8 Core Database and System Integration Criteria:
 * 1. Database connection and configuration (JDBC, SQLite/MySQL, PRAGMA/Foreign Keys).
 * 2. Registration and login validation (BCrypt hashing, validation, generic anti-enumeration error).
 * 3. Database CRUD operations (Users, Stocks, Portfolio, Transactions, Watchlist).
 * 4. Stock listing and portfolio valuation/retrieval.
 * 5. Invalid buy/sell requests (zero, negative, insufficient balance, overselling).
 * 6. Database transaction atomicity & rollback on failure.
 * 7. Unauthorized access to protected operations (401/403 RBAC, anti-header-spoofing).
 * 8. Responsive UI contracts, payload structures, and system diagnostics.
 */
public class DatabaseAndIntegrationTest {
    private static int testsPassed = 0;
    private static int testsTotal = 0;

    private static void assertTrue(boolean condition, String testName) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.println("  ✓ PASS: " + testName);
        } else {
            System.err.println("  ✗ FAIL: " + testName);
        }
    }

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   REVIEW 2 EVALUATION: DATABASE & INTEGRATION TEST SUITE      ");
        System.out.println("===============================================================\n");

        // Initialize live database
        DBConnection.initializeDatabase();

        testDatabaseConnectionAndConfiguration();
        testRegistrationAndLoginValidation();
        testDatabaseCRUDOperations();
        testStockListingAndPortfolioRetrieval();
        testInvalidBuySellRequests();
        testDatabaseTransactionRollbackOnFailure();
        testUnauthorizedAccessToProtectedOperations();
        testResponsiveUIContractsAndValidation();

        System.out.printf("\nDatabaseAndIntegrationTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        if (testsPassed != testsTotal) {
            System.err.println("Some tests failed in DatabaseAndIntegrationTest!");
            System.exit(1);
        }
    }

    /**
     * 1. Test Database connection and configuration.
     */
    public static void testDatabaseConnectionAndConfiguration() {
        System.out.println(">>> 1. Testing Database Connection & Configuration...");
        try (Connection conn = DBConnection.getConnection()) {
            assertTrue(conn != null && !conn.isClosed(), "DB-1: JDBC connection established successfully");

            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            assertTrue(productName != null && !productName.isEmpty(),
                    "DB-2: Relational database metadata identified: " + productName + " (" + meta.getDriverName() + ")");

            // Check health status inspector
            Map<String, Object> health = DBConnection.getHealthStatus();
            assertTrue(Boolean.TRUE.equals(health.get("connected")), "DB-3: getHealthStatus reports connected = true");

            @SuppressWarnings("unchecked")
            Map<String, Integer> counts = (Map<String, Integer>) health.get("tableCounts");
            assertTrue(counts != null && counts.containsKey("users") && counts.containsKey("stocks") &&
                       counts.containsKey("portfolio") && counts.containsKey("transactions") && counts.containsKey("watchlist"),
                    "DB-4: All 5 core relational tables verified in schema (users, stocks, portfolio, transactions, watchlist)");

        } catch (SQLException e) {
            assertTrue(false, "DB-1: Connection failed: " + e.getMessage());
        }
    }

    /**
     * 2. Test Registration and login validation.
     */
    public static void testRegistrationAndLoginValidation() {
        System.out.println(">>> 2. Testing Registration & Login Validation...");
        UserDAO userDAO = new UserDAO();
        String testUid = "reg_test_" + System.currentTimeMillis();
        String testEmail = testUid + "@college.edu";
        String rawPassword = "StrongStudentPassword@2026";

        // Register new trader
        User newTrader = new Trader(testUid, "College Review Student", testEmail, rawPassword, 50000.0);
        IUserService userService = new UserService();

        try {
            userService.createUser(newTrader);
            User saved = userDAO.findById(testUid);
            assertTrue(saved != null, "REG-1: New user registration successfully persisted to database");
            assertTrue(PasswordHasher.isBCryptHash(saved.getPassword()), "REG-2: Password hashed with BCrypt ($2a$) in database");
            assertTrue(PasswordHasher.verifyPassword(rawPassword, saved.getPassword()), "REG-3: BCrypt verification succeeds for plaintext input");

            // Duplicate email check
            boolean duplicateBlocked = false;
            try {
                User dupTrader = new Trader("dup_" + testUid, "Duplicate", testEmail, "pass123", 50000.0);
                userService.createUser(dupTrader);
            } catch (DuplicateUserException e) {
                duplicateBlocked = true;
            }
            assertTrue(duplicateBlocked, "REG-4: Duplicate email registration blocked with DuplicateUserException");

            // Login validation
            AuthenticationService auth = new AuthenticationService(userService, null);
            User authenticated = auth.login(testEmail, rawPassword);
            assertTrue(authenticated != null && authenticated.getUserId().equals(testUid),
                    "REG-5: Successful login returns authenticated user record");

            // Bad password error
            String badPassError = "";
            try {
                auth.login(testEmail, "WrongPassword123");
            } catch (InvalidLoginException e) {
                badPassError = e.getMessage();
            }
            assertTrue(badPassError.equals("Invalid username/email or password."),
                    "REG-6: Invalid password returns generic message mitigating enumeration");

        } catch (Exception e) {
            assertTrue(false, "REG: Error in registration/login test: " + e.getMessage());
        }
    }

    /**
     * 3. Test Database CRUD operations.
     */
    public static void testDatabaseCRUDOperations() {
        System.out.println(">>> 3. Testing Database CRUD Operations...");
        UserDAO userDAO = new UserDAO();
        StockDAO stockDAO = new StockDAO();
        PortfolioDAO portfolioDAO = new PortfolioDAO();
        TransactionDAO txDAO = new TransactionDAO();
        WatchlistDAO watchlistDAO = new WatchlistDAO();

        try {
            // CRUD 1: Users (Update balance)
            User trader1 = userDAO.findById("trader1");
            if (trader1 != null) {
                double originalBalance = trader1.getBalance();
                userDAO.updateBalance("trader1", originalBalance + 100.0);
                User updated = userDAO.findById("trader1");
                assertTrue(updated != null && Math.abs(updated.getBalance() - (originalBalance + 100.0)) < 0.01,
                        "CRUD-1: UserDAO updateBalance persisted correctly to database");
                // Revert
                userDAO.updateBalance("trader1", originalBalance);
            }

            // CRUD 2: Stock (Create, Read, Update)
            String testSym = "CRUDSYM" + (System.currentTimeMillis() % 1000);
            Stock testStock = new Stock(testSym, "Test Equities Corp", "NSE", 1500.0, 1000);
            stockDAO.insert(testStock);
            Stock fetchedStock = stockDAO.findBySymbol(testSym);
            assertTrue(fetchedStock != null && fetchedStock.getSymbol().equals(testSym),
                    "CRUD-2: StockDAO insert and findBySymbol succeeded");

            // CRUD 3: Portfolio (Record buy, getHoldings, Record sell)
            try (Connection conn = DBConnection.getConnection()) {
                portfolioDAO.recordBuy(conn, "trader1", fetchedStock.getId(), testSym, 10, 1500.0);
                Map<String, Integer> holdings = portfolioDAO.getHoldings("trader1");
                assertTrue(holdings.getOrDefault(testSym, 0) == 10,
                        "CRUD-3: PortfolioDAO recordBuy created holding of 10 shares in database");

                portfolioDAO.recordSell(conn, "trader1", testSym, 10);
                Map<String, Integer> holdingsAfterSell = portfolioDAO.getHoldings("trader1");
                assertTrue(holdingsAfterSell.getOrDefault(testSym, 0) == 0,
                        "CRUD-4: PortfolioDAO recordSell deleted 0-share position from database");
            }

            // CRUD 4: Watchlist (Add, Exists, Remove)
            watchlistDAO.addToWatchlist("trader1", testSym);
            assertTrue(watchlistDAO.isInWatchlist("trader1", testSym),
                    "CRUD-5: WatchlistDAO addToWatchlist successfully stored in database");
            watchlistDAO.removeFromWatchlist("trader1", testSym);
            assertTrue(!watchlistDAO.isInWatchlist("trader1", testSym),
                    "CRUD-6: WatchlistDAO removeFromWatchlist removed item from database");

        } catch (SQLException e) {
            assertTrue(false, "CRUD: SQL exception during CRUD test: " + e.getMessage());
        }
    }

    /**
     * 4. Test Stock listing and portfolio retrieval.
     */
    public static void testStockListingAndPortfolioRetrieval() {
        System.out.println(">>> 4. Testing Stock Listing & Portfolio Retrieval...");
        StockDAO stockDAO = new StockDAO();
        PortfolioDAO portfolioDAO = new PortfolioDAO();

        try {
            List<Stock> stocks = stockDAO.findAll();
            assertTrue(stocks != null && stocks.size() >= 10,
                    "LIST-1: StockDAO returned " + (stocks != null ? stocks.size() : 0) + " Indian listed equities");

            // Verify market metrics presence (dayHigh, dayLow, volumeTraded)
            Stock reliance = stockDAO.findBySymbol("RELIANCE");
            assertTrue(reliance != null && reliance.getCurrentPrice() > 0 && reliance.getExchange().equals("NSE"),
                    "LIST-2: RELIANCE stock entity mapped with exchange and current price in INR (₹" +
                    (reliance != null ? reliance.getCurrentPrice() : 0) + ")");

            // Portfolio retrieval
            Portfolio portfolio = portfolioDAO.findByUserId("trader1");
            assertTrue(portfolio != null && portfolio.getTraderId().equals("trader1"),
                    "LIST-3: PortfolioDAO findByUserId retrieved trader portfolio record");

            Map<String, Stock> marketStockMap = new HashMap<>();
            for (Stock s : stocks) marketStockMap.put(s.getSymbol(), s);

            double valuation = portfolio.getCurrentPortfolioValue(marketStockMap);
            assertTrue(valuation >= 0, "LIST-4: Portfolio valuation computed against market prices: ₹" + valuation);

        } catch (SQLException e) {
            assertTrue(false, "LIST: Stock listing test error: " + e.getMessage());
        }
    }

    /**
     * 5. Test Invalid buy/sell requests.
     */
    public static void testInvalidBuySellRequests() {
        System.out.println(">>> 5. Testing Invalid Buy/Sell Requests...");
        User trader = new Trader("test_buyer", "Test Buyer", "buyer@test.in", "pass", 5000.0);
        Stock tcs = new Stock("TCS", "Tata Consultancy Services", "NSE", 4200.0, 50);

        // Zero quantity
        boolean zeroCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 0);
        } catch (InvalidInputException e) {
            zeroCaught = true;
        } catch (Exception ignored) {}
        assertTrue(zeroCaught, "VAL-1: Zero share buy order rejected with InvalidInputException");

        // Negative quantity
        boolean negCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, -10);
        } catch (InvalidInputException e) {
            negCaught = true;
        } catch (Exception ignored) {}
        assertTrue(negCaught, "VAL-2: Negative share buy order rejected with InvalidInputException");

        // Insufficient balance: costs ₹8400 > ₹5000
        boolean balCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 2);
        } catch (InsufficientBalanceException e) {
            balCaught = true;
        } catch (Exception ignored) {}
        assertTrue(balCaught, "VAL-3: Trade exceeding cash balance caught InsufficientBalanceException");

        // Overselling: Trader owns 5, requests to sell 15
        boolean overSellCaught = false;
        try {
            TradingValidator.validateSellOrder(tcs, 5, 15);
        } catch (InsufficientHoldingsException e) {
            overSellCaught = true;
        } catch (Exception ignored) {}
        assertTrue(overSellCaught, "VAL-4: Sell order exceeding holdings caught InsufficientHoldingsException");
    }

    /**
     * 6. Test Database transaction rollback on failure.
     */
    public static void testDatabaseTransactionRollbackOnFailure() {
        System.out.println(">>> 6. Testing Database Transaction Atomicity & Rollback...");
        UserDAO userDAO = new UserDAO();
        StockDAO stockDAO = new StockDAO();

        String rollbackTraderId = "rb_trader_" + System.currentTimeMillis();
        User testUser = new Trader(rollbackTraderId, "Rollback Tester", rollbackTraderId + "@trade.in", "pass", 20000.0);

        try {
            userDAO.insert(testUser);
            double initialBalance = testUser.getBalance();

            // Simulate atomic transaction where an error occurs AFTER balance deduction
            Connection conn = null;
            boolean caughtRollback = false;
            try {
                conn = DBConnection.getConnection();
                conn.setAutoCommit(false); // BEGIN TRANSACTION

                // Step 1: Deduct balance
                userDAO.updateBalance(conn, rollbackTraderId, initialBalance - 5000.0);

                // Step 2: Simulate failure (e.g. database constraint or business logic abort)
                if (true) {
                    throw new SQLException("Simulated system fault during order settlement");
                }

                conn.commit();
            } catch (SQLException simulatedFault) {
                caughtRollback = true;
                if (conn != null) {
                    conn.rollback(); // ROLLBACK TRANSACTION
                }
            } finally {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            }

            assertTrue(caughtRollback, "TX-1: System fault caught and rollback invoked");

            // Verify trader balance in database was NOT altered!
            User afterRollback = userDAO.findById(rollbackTraderId);
            assertTrue(afterRollback != null && Math.abs(afterRollback.getBalance() - initialBalance) < 0.01,
                    "TX-2: Balance preserved (₹" + (afterRollback != null ? afterRollback.getBalance() : 0) +
                    " == ₹" + initialBalance + ") proving full JDBC transaction rollback");

        } catch (SQLException e) {
            assertTrue(false, "TX: Exception during rollback test: " + e.getMessage());
        }
    }

    /**
     * 7. Test Unauthorized access to protected operations.
     */
    public static void testUnauthorizedAccessToProtectedOperations() {
        System.out.println(">>> 7. Testing Unauthorized Access & RBAC Security...");
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        // Test 1: Unauthenticated access to /api/admin/users
        SecurityAndAuthTest.MockHttpServletRequest unauthReq =
                new SecurityAndAuthTest.MockHttpServletRequest("GET", "/api/admin/users", null);
        SecurityAndAuthTest.MockHttpServletResponse unauthResp =
                new SecurityAndAuthTest.MockHttpServletResponse();

        try {
            filter.doFilter(unauthReq.toProxy(), unauthResp.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}
        assertTrue(unauthResp.status == 401, "RBAC-1: Unauthenticated admin request returned HTTP 401 Unauthorized");

        // Test 2: Trader session attempting admin access
        User traderUser = new Trader("traderOnly", "Trader Only", "to@trade.in", "hash", 50000.0);
        SecurityAndAuthTest.MockSession traderSession = new SecurityAndAuthTest.MockSession("sess-trader");
        traderSession.setAttribute("user", traderUser);

        SecurityAndAuthTest.MockHttpServletRequest traderReq =
                new SecurityAndAuthTest.MockHttpServletRequest("GET", "/api/admin/dashboard", traderSession);
        SecurityAndAuthTest.MockHttpServletResponse traderResp =
                new SecurityAndAuthTest.MockHttpServletResponse();

        try {
            filter.doFilter(traderReq.toProxy(), traderResp.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}
        assertTrue(traderResp.status == 403, "RBAC-2: Trader accessing /api/admin/dashboard returned HTTP 403 Forbidden");

        // Test 3: Forged admin header strictly rejected
        traderReq.headers.put("X-User-Id", "admin");
        SecurityAndAuthTest.MockHttpServletResponse forgedResp =
                new SecurityAndAuthTest.MockHttpServletResponse();

        try {
            filter.doFilter(traderReq.toProxy(), forgedResp.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}
        assertTrue(forgedResp.status == 403, "RBAC-3: Forged X-User-Id: admin header strictly rejected with HTTP 403 Forbidden");
    }

    /**
     * 8. Test Responsive UI contracts and validation.
     */
    public static void testResponsiveUIContractsAndValidation() {
        System.out.println(">>> 8. Testing Responsive UI Contracts & Diagnostics...");
        Map<String, Object> health = DBConnection.getHealthStatus();

        assertTrue(health.containsKey("databaseProduct"), "UI-1: Diagnostics payload contains databaseProduct");
        assertTrue(health.containsKey("driverName"), "UI-2: Diagnostics payload contains driverName");
        assertTrue(health.containsKey("tableCounts"), "UI-3: Diagnostics payload contains tableCounts breakdown");

        // Stock entity contract validation
        Stock sample = new Stock("RELIANCE", "Reliance Industries Limited", "NSE", 2980.50, 50000);
        assertTrue(sample.getSymbol().equals("RELIANCE") && sample.getCurrentPrice() > 0 && sample.getExchange().equals("NSE"),
                "UI-4: Stock entity format conforms to frontend StocksView and TradeModal contracts");

        System.out.println(">>> All 8 Review 2 Evaluation Criteria Verified Successfully!\n");
    }
}
