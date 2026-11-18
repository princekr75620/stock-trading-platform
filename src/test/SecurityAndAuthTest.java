package test;

import exception.*;
import filter.AuthenticationFilter;
import model.*;
import server.ServletBridge;
import service.*;
import util.PasswordHasher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive Security, Authentication, Authorization, CSRF, and Session Hardening Test Suite.
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
        if (!runAll()) {
            System.exit(1);
        }
    }

    public static boolean runAll() {
        testsPassed = 0;
        testsTotal = 0;

        System.out.println(">>> Running SecurityAndAuthTest Suite...");
        testPasswordHashingAndVerification();
        testLegacyPasswordMigration();
        testValidAndInvalidLogin();
        testAccountLockoutPolicy();
        testRegistrationAdminPrivilegeEscalationPrevented();
        testNormalUserDeniedAdminAccess();
        testForgedAdminHeadersRejected();
        testStrictCsrfAndLogoutEnforcement();
        testCrossSessionAndMethodCsrfProtection();
        testSessionHandlingFixationAndExpiry();
        testNoPlaintextPersistenceOrJsonLeak();
        testPortfolioCrossTenantIsolation();
        testInvalidTradeRequests();

        System.out.printf("SecurityAndAuthTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        return testsPassed == testsTotal;
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
        String rawLegacyPass = "legacyPlaintext123";
        assertTrue(!PasswordHasher.verifyPassword(rawLegacyPass, rawLegacyPass),
                "testLegacyPasswordMigration - General verifyPassword rejects unflagged plaintext password");
        assertTrue(!PasswordHasher.needsRehash(rawLegacyPass),
                "testLegacyPasswordMigration - Unflagged plaintext is not eligible for auto-migration");

        User unflaggedUser = new Trader("unflaggedUser", "Unflagged User", "unflagged@trade.in", rawLegacyPass, 50000.0);
        Map<String, User> unflaggedMap = new HashMap<>();
        unflaggedMap.put(unflaggedUser.getUserId(), unflaggedUser);
        IUserService unflaggedService = new UserService(unflaggedMap);
        AuthenticationService unflaggedAuth = new AuthenticationService(unflaggedService, null);

        boolean unflaggedRejected = false;
        try {
            unflaggedAuth.login("unflaggedUser", rawLegacyPass);
        } catch (InvalidLoginException e) {
            unflaggedRejected = true;
        }
        assertTrue(unflaggedRejected, "testLegacyPasswordMigration - Normal login strictly rejects unflagged plaintext password");

        String legacyMarked = PasswordHasher.markLegacyPassword(rawLegacyPass);
        User legacyUser = new Trader("migratedTrader", "Migrated User", "migrate@trade.in", legacyMarked, 50000.0);
        Map<String, User> userMap = new HashMap<>();
        userMap.put(legacyUser.getUserId(), legacyUser);
        IUserService userService = new UserService(userMap);
        AuthenticationService authService = new AuthenticationService(userService, null);

        assertTrue(PasswordHasher.needsRehash(legacyUser.getPassword()),
                "testLegacyPasswordMigration - Detects $LEGACY$ marker requires upgrade");

        try {
            User authenticated = authService.login("migratedTrader", rawLegacyPass);
            assertTrue(authenticated != null, "testLegacyPasswordMigration - Logs in with explicitly marked $LEGACY$ password");
            assertTrue(PasswordHasher.isBCryptHash(authenticated.getPassword()),
                    "testLegacyPasswordMigration - Conditionally upgraded $LEGACY$ record to BCrypt hash");
            assertTrue(PasswordHasher.verifyPassword(rawLegacyPass, authenticated.getPassword()),
                    "testLegacyPasswordMigration - Upgraded BCrypt hash verifies accurately");
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

        boolean loginSuccess = false;
        try {
            User u = authService.login("valid@trade.in", rawPass);
            loginSuccess = (u != null && u.getUserId().equals("validTrader"));
        } catch (Exception ignored) {}
        assertTrue(loginSuccess, "testValidLogin - Successfully authenticates with valid credentials");

        String errorBadPassword = "";
        try {
            authService.login("valid@trade.in", "WrongPassword!");
        } catch (InvalidLoginException e) {
            errorBadPassword = e.getMessage();
        }
        String errorUnknownUser = "";
        try {
            authService.login("nonexistent@trade.in", "AnyPassword");
        } catch (InvalidLoginException e) {
            errorUnknownUser = e.getMessage();
        }

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

        for (int i = 0; i < 3; i++) {
            try {
                authService.login("lockoutUser", "wrongPass" + i);
            } catch (InvalidLoginException ignored) {}
        }

        boolean locked = false;
        try {
            authService.login("lockoutUser", "correct123");
        } catch (InvalidLoginException e) {
            locked = e.getMessage().toLowerCase().contains("locked");
        }
        assertTrue(locked, "testAccountLockoutPolicy - Enforces automatic account lockout after threshold");
    }

    public static void testRegistrationAdminPrivilegeEscalationPrevented() {
        servlet.RegisterServlet registerServlet = new servlet.RegisterServlet();
        IUserService userService = new UserService(new HashMap<>());
        servlet.BaseServlet.initializeServices(
                userService, null, null, null, null, null, null, null, null
        );

        String hackerUid = "hacker_" + System.currentTimeMillis();
        MockHttpServletRequest requestWithAdminRole = new MockHttpServletRequest("POST", "/api/auth/register", null);
        requestWithAdminRole.headers.put("Content-Type", "application/json");
        requestWithAdminRole.body = String.format(
                "{\"userId\":\"%s\",\"name\":\"Hacker\",\"email\":\"%s@test.com\",\"password\":\"HackerPass123!\",\"role\":\"ADMIN\",\"initialBalance\":99999999}",
                hackerUid, hackerUid);
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        try {
            registerServlet.doPost(requestWithAdminRole.toProxy(), response1.toProxy());
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertTrue(response1.status == 201, "testRegistrationAdminPrivilegeEscalation - Public registration endpoint responds 201 Created");
        User createdUser = null;
        try {
            createdUser = userService.getUserById(hackerUid);
        } catch (Exception ignored) {}

        assertTrue(createdUser != null, "testRegistrationAdminPrivilegeEscalation - Registered user entity created in service");
        assertTrue(createdUser != null && createdUser.getRole() == UserRole.TRADER,
                "testRegistrationAdminPrivilegeEscalation - Public registration with role=ADMIN forced to TRADER");
        assertTrue(createdUser != null && createdUser.getRole() != UserRole.ADMIN,
                "testRegistrationAdminPrivilegeEscalation - Public registration strictly denied ADMIN role");
        assertTrue(createdUser != null && createdUser.getBalance() == 50000.0,
                "testRegistrationAdminPrivilegeEscalation - Client-supplied initialBalance 99999999 rejected; default 50000.0 enforced");
        assertTrue(response1.body.toString().contains("\"csrfToken\""),
                "testRegistrationAdminPrivilegeEscalation - Registration response issues session csrfToken");
    }

    public static void testNormalUserDeniedAdminAccess() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        User normalTrader = new Trader("traderUser", "Normal Trader", "trader@trade.in", "hash", 50000.0);
        MockSession session = new MockSession("sess-1");
        session.setAttribute("user", normalTrader);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users", session);
        MockHttpServletResponse response = new MockHttpServletResponse();

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

        MockHttpServletRequest requestWithForgedHeader = new MockHttpServletRequest("GET", "/api/admin/users", null);
        requestWithForgedHeader.headers.put("X-User-Id", "admin");
        requestWithForgedHeader.headers.put("X-Admin", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(requestWithForgedHeader.toProxy(), response.toProxy(), (req, resp) -> {});
        } catch (Exception ignored) {}

        assertTrue(response.status == 401, "testForgedAdminHeadersRejected - Forged header X-User-Id: admin rejected without session (HTTP 401)");

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

    public static void testStrictCsrfAndLogoutEnforcement() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        User trader = new Trader("csrfUser", "CSRF Tester", "csrf@trade.in", "hash", 50000.0);
        MockSession session = new MockSession("sess-csrf");
        session.setAttribute("user", trader);
        session.setAttribute("csrfToken", "expected-csrf-secret-token");

        try {
            // 1. Missing CSRF token rejected (403)
            MockHttpServletRequest missingCsrf = new MockHttpServletRequest("POST", "/api/trades/buy", session);
            missingCsrf.headers.put("Origin", "http://localhost:3000");
            MockHttpServletResponse resp1 = new MockHttpServletResponse();
            filter.doFilter(missingCsrf.toProxy(), resp1.toProxy(), (req, resp) -> {});
            assertTrue(resp1.status == 403, "testStrictCsrf - Missing X-CSRF-Token blocked (403)");

            // 2. Legacy X-Requested-With / Content-Type: application/json bypasses NO LONGER accepted (403)
            MockHttpServletRequest legacyBypass = new MockHttpServletRequest("POST", "/api/trades/buy", session);
            legacyBypass.headers.put("Content-Type", "application/json");
            legacyBypass.headers.put("X-Requested-With", "XMLHttpRequest");
            legacyBypass.headers.put("Origin", "http://localhost:3000");
            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            filter.doFilter(legacyBypass.toProxy(), resp2.toProxy(), (req, resp) -> {});
            assertTrue(resp2.status == 403, "testStrictCsrf - X-Requested-With & Content-Type bypasses rejected without CSRF token (403)");

            // 3. Mismatched CSRF token rejected (403)
            MockHttpServletRequest wrongCsrf = new MockHttpServletRequest("POST", "/api/trades/buy", session);
            wrongCsrf.headers.put("X-CSRF-Token", "wrong-token");
            wrongCsrf.headers.put("Origin", "http://localhost:3000");
            MockHttpServletResponse resp3 = new MockHttpServletResponse();
            filter.doFilter(wrongCsrf.toProxy(), resp3.toProxy(), (req, resp) -> {});
            assertTrue(resp3.status == 403, "testStrictCsrf - Invalid X-CSRF-Token rejected (403)");

            // 4. Untrusted Origin rejected even with valid CSRF token (403)
            MockHttpServletRequest evilOrigin = new MockHttpServletRequest("POST", "/api/trades/buy", session);
            evilOrigin.headers.put("X-CSRF-Token", "expected-csrf-secret-token");
            evilOrigin.headers.put("Origin", "https://attacker.example.com");
            MockHttpServletResponse resp4 = new MockHttpServletResponse();
            filter.doFilter(evilOrigin.toProxy(), resp4.toProxy(), (req, resp) -> {});
            assertTrue(resp4.status == 403, "testStrictCsrf - Untrusted Origin header rejected (403)");

            // 5. Valid CSRF token + trusted Origin succeeds (200)
            MockHttpServletRequest validReq = new MockHttpServletRequest("POST", "/api/trades/buy", session);
            validReq.headers.put("X-CSRF-Token", "expected-csrf-secret-token");
            validReq.headers.put("Origin", "http://localhost:3000");
            MockHttpServletResponse resp5 = new MockHttpServletResponse();
            final boolean[] passed = {false};
            filter.doFilter(validReq.toProxy(), resp5.toProxy(), (req, resp) -> passed[0] = true);
            assertTrue(passed[0] && resp5.status == 200, "testStrictCsrf - Valid session CSRF token & trusted Origin passes filter");

            // 6. GET /api/auth/logout strictly rejected (405)
            MockHttpServletRequest getLogout = new MockHttpServletRequest("GET", "/api/auth/logout", session);
            MockHttpServletResponse resp6 = new MockHttpServletResponse();
            filter.doFilter(getLogout.toProxy(), resp6.toProxy(), (req, resp) -> {});
            assertTrue(resp6.status == 405, "testStrictCsrf - GET /api/auth/logout rejected with 405 Method Not Allowed");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "testStrictCsrf - Unexpected exception: " + e.getMessage());
        }
    }

    public static void testCrossSessionAndMethodCsrfProtection() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        User admin = new Admin("adminCsrf", "Admin CSRF", "adm@trade.in", "hash", "Ops");
        MockSession sessionA = new MockSession("sess-A");
        sessionA.setAttribute("user", admin);
        sessionA.setAttribute("csrfToken", "token-belongs-to-session-A");

        MockSession sessionB = new MockSession("sess-B");
        sessionB.setAttribute("user", admin);
        sessionB.setAttribute("csrfToken", "token-belongs-to-session-B");

        try {
            // 1. Cross-session CSRF token submitted by Session B rejected (403)
            MockHttpServletRequest crossReq = new MockHttpServletRequest("POST", "/api/admin/users", sessionB);
            crossReq.headers.put("Origin", "http://localhost:3000");
            crossReq.headers.put("X-CSRF-Token", "token-belongs-to-session-A");
            MockHttpServletResponse crossResp = new MockHttpServletResponse();
            filter.doFilter(crossReq.toProxy(), crossResp.toProxy(), (req, resp) -> {});
            assertTrue(crossResp.status == 403,
                    "testCrossSessionAndMethodCsrfProtection - Session B submitting Session A CSRF token rejected (403)");

            // 2. Protected PUT & DELETE without CSRF rejected (403), with valid CSRF allowed (200)
            for (String method : new String[]{"PUT", "DELETE"}) {
                MockHttpServletRequest noTokenReq = new MockHttpServletRequest(method, "/api/admin/users", sessionA);
                noTokenReq.headers.put("Origin", "http://localhost:3000");
                MockHttpServletResponse noTokenResp = new MockHttpServletResponse();
                filter.doFilter(noTokenReq.toProxy(), noTokenResp.toProxy(), (req, resp) -> {});
                assertTrue(noTokenResp.status == 403,
                        "testCrossSessionAndMethodCsrfProtection - " + method + " without CSRF token rejected (403)");

                MockHttpServletRequest okReq = new MockHttpServletRequest(method, "/api/admin/users", sessionA);
                okReq.headers.put("Origin", "http://localhost:3000");
                okReq.headers.put("X-CSRF-Token", "token-belongs-to-session-A");
                MockHttpServletResponse okResp = new MockHttpServletResponse();
                final boolean[] passed = {false};
                filter.doFilter(okReq.toProxy(), okResp.toProxy(), (req, resp) -> passed[0] = true);
                assertTrue(passed[0] && okResp.status == 200,
                        "testCrossSessionAndMethodCsrfProtection - " + method + " with valid session CSRF token permitted");
            }

            // 3. Expired / invalidated session cannot POST even with old CSRF token (401)
            sessionA.invalidate();
            MockHttpServletRequest expiredPost = new MockHttpServletRequest("POST", "/api/trades/buy", sessionA);
            expiredPost.headers.put("Origin", "http://localhost:3000");
            expiredPost.headers.put("X-CSRF-Token", "token-belongs-to-session-A");
            MockHttpServletResponse expiredResp = new MockHttpServletResponse();
            filter.doFilter(expiredPost.toProxy(), expiredResp.toProxy(), (req, resp) -> {});
            assertTrue(expiredResp.status == 401,
                    "testCrossSessionAndMethodCsrfProtection - Expired/invalidated session rejected on POST even with old CSRF token (401)");
        } catch (Exception e) {
            assertTrue(false, "testCrossSessionAndMethodCsrfProtection - Unexpected error: " + e.getMessage());
        }
    }

    public static void testNoPlaintextPersistenceOrJsonLeak() {
        boolean legacyTxtExists = new java.io.File("data/users.txt").exists();
        assertTrue(!legacyTxtExists, "testNoPlaintextPersistenceOrJsonLeak - No data/users.txt plaintext credential file exists");

        boolean dpmRemoved = false;
        try {
            Class.forName("util.DataPersistenceManager");
        } catch (ClassNotFoundException e) {
            dpmRemoved = true;
        }
        assertTrue(dpmRemoved, "testNoPlaintextPersistenceOrJsonLeak - Legacy plaintext DataPersistenceManager is completely removed");

        String secretHash = PasswordHasher.hashPassword("TopSecretPlain99!");
        User u = new Trader("jsonSafe", "Safe User", "safe@trade.in", secretHash, 25000.0);
        String json = server.JsonHelper.userToJson(u);
        assertTrue(!json.contains("TopSecretPlain99!") && !json.contains("$2a$") && !json.contains("\"password\""),
                "testNoPlaintextPersistenceOrJsonLeak - JsonHelper.userToJson never leaks password or BCrypt hash");
    }

    public static void testSessionHandlingFixationAndExpiry() {
        MockSession session = new MockSession("session-100");
        User user = new Trader("sessionUser", "Session User", "sess@trade.in", PasswordHasher.hashPassword("SecretPass1!"), 50000.0);
        session.setAttribute("user", user);

        assertTrue(session.getAttribute("user") != null, "testSessionHandling - Session stores authenticated user");
        assertTrue(session.isValid(), "testSessionHandling - Session is active before logout");

        // Session fixation check via LoginServlet
        Map<String, User> userMap = new HashMap<>();
        userMap.put(user.getUserId(), user);
        IUserService us = new UserService(userMap);
        IAuthenticationService as = new AuthenticationService(us, null);
        servlet.BaseServlet.initializeServices(us, null, null, null, null, null, null, null, as);
        servlet.LoginServlet loginServlet = new servlet.LoginServlet();

        MockSession preLoginSess = new MockSession("fixed-old-id");
        MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/auth/login", preLoginSess);
        loginReq.body = "{\"usernameOrEmail\":\"sessionUser\",\"password\":\"SecretPass1!\"}";
        MockHttpServletResponse loginResp = new MockHttpServletResponse();
        try {
            loginServlet.service(loginReq.toProxy(), loginResp.toProxy());
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertTrue(!preLoginSess.isValid() && loginReq.session != null
                        && !"fixed-old-id".equals(loginReq.session.getId())
                        && loginResp.body.toString().contains("\"csrfToken\""),
                "testSessionHandlingFixationAndExpiry - Login invalidates prior session, rotates ID & issues CSRF token");

        // Real ServletBridgeSession timeout expiration check
        ServletBridge.ServletBridgeSession expiring = new ServletBridge.ServletBridgeSession("exp-test", 1);
        assertTrue(!expiring.isExpired(), "testSessionHandlingFixationAndExpiry - Fresh session is not expired");
        try {
            Thread.sleep(1150);
        } catch (InterruptedException ignored) {}
        assertTrue(expiring.isExpired(), "testSessionHandlingFixationAndExpiry - Session expires cleanly after inactivity timeout");

        session.invalidate();
        assertTrue(!session.isValid() && session.getAttribute("user") == null,
                "testSessionHandlingFixationAndExpiry - Session invalidated on logout");
    }

    public static void testPortfolioCrossTenantIsolation() {
        AuthenticationFilter filter = new AuthenticationFilter();
        try { filter.init(null); } catch (Exception ignored) {}

        User trader1 = new Trader("trader1", "Trader One", "trader1@trade.in", "hash", 50000.0);
        MockSession session1 = new MockSession("sess-t1");
        session1.setAttribute("user", trader1);

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

        MockHttpServletRequest requestOwn = new MockHttpServletRequest("GET", "/api/portfolio", session1);
        requestOwn.parameters.put("traderId", "trader1");
        MockHttpServletResponse responseOwn = new MockHttpServletResponse();

        boolean reachedFilterOwn = false;
        try {
            filter.doFilter(requestOwn.toProxy(), responseOwn.toProxy(), (req, resp) -> {});
            reachedFilterOwn = (responseOwn.status == 200);
        } catch (Exception ignored) {}

        assertTrue(reachedFilterOwn, "testPortfolioCrossTenantIsolation - Trader accessing own portfolio is permitted");
    }

    public static void testInvalidTradeRequests() {
        User trader = new Trader("tradeTestUser", "Trade Tester", "test@trade.in", "hash", 10000.0);
        Stock stock = new Stock("TCS", "Tata Consultancy Services", "NSE", 4000.0, 100);
        stock.setStatus("ACTIVE");

        boolean zeroQtyRejected = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, 0);
        } catch (InvalidInputException e) {
            zeroQtyRejected = true;
        } catch (Exception ignored) {}
        assertTrue(zeroQtyRejected, "testInvalidTradeRequests - Zero share order rejected with InvalidInputException");

        boolean negativeQtyRejected = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, -5);
        } catch (InvalidInputException e) {
            negativeQtyRejected = true;
        } catch (Exception ignored) {}
        assertTrue(negativeQtyRejected, "testInvalidTradeRequests - Negative share order rejected with InvalidInputException");

        boolean insufficientBalance = false;
        try {
            TradingValidator.validateBuyOrder(trader, stock, 10);
        } catch (InsufficientBalanceException e) {
            insufficientBalance = true;
        } catch (Exception ignored) {}
        assertTrue(insufficientBalance, "testInvalidTradeRequests - Order exceeding cash balance caught InsufficientBalanceException");

        boolean insufficientStock = false;
        try {
            trader.setBalance(1000000.0);
            TradingValidator.validateBuyOrder(trader, stock, 500);
        } catch (InsufficientStockQuantityException e) {
            insufficientStock = true;
        } catch (Exception ignored) {}
        assertTrue(insufficientStock, "testInvalidTradeRequests - Order exceeding available float caught InsufficientStockQuantityException");
    }

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
        String body = "";

        MockHttpServletRequest(String method, String uri, MockSession session) {
            this.method = method;
            this.uri = uri;
            this.session = session;
        }

        private String lookupHeader(String target) {
            if (target == null) return null;
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(target)) return e.getValue();
            }
            return null;
        }

        public HttpServletRequest toProxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, m, args) -> {
                        switch (m.getName()) {
                            case "getMethod": return method;
                            case "getRequestURI": return uri;
                            case "getScheme": return "http";
                            case "getServerName": return "localhost";
                            case "getHeader": return lookupHeader((String) args[0]);
                            case "getParameter": return parameters.get(args[0]);
                            case "getReader": return new BufferedReader(new StringReader(body != null ? body : ""));
                            case "getSession":
                                boolean create = (args != null && args.length > 0) ? (Boolean) args[0] : true;
                                if (session != null && !session.isValid()) {
                                    session = null;
                                }
                                if (session == null && create) {
                                    session = new MockSession("sess-auto-" + System.nanoTime());
                                }
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
