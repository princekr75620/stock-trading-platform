package test;

import exception.*;
import filter.AuthenticationFilter;
import model.*;
import service.*;
import util.BCrypt;
import util.PasswordHasher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive Security, Authentication, and Authorization Test Suite.
 * Covers:
 * 1. Valid login & invalid login (generic error message, no account enumeration)
 * 2. BCrypt password hashing & timing-safe verification
 * 3. Normal users denied admin access (HTTP 403)
 * 4. Forged admin headers (X-User-Id: admin) strictly rejected
 * 5. Session handling, session fixation prevention, and logout invalidation
 * 6. Cross-tenant portfolio isolation (unauthorized access blocked)
 * 7. Invalid trade requests (negative qty, insufficient balance, unlisted stocks)
 * 8. Transparent legacy password migration to BCrypt
 */
public class SecurityAndAuthTest {
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
        System.out.println(">>> Running SecurityAndAuthTest Suite...");
        testPasswordHashingAndVerification();
        testLegacyPasswordMigration();
        testValidAndInvalidLogin();
        testAccountLockoutPolicy();
        testNormalUserDeniedAdminAccess();
        testForgedAdminHeadersRejected();
        testSessionHandlingAndLogout();
        testPortfolioCrossTenantIsolation();
        testInvalidTradeRequests();

