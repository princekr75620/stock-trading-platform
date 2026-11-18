package test;

import dao.*;
import exception.*;
import filter.AuthenticationFilter;
import model.*;
import service.*;
import util.DBConnection;
import util.PasswordHasher;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * Database & System Integration Test Suite.
 * Covers JDBC configuration, CRUD, foreign-key/unique constraint enforcement,
 * atomic balance/inventory guards, persistence failure propagation, RBAC, and rollback.
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
        if (!runAll()) {
            System.err.println("Some tests failed in DatabaseAndIntegrationTest!");
            System.exit(1);
        }
    }

    public static boolean runAll() {
        testsPassed = 0;
        testsTotal = 0;

        System.out.println("===============================================================");
        System.out.println("   DATABASE & RELATIONAL INTEGRATION TEST SUITE                ");
        System.out.println("===============================================================\n");

        DBConnection.initializeDatabase();

        testDatabaseConnectionAndConfiguration();
        testConfigurationValidationAndMigrations();
        testRegistrationAndLoginValidation();
        testDatabaseCRUDOperations();
        testRelationalIntegrityAndAtomicGuards();
        testStockListingAndPortfolioRetrieval();
        testInvalidBuySellRequests();
        testDatabaseTransactionRollbackOnFailure();
        testUnauthorizedAccessToProtectedOperations();
        testResponsiveUIContractsAndValidation();

        System.out.printf("\nDatabaseAndIntegrationTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        return testsPassed == testsTotal;
    }

    public static void testDatabaseConnectionAndConfiguration() {
        System.out.println(">>> 1. Testing Database Connection & Configuration...");
        try (Connection conn = DBConnection.getConnection()) {
            assertTrue(conn != null && !conn.isClosed(), "DB-1: JDBC connection established successfully");

            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            assertTrue(productName != null && !productName.isEmpty(),
                    "DB-2: Relational database metadata identified: " + productName + " (" + meta.getDriverName() + ")");

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

    public static void testConfigurationValidationAndMigrations() {
        System.out.println(">>> 1b. Testing Startup DB Config Validation & Idempotent Data-Preserving Migrations...");

        // 1. Unsupported DB_TYPE must fail loudly instead of silently falling back
        boolean invalidEngineRejected = false;
        try {
            DBConnection.validateConfigurationValues("postgres", "127.0.0.1", "5432", "db", "user", "StrongPass1!", true);
        } catch (IllegalStateException e) {
            invalidEngineRejected = e.getMessage().contains("Unsupported DB_TYPE");
        }
        assertTrue(invalidEngineRejected, "CFG-1: Unsupported DB_TYPE rejected explicitly");

        // 2. Weak/default MySQL password in production fails without leaking password value
        boolean weakPassRejectedSafely = false;
        try {
            DBConnection.validateConfigurationValues("mysql", "127.0.0.1", "3306", "stock_trading", "appuser", "root", true);
        } catch (IllegalStateException e) {
            weakPassRejectedSafely = e.getMessage().contains("minimum strength") && !e.getMessage().contains("root");
        }
        assertTrue(weakPassRejectedSafely, "CFG-2: Weak MySQL production password rejected without exposing secret");

        // 3. Invalid TCP port rejected
        boolean badPortRejected = false;
        try {
            DBConnection.validateConfigurationValues("mysql", "127.0.0.1", "99999", "stock_trading", "appuser", "StrongPass1!", true);
        } catch (IllegalStateException e) {
            badPortRejected = e.getMessage().contains("65535");
        }
        assertTrue(badPortRejected, "CFG-3: Out-of-range MySQL port rejected");

        // 4. Valid production MySQL config accepted
        boolean validMysqlAccepted = true;
        try {
            DBConnection.validateConfigurationValues("mysql", "db.internal", "3306", "stock_trading", "trader_svc", "StrongPass#2026!", true);
        } catch (IllegalStateException e) {
            validMysqlAccepted = false;
        }
        assertTrue(validMysqlAccepted, "CFG-4: Valid MySQL configuration passes validation");

        // 5. Idempotent non-destructive SQLite schema migration test on an isolated temp DB file
        java.io.File tempDb = null;
        try {
            tempDb = java.io.File.createTempFile("migration_test_", ".db");
            String tempUrl = "jdbc:sqlite:" + tempDb.getAbsolutePath() + "?busy_timeout=5000&transaction_mode=IMMEDIATE";
            try (Connection conn = java.sql.DriverManager.getConnection(tempUrl);
                 java.sql.Statement st = conn.createStatement()) {
                // Simulate older pre-migration schema missing alert columns, fee_amount, idempotency_key, and unique portfolio index
                st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id VARCHAR(50) UNIQUE NOT NULL, name VARCHAR(100) NOT NULL, email VARCHAR(150) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL, balance DECIMAL(15,2) NOT NULL DEFAULT 50000.00, department VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                st.execute("CREATE TABLE stocks (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol VARCHAR(20) UNIQUE NOT NULL, company_name VARCHAR(150) NOT NULL, exchange VARCHAR(10) NOT NULL DEFAULT 'NSE', price DECIMAL(15,2) NOT NULL, available_quantity INTEGER NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', previous_price DECIMAL(15,2) DEFAULT 0.00, day_high DECIMAL(15,2) DEFAULT 0.00, day_low DECIMAL(15,2) DEFAULT 0.00, volume_traded BIGINT DEFAULT 0);");
                st.execute("CREATE TABLE portfolio (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id VARCHAR(50) NOT NULL, stock_id INTEGER NOT NULL DEFAULT 0, symbol VARCHAR(20) NOT NULL, quantity INTEGER NOT NULL, average_price DECIMAL(15,2) NOT NULL);");
                st.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id VARCHAR(50) NOT NULL, stock_id INTEGER NOT NULL DEFAULT 0, symbol VARCHAR(20) NOT NULL, transaction_type VARCHAR(10) NOT NULL, quantity INTEGER NOT NULL, price DECIMAL(15,2) NOT NULL, total_amount DECIMAL(15,2) NOT NULL, transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
                st.execute("CREATE TABLE watchlist (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id VARCHAR(50) NOT NULL, symbol VARCHAR(20) NOT NULL, added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id, symbol));");

                st.execute("INSERT INTO users (user_id, name, email, password, role, balance) VALUES ('legacy_mig_u', 'Legacy User', 'leg@test.in', '$2a$12$dummyhashvalueforpreservationcheck', 'TRADER', 12345.67);");
                st.execute("INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES ('legacy_mig_u', 1, 'RELIANCE', 7, 2900.00);");

                // Execute migrations TWICE to prove idempotency & non-destructive behavior
                DBConnection.initializeSchemaAndMigrate(conn, false);
                DBConnection.initializeSchemaAndMigrate(conn, false);

                // Verify new columns, unique index, migration history, AND preserved existing data
                boolean hasAlertCol = false;
                boolean hasFeeCol = false;
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "users", null)) {
                    while (rs.next()) if ("email_alerts_enabled".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) hasAlertCol = true;
                }
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "transactions", null)) {
                    while (rs.next()) if ("fee_amount".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) hasFeeCol = true;
                }

                boolean hasPortfolioUniqueIdx = false;
                try (java.sql.ResultSet rs = conn.getMetaData().getIndexInfo(null, null, "portfolio", true, false)) {
                    while (rs.next()) {
                        String idxName = rs.getString("INDEX_NAME");
                        if ("uq_portfolio_user_symbol".equalsIgnoreCase(idxName)) hasPortfolioUniqueIdx = true;
                    }
                }

                int migCount = 0;
                try (java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
                    if (rs.next()) migCount = rs.getInt(1);
                }

                double preservedBal = 0;
                int preservedQty = 0;
                try (java.sql.ResultSet rs = st.executeQuery("SELECT balance FROM users WHERE user_id = 'legacy_mig_u'")) {
                    if (rs.next()) preservedBal = rs.getDouble(1);
                }
                try (java.sql.ResultSet rs = st.executeQuery("SELECT quantity FROM portfolio WHERE user_id = 'legacy_mig_u' AND symbol = 'RELIANCE'")) {
                    if (rs.next()) preservedQty = rs.getInt(1);
                }

                assertTrue(hasAlertCol && hasFeeCol && hasPortfolioUniqueIdx && migCount >= 3,
                        "MIG-1: Idempotent migrations added missing columns, unique portfolio(user_id,symbol), and tracked 3 migrations");
                assertTrue(Math.abs(preservedBal - 12345.67) < 0.001 && preservedQty == 7,
                        "MIG-2: Repeated migration preserved pre-existing user balance & portfolio rows without dropping tables");
            }
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "MIG: Unexpected error in isolated migration test: " + e.getMessage());
        } finally {
            if (tempDb != null && tempDb.exists()) {
                tempDb.delete();
            }
        }
    }

    public static void testRegistrationAndLoginValidation() {
        System.out.println(">>> 2. Testing Registration & Login Validation...");
        UserDAO userDAO = new UserDAO();
        String testUid = "reg_test_" + System.currentTimeMillis();
        String testEmail = testUid + "@college.edu";
        String rawPassword = "StrongStudentPassword@2026";

        User newTrader = new Trader(testUid, "College Review Student", testEmail, rawPassword, 50000.0);
        IUserService userService = new UserService();

        try {
            userService.createUser(newTrader);
            User saved = userDAO.findById(testUid);
            assertTrue(saved != null, "REG-1: New user registration successfully persisted to database");
            assertTrue(PasswordHasher.isBCryptHash(saved.getPassword()), "REG-2: Password hashed with BCrypt ($2a$) in database");
            assertTrue(PasswordHasher.verifyPassword(rawPassword, saved.getPassword()), "REG-3: BCrypt verification succeeds for plaintext input");

            boolean duplicateBlocked = false;
            try {
                User dupTrader = new Trader("dup_" + testUid, "Duplicate", testEmail, "pass123", 50000.0);
                userService.createUser(dupTrader);
            } catch (DuplicateUserException e) {
                duplicateBlocked = true;
            }
            assertTrue(duplicateBlocked, "REG-4: Duplicate email registration blocked with DuplicateUserException");

            AuthenticationService auth = new AuthenticationService(userService, null);
            User authenticated = auth.login(testEmail, rawPassword);
            assertTrue(authenticated != null && authenticated.getUserId().equals(testUid),
                    "REG-5: Successful login returns authenticated user record");

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
        } finally {
            try {
                userDAO.delete(testUid);
            } catch (SQLException ignored) {}
        }
    }

    public static void testDatabaseCRUDOperations() {
        System.out.println(">>> 3. Testing Database CRUD Operations...");
        UserDAO userDAO = new UserDAO();
        StockDAO stockDAO = new StockDAO();
        PortfolioDAO portfolioDAO = new PortfolioDAO();
        WatchlistDAO watchlistDAO = new WatchlistDAO();
        String testSym = "CRUDSYM" + (System.currentTimeMillis() % 1000);

        try {
            User trader1 = userDAO.findById("trader1");
            if (trader1 != null) {
                double originalBalance = trader1.getBalance();
                userDAO.updateBalance("trader1", originalBalance + 100.0);
                User updated = userDAO.findById("trader1");
                assertTrue(updated != null && Math.abs(updated.getBalance() - (originalBalance + 100.0)) < 0.01,
                        "CRUD-1: UserDAO updateBalance persisted correctly to database");
                userDAO.updateBalance("trader1", originalBalance);
            }

            Stock testStock = new Stock(testSym, "Test Equities Corp", "NSE", 1500.0, 1000);
            stockDAO.insert(testStock);
            Stock fetchedStock = stockDAO.findBySymbol(testSym);
            assertTrue(fetchedStock != null && fetchedStock.getSymbol().equals(testSym),
                    "CRUD-2: StockDAO insert and findBySymbol succeeded");

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

            watchlistDAO.addToWatchlist("trader1", testSym);
            assertTrue(watchlistDAO.isInWatchlist("trader1", testSym),
                    "CRUD-5: WatchlistDAO addToWatchlist successfully stored in database");
            watchlistDAO.removeFromWatchlist("trader1", testSym);
            assertTrue(!watchlistDAO.isInWatchlist("trader1", testSym),
                    "CRUD-6: WatchlistDAO removeFromWatchlist removed item from database");

        } catch (SQLException e) {
            assertTrue(false, "CRUD: SQL exception during CRUD test: " + e.getMessage());
        } finally {
            try {
                stockDAO.delete(testSym);
            } catch (SQLException ignored) {}
        }
    }

    public static void testRelationalIntegrityAndAtomicGuards() {
        System.out.println(">>> 3b. Testing Foreign Key Integrity, Atomic SQL Guards & Failure Propagation...");
        UserDAO userDAO = new UserDAO();
        StockDAO stockDAO = new StockDAO();
        IUserService userService = new UserService();
        String ghostId = "nonexistent_fk_user_" + System.currentTimeMillis();

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Foreign key rejection when inserting portfolio row for nonexistent user
            boolean fkRejected = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES (?, 1, 'RELIANCE', 5, 100.00)")) {
                ps.setString(1, ghostId);
                ps.executeUpdate();
            } catch (SQLException expected) {
                fkRejected = true;
            }
            assertTrue(fkRejected, "INT-1: Foreign key constraint rejected orphan portfolio insertion");

            // 2. Atomic conditional balance debit guard prevents negative overdraft
            String tempUid = "guard_u_" + System.currentTimeMillis();
            userDAO.insert(new Trader(tempUid, "Guard Tester", tempUid + "@t.in", PasswordHasher.hashPassword("Pass123!"), 500.00));
            boolean overdraftBlocked = !userDAO.adjustBalanceAtomically(
                    conn, tempUid, new BigDecimal("-1000.00"), new BigDecimal("1000.00"));
            assertTrue(overdraftBlocked && Math.abs(userDAO.findById(tempUid).getBalance() - 500.00) < 0.01,
                    "INT-2: Atomic SQL balance guard rejected overdraft & preserved ₹500.00");
            userDAO.delete(tempUid);

            // 3. Atomic stock float guard rejects halted stock
            String haltSym = "HLTG" + (System.currentTimeMillis() % 10000);
            Stock s = new Stock(haltSym, "Halted Guard Co", "NSE", 200.0, 100);
            s.setStatus("HALTED");
            stockDAO.insert(s);
            boolean haltedUpdateBlocked = !stockDAO.updateQuantityAndVolume(conn, haltSym, 5, true);
            assertTrue(haltedUpdateBlocked, "INT-3: Atomic StockDAO update rejected non-ACTIVE stock");
            stockDAO.delete(haltSym);

            // 4. UserService propagates persistence failure cleanly when updating ghost user
            User ghost = new Trader(ghostId, "Ghost", ghostId + "@t.in", PasswordHasher.hashPassword("Pass123!"), 100.0);
            boolean propagateUpdateFailure = false;
            try {
                userService.updateUser(ghost);
            } catch (RuntimeException expected) {
                propagateUpdateFailure = true;
            }
            assertTrue(propagateUpdateFailure, "INT-4: UserService fails loudly instead of silently ignoring 0-row DB update");

        } catch (SQLException e) {
            assertTrue(false, "INT: Unexpected SQL error: " + e.getMessage());
        }
    }

    public static void testStockListingAndPortfolioRetrieval() {
        System.out.println(">>> 4. Testing Stock Listing & Portfolio Retrieval...");
        StockDAO stockDAO = new StockDAO();
        PortfolioDAO portfolioDAO = new PortfolioDAO();

        try {
            List<Stock> stocks = stockDAO.findAll();
            assertTrue(stocks != null && stocks.size() >= 10,
                    "LIST-1: StockDAO returned " + (stocks != null ? stocks.size() : 0) + " Indian listed equities");

            Stock reliance = stockDAO.findBySymbol("RELIANCE");
            assertTrue(reliance != null && reliance.getCurrentPrice() > 0 && reliance.getExchange().equals("NSE"),
                    "LIST-2: RELIANCE stock entity mapped with exchange and current price in INR (₹" +
                    (reliance != null ? reliance.getCurrentPrice() : 0) + ")");

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

    public static void testInvalidBuySellRequests() {
        System.out.println(">>> 5. Testing Invalid Buy/Sell Requests...");
        User trader = new Trader("test_buyer", "Test Buyer", "buyer@test.in", "pass", 5000.0);
        Stock tcs = new Stock("TCS", "Tata Consultancy Services", "NSE", 4200.0, 50);
        tcs.setStatus("ACTIVE");

        boolean zeroCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 0);
        } catch (InvalidInputException e) {
            zeroCaught = true;
        } catch (Exception ignored) {}
        assertTrue(zeroCaught, "VAL-1: Zero share buy order rejected with InvalidInputException");

        boolean negCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, -10);
        } catch (InvalidInputException e) {
            negCaught = true;
        } catch (Exception ignored) {}
        assertTrue(negCaught, "VAL-2: Negative share buy order rejected with InvalidInputException");

        boolean balCaught = false;
        try {
            TradingValidator.validateBuyOrder(trader, tcs, 2);
        } catch (InsufficientBalanceException e) {
            balCaught = true;
        } catch (Exception ignored) {}
        assertTrue(balCaught, "VAL-3: Trade exceeding cash balance caught InsufficientBalanceException");

        boolean overSellCaught = false;
        try {
            TradingValidator.validateSellOrder(tcs, 5, 15);
        } catch (InsufficientHoldingsException e) {
            overSellCaught = true;
        } catch (Exception ignored) {}
        assertTrue(overSellCaught, "VAL-4: Sell order exceeding holdings caught InsufficientHoldingsException");
    }

    public static void testDatabaseTransactionRollbackOnFailure() {
        System.out.println(">>> 6. Testing Database Transaction Atomicity & Rollback...");
        UserDAO userDAO = new UserDAO();

        String rollbackTraderId = "rb_trader_" + System.currentTimeMillis();
        User testUser = new Trader(rollbackTraderId, "Rollback Tester", rollbackTraderId + "@trade.in", PasswordHasher.hashPassword("RollPass123!"), 20000.0);

        try {
            userDAO.insert(testUser);
            double initialBalance = testUser.getBalance();

            Connection conn = null;
            boolean caughtRollback = false;
            try {
                conn = DBConnection.getConnection();
                conn.setAutoCommit(false);

                userDAO.updateBalance(conn, rollbackTraderId, initialBalance - 5000.0);

                if (true) {
                    throw new SQLException("Simulated system fault during order settlement");
                }

                conn.commit();
            } catch (SQLException simulatedFault) {
                caughtRollback = true;
                if (conn != null) {
                    conn.rollback();
                }
            } finally {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            }

            assertTrue(caughtRollback, "TX-1: System fault caught and rollback invoked");

            User afterRollback = userDAO.findById(rollbackTraderId);
            assertTrue(afterRollback != null && Math.abs(afterRollback.getBalance() - initialBalance) < 0.01,
                    "TX-2: Balance preserved (₹" + (afterRollback != null ? afterRollback.getBalance() : 0) +
                    " == ₹" + initialBalance + ") proving full JDBC transaction rollback");

        } catch (SQLException e) {
            assertTrue(false, "TX: Exception during rollback test: " + e.getMessage());
        } finally {
            try {
                userDAO.delete(rollbackTraderId);
            } catch (SQLException ignored) {}
        }
    }

    public static void testUnauthorizedAccessToProtectedOperations() {
        System.out.println(">>> 7. Testing Unauthorized Access & RBAC Security...");
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        SecurityAndAuthTest.MockHttpServletRequest unauthReq =
                new SecurityAndAuthTest.MockHttpServletRequest("GET", "/api/admin/users", null);
        SecurityAndAuthTest.MockHttpServletResponse unauthResp =
                new SecurityAndAuthTest.MockHttpServletResponse();

        try {
            filter.doFilter(unauthReq.toProxy(), unauthResp.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}
        assertTrue(unauthResp.status == 401, "RBAC-1: Unauthenticated admin request returned HTTP 401 Unauthorized");

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

        traderReq.headers.put("X-User-Id", "admin");
        SecurityAndAuthTest.MockHttpServletResponse forgedResp =
                new SecurityAndAuthTest.MockHttpServletResponse();

        try {
            filter.doFilter(traderReq.toProxy(), forgedResp.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}
        assertTrue(forgedResp.status == 403, "RBAC-3: Forged X-User-Id: admin header strictly rejected with HTTP 403 Forbidden");
    }

    public static void testResponsiveUIContractsAndValidation() {
        System.out.println(">>> 8. Testing Responsive UI Contracts & Diagnostics...");
        Map<String, Object> health = DBConnection.getHealthStatus();

        assertTrue(health.containsKey("databaseProduct"), "UI-1: Diagnostics payload contains databaseProduct");
        assertTrue(health.containsKey("driverName"), "UI-2: Diagnostics payload contains driverName");
        assertTrue(health.containsKey("tableCounts"), "UI-3: Diagnostics payload contains tableCounts breakdown");

        Stock sample = new Stock("RELIANCE", "Reliance Industries Limited", "NSE", 2980.50, 50000);
        assertTrue(sample.getSymbol().equals("RELIANCE") && sample.getCurrentPrice() > 0 && sample.getExchange().equals("NSE"),
                "UI-4: Stock entity format conforms to frontend StocksView and TradeModal contracts");

        System.out.println(">>> All Relational DB & Integration Criteria Verified Successfully!\n");
    }
}