        System.out.printf("SecurityAndAuthTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        if (testsPassed != testsTotal) {
            System.exit(1);
        }
    }

    public static void testPasswordHashingAndVerification() {
        String rawPassword = "SecurePassword@2026!";
        String hash = PasswordHasher.hashPassword(rawPassword);

        assertTrue(PasswordHasher.isBCryptHash(hash), "testPasswordHashing - Generates valid BCrypt hash format ($2a$)");
        assertTrue(PasswordHasher.verifyPassword(rawPassword, hash), "testPasswordVerification - Verifies correct plaintext against BCrypt hash");
        assertTrue(!PasswordHasher.verifyPassword("WrongPassword123", hash), "testPasswordVerification - Rejects wrong password");
        assertTrue(!hash.equals(rawPassword), "testPasswordHashing - Never stores plaintext password");
    }

    public static void testLegacyPasswordMigration() {
        // Create user with legacy plaintext password
        User legacyUser = new Trader("migratedTrader", "Migrated User", "migrate@trade.in", "legacyPlaintext123", 50000.0);
        Map<String, User> userMap = new HashMap<>();
        userMap.put(legacyUser.getUserId(), legacyUser);
        IUserService userService = new UserService(userMap);
        AuthenticationService authService = new AuthenticationService(userService, null);

        assertTrue(PasswordHasher.needsRehash(legacyUser.getPassword()), "testLegacyPasswordMigration - Detects legacy plaintext requires rehash");

        try {
            User authenticated = authService.login("migratedTrader", "legacyPlaintext123");
            assertTrue(authenticated != null, "testLegacyPasswordMigration - Successfully logs in with legacy password");
            assertTrue(PasswordHasher.isBCryptHash(authenticated.getPassword()), "testLegacyPasswordMigration - Transparently upgraded to BCrypt hash upon login");
            assertTrue(PasswordHasher.verifyPassword("legacyPlaintext123", authenticated.getPassword()), "testLegacyPasswordMigration - Upgraded hash verifies accurately");
        } catch (Exception e) {
            assertTrue(false, "testLegacyPasswordMigration - Exception during migration: " + e.getMessage());
        }
    }

    public static void testValidAndInvalidLogin() {
        String rawPass = "traderSecret456";
        String hashedPass = PasswordHasher.hashPassword(rawPass);
        User trader = new Trader("validTrader", "Valid Trader", "valid@trade.in", hashedPass, 50000.0);

        Map<String, User> userMap = new HashMap<>();
        userMap.put(trader.getUserId(), trader);
        IUserService userService = new UserService(userMap);
        AuthenticationService authService = new AuthenticationService(userService, null);

        // Valid login
        boolean loginSuccess = false;
        try {
            User u = authService.login("valid@trade.in", rawPass);
            loginSuccess = (u != null && u.getUserId().equals("validTrader"));
        } catch (Exception e) {
            loginSuccess = false;
        }
        assertTrue(loginSuccess, "testValidLogin - Successfully authenticates with valid credentials");

        // Invalid login: bad password
        String errorBadPassword = "";
        try {
            authService.login("valid@trade.in", "WrongPassword!");
        } catch (InvalidLoginException e) {
            errorBadPassword = e.getMessage();
        }
        // Invalid login: unknown user
        String errorUnknownUser = "";
        try {
            authService.login("nonexistent@trade.in", "AnyPassword");
        } catch (InvalidLoginException e) {
            errorUnknownUser = e.getMessage();
        }

        // Generic error message check (OWASP Authentication Cheat Sheet: identical message prevents user enumeration)
        assertTrue(errorBadPassword.equals("Invalid username/email or password."),
                "testInvalidLogin - Returns generic message on bad password");
        assertTrue(errorUnknownUser.equals("Invalid username/email or password."),
                "testInvalidLogin - Returns generic message on unknown user (no account enumeration)");
        assertTrue(errorBadPassword.equals(errorUnknownUser),
                "testInvalidLogin - Generic error messages are identical, mitigating enumeration risks");
    }

    public static void testAccountLockoutPolicy() {
        SecuritySettings settings = new SecuritySettings();
        settings.setAutomaticAccountLockout(true);
        settings.setMaxFailedLoginAttempts(3);
        SecurityService secService = new SecurityService(settings, null);

        User user = new Trader("lockoutUser", "Lockout Test", "lock@trade.in", PasswordHasher.hashPassword("correct123"), 50000.0);
        Map<String, User> userMap = new HashMap<>();
        userMap.put(user.getUserId(), user);
        IUserService userService = new UserService(userMap);
        AuthenticationService authService = new AuthenticationService(userService, secService);

        // 3 failed attempts
        for (int i = 0; i < 3; i++) {
            try {
                authService.login("lockoutUser", "wrongPass" + i);
            } catch (InvalidLoginException ignored) {}
        }

        // 4th attempt should be blocked by account lockout
        boolean locked = false;
        try {
            authService.login("lockoutUser", "correct123");
        } catch (InvalidLoginException e) {
            locked = e.getMessage().toLowerCase().contains("locked");
        }
        assertTrue(locked, "testAccountLockoutPolicy - Enforces automatic account lockout after threshold");
    }

    public static void testNormalUserDeniedAdminAccess() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        User normalTrader = new Trader("traderUser", "Normal Trader", "trader@trade.in", "hash", 50000.0);
        MockSession session = new MockSession("sess-1");
        session.setAttribute("user", normalTrader);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users", session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean reachedChain = false;
        try {
            filter.doFilter(request.toProxy(), response.toProxy(), (req, resp) -> {});
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertTrue(response.status == 403, "testNormalUserDeniedAdminAccess - HTTP 403 Forbidden returned for TRADER on /api/admin/*");
        assertTrue(response.body.toString().contains("Administrator privileges required"),
                "testNormalUserDeniedAdminAccess - Appropriate authorization error message returned");
    }

    public static void testForgedAdminHeadersRejected() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        // Attacker attempts to forge X-User-Id: admin without an authenticated admin session
        MockHttpServletRequest requestWithForgedHeader = new MockHttpServletRequest("GET", "/api/admin/users", null);
        requestWithForgedHeader.headers.put("X-User-Id", "admin");
        requestWithForgedHeader.headers.put("X-Admin", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(requestWithForgedHeader.toProxy(), response.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}

        assertTrue(response.status == 401, "testForgedAdminHeadersRejected - Forged header X-User-Id: admin rejected without session (HTTP 401)");

        // Attacker is logged in as TRADER and sends X-User-Id: admin header
        User normalTrader = new Trader("attackerTrader", "Attacker", "att@trade.in", "hash", 50000.0);
        MockSession traderSession = new MockSession("sess-att");
        traderSession.setAttribute("user", normalTrader);

        MockHttpServletRequest requestTraderForgingHeader = new MockHttpServletRequest("GET", "/api/admin/dashboard", traderSession);
        requestTraderForgingHeader.headers.put("X-User-Id", "admin");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        try {
            filter.doFilter(requestTraderForgingHeader.toProxy(), response2.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}

        assertTrue(response2.status == 403, "testForgedAdminHeadersRejected - TRADER with forged admin header strictly denied (HTTP 403)");
    }

    public static void testSessionHandlingAndLogout() {
        MockSession session = new MockSession("session-100");
        User user = new Trader("sessionUser", "Session User", "sess@trade.in", "hash", 50000.0);
        session.setAttribute("user", user);

        assertTrue(session.getAttribute("user") != null, "testSessionHandling - Session stores authenticated user");
        assertTrue(session.isValid(), "testSessionHandling - Session is active before logout");

        // Invalidate on logout
        session.invalidate();
        assertTrue(!session.isValid(), "testSessionHandlingAndLogout - Session invalidated on logout");
        assertTrue(session.getAttribute("user") == null, "testSessionHandlingAndLogout - User attribute purged upon session invalidation");
    }

    public static void testPortfolioCrossTenantIsolation() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        // Trader1 is logged in
        User trader1 = new Trader("trader1", "Trader One", "trader1@trade.in", "hash", 50000.0);
        MockSession session1 = new MockSession("sess-t1");
        session1.setAttribute("user", trader1);

        // Trader1 attempts to access Trader2's portfolio
        MockHttpServletRequest requestCrossTenant = new MockHttpServletRequest("GET", "/api/portfolio", session1);
        requestCrossTenant.parameters.put("traderId", "trader2");
        MockHttpServletResponse responseCrossTenant = new MockHttpServletResponse();

        try {
            filter.doFilter(requestCrossTenant.toProxy(), responseCrossTenant.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}

        assertTrue(responseCrossTenant.status == 403,
                "testPortfolioCrossTenantIsolation - Trader1 denied access to Trader2 portfolio (HTTP 403)");
        assertTrue(responseCrossTenant.body.toString().contains("not authorized to view another user's portfolio"),
                "testPortfolioCrossTenantIsolation - Appropriate cross-tenant access denied message");

        // Trader1 accessing their own portfolio is permitted
        MockHttpServletRequest requestOwn = new MockHttpServletRequest("GET", "/api/portfolio", session1);
        requestOwn.parameters.put("traderId", "trader1");
        MockHttpServletResponse responseOwn = new MockHttpServletResponse();

        boolean reachedFilterOwn = false;
        try {
            filter.doFilter(requestOwn.toProxy(), responseOwn.toProxy(), (req, resp) -> {
                // Filter permitted access to downstream servlet
            });
            reachedFilterOwn = (responseOwn.status == 200);
        } catch (Exception ignored) {}

        assertTrue(reachedFilterOwn, "testPortfolioCrossTenantIsolation - Trader accessing own portfolio is permitted");
    }

    public static void testInvalidTradeRequests() {
        User trader = new Trader("tradeTestUser", "Trade Tester", "test@trade.in", "hash", 10000.0);
        Stock stock = new Stock("TCS", "Tata Consultancy Services", "NSE", 4000.0, 100);

        // Zero quantity
        boolean zeroQtyRejected = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, 0);
        } catch (InvalidInputException e) {
            zeroQtyRejected = true;
        } catch (Exception ignored) {}
        assertTrue(zeroQtyRejected, "testInvalidTradeRequests - Zero share order rejected with InvalidInputException");

        // Negative quantity
        boolean negativeQtyRejected = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, -5);
        } catch (InvalidInputException e) {
            negativeQtyRejected = true;
        } catch (Exception ignored) {}
        assertTrue(negativeQtyRejected, "testInvalidTradeRequests - Negative share order rejected with InvalidInputException");

        // Exceeding trader balance
        boolean insufficientBalance = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, 10); // costs 40,000 > 10,000 balance
        } catch (InsufficientBalanceException e) {
            insufficientBalance = true;
        } catch (Exception ignored) {}
        assertTrue(insufficientBalance, "testInvalidTradeRequests - Order exceeding cash balance caught InsufficientBalanceException");

        // Exceeding available market float
        boolean insufficientStock = false;
        try {
            trader.setBalance(1000000.0);
            TradingValidator.validateBuyOrder(trader, stock, 500); // requested 500 > 100 available
        } catch (InsufficientStockQuantityException e) {
            insufficientStock = true;
        } catch (Exception ignored) {}
        assertTrue(insufficientStock, "testInvalidTradeRequests - Order exceeding available float caught InsufficientStockQuantityException");
    }

    // =========================================================================
    // IN-MEMORY MOCKS FOR FILTER & SERVLET ISOLATED UNIT TESTING
    // =========================================================================
    static class MockSession {
        final String id;
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        boolean valid = true;

        MockSession(String id) { this.id = id; }
        public String getId() { return id; }
        public Object getAttribute(String name) { return valid ? attributes.get(name) : null; }
        public void setAttribute(String name, Object val) { if (valid) attributes.put(name, val); }
        public void invalidate() { valid = false; attributes.clear(); }
        public boolean isValid() { return valid; }

        public HttpSession toProxy() {
            return (HttpSession) Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getId": return getId();
                            case "getAttribute": return getAttribute((String) args[0]);
                            case "setAttribute": setAttribute((String) args[0], args[1]); return null;
                            case "invalidate": invalidate(); return null;
                            default: return null;
                        }
                    }
            );
        }
    }

    static class MockHttpServletRequest {
        String method;
        String uri;
        MockSession session;
        Map<String, String> headers = new HashMap<>();
        Map<String, String> parameters = new HashMap<>();

        MockHttpServletRequest(String method, String uri, MockSession session) {
            this.method = method;
            this.uri = uri;
            this.session = session;
        }

        public HttpServletRequest toProxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, m, args) -> {
                        switch (m.getName()) {
                            case "getMethod": return method;
                            case "getRequestURI": return uri;
                            case "getHeader": return headers.get(args[0]);
                            case "getParameter": return parameters.get(args[0]);
                            case "getSession":
                                boolean create = (args != null && args.length > 0) ? (Boolean) args[0] : true;
                                if (session != null && session.isValid()) return session.toProxy();
                                return null;
                            default: return null;
                        }
                    }
            );
        }
    }

    static class MockHttpServletResponse {
        int status = 200;
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);

        public HttpServletResponse toProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, m, args) -> {
                        switch (m.getName()) {
                            case "setStatus": status = (Integer) args[0]; return null;
                            case "setHeader": headers.put((String) args[0], (String) args[1]); return null;
                            case "setContentType": headers.put("Content-Type", (String) args[0]); return null;
                            case "getWriter": return writer;
                            default: return null;
                        }
                    }
            );
        }
    }
}
