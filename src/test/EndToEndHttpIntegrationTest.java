package test;

import dao.UserDAO;
import model.Trader;
import model.User;
import server.TradingPlatformHttpServer;
import util.DBConnection;
import util.PasswordHasher;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * End-to-End HTTP-to-JDBC Integration Test Suite.
 * Boots the real TradingPlatformHttpServer (HttpServer + ServletBridge + AuthenticationFilter + Servlets + SQLite JDBC),
 * issues real HTTP requests with session cookies, and verifies DB state persistence across server restarts.
 */
public class EndToEndHttpIntegrationTest {
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

    private static HttpClient newSessionClient() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String jsonBody, String... extraHeaders)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5));

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json");
            builder.header("X-Requested-With", "XMLHttpRequest");
        }
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            builder.header(extraHeaders[i], extraHeaders[i + 1]);
        }

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"));
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void deleteUserCascade(String userId) {
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE user_id = ?")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM portfolio WHERE user_id = ?")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM watchlist WHERE user_id = ?")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   END-TO-END HTTP + SERVLETBRIDGE + JDBC INTEGRATION SUITE   ");
        System.out.println("===============================================================");

        int port1 = 18765;
        int port2 = 18766;
        String base1 = "http://127.0.0.1:" + port1;
        String base2 = "http://127.0.0.1:" + port2;

        String ts = String.valueOf(System.currentTimeMillis() % 1000000);
        String regUid = "e2e_reg_" + ts;
        String plainUid = "e2e_plain_" + ts;
        String legacyUid = "e2e_leg_" + ts;

        TradingPlatformHttpServer server1 = null;
        TradingPlatformHttpServer server2 = null;

        try {
            DBConnection.initializeDatabase();
            server1 = new TradingPlatformHttpServer(port1);
            server1.start();

            UserDAO userDAO = new UserDAO();
            HttpClient anonClient = newSessionClient();
            HttpClient regClient = newSessionClient();
            HttpClient trader1Client = newSessionClient();
            HttpClient adminClient = newSessionClient();

            // 1. Live /api/health returns UP + DB metadata
            HttpResponse<String> healthRes = send(anonClient, base1 + "/api/health", "GET", null);
            assertTrue(healthRes.statusCode() == 200 && healthRes.body().contains("\"status\":\"UP\""),
                    "HTTP-1: GET /api/health returns 200 UP with live DB connection");

            // 2. Public registration prevents ADMIN escalation & validates fields, stores BCrypt in SQLite
            HttpResponse<String> badReg = send(anonClient, base1 + "/api/auth/register", "POST",
                    "{\"name\":\"Bad\",\"email\":\"invalid-email\",\"password\":\"123\",\"role\":\"ADMIN\"}");
            assertTrue(badReg.statusCode() == 400,
                    "HTTP-2: POST /api/auth/register rejects malformed email / short password (400)");

            HttpResponse<String> regRes = send(regClient, base1 + "/api/auth/register", "POST",
                    String.format("{\"userId\":\"%s\",\"name\":\"E2E Trader\",\"email\":\"%s@test.in\",\"password\":\"E2ESecret123!\",\"role\":\"ADMIN\",\"initialBalance\":99999999}",
                            regUid, regUid));
            assertTrue(regRes.statusCode() == 201 && regRes.body().contains("\"role\":\"TRADER\"") && !regRes.body().contains("\"role\":\"ADMIN\""),
                    "HTTP-3: POST /api/auth/register forces TRADER role even when role=ADMIN submitted");

            User persistedReg = userDAO.findById(regUid);
            assertTrue(persistedReg != null && PasswordHasher.isBCryptHash(persistedReg.getPassword()) && Math.abs(persistedReg.getBalance() - 50000.0) < 0.01,
                    "HTTP-4: Registered user persisted in SQLite with BCrypt hash and default 50000.00 balance");

            // Registered trader is denied admin endpoint even with forged header
            HttpResponse<String> regAdminAttempt = send(regClient, base1 + "/api/admin/stats", "GET", null, "X-User-Id", "admin");
            assertTrue(regAdminAttempt.statusCode() == 403,
                    "HTTP-5: Registered TRADER denied /api/admin/stats even with forged X-User-Id: admin header (403)");

            // Unauthenticated forged header is denied
            HttpResponse<String> anonForged = send(anonClient, base1 + "/api/admin/users", "GET", null, "X-User-Id", "admin");
            assertTrue(anonForged.statusCode() == 401,
                    "HTTP-6: Anonymous request with forged X-User-Id: admin rejected with HTTP 401");

            // 3. Unflagged plaintext password in DB MUST fail normal login; $LEGACY$-marked record migrates to BCrypt
            User plainUser = new Trader(plainUid, "Plain DB User", plainUid + "@test.in", "RawPlainPass123", 10000.0);
            userDAO.insert(plainUser);
            HttpClient plainClient = newSessionClient();
            HttpResponse<String> plainLogin = send(plainClient, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"RawPlainPass123\"}", plainUid));
            assertTrue(plainLogin.statusCode() == 401,
                    "HTTP-7: Unflagged plaintext password in DB is strictly rejected on POST /api/auth/login (401)");

            String markedLegacy = PasswordHasher.markLegacyPassword("LegacyPass123!");
            User legacyUser = new Trader(legacyUid, "Legacy DB User", legacyUid + "@test.in", markedLegacy, 15000.0);
            userDAO.insert(legacyUser);
            HttpClient legacyClient = newSessionClient();
            HttpResponse<String> legacyLogin = send(legacyClient, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"LegacyPass123!\"}", legacyUid));
            assertTrue(legacyLogin.statusCode() == 200 && legacyLogin.body().contains("\"success\":true"),
                    "HTTP-8: Explicitly marked $LEGACY$ user authenticates once via isolated migration path");

            User migratedRecord = userDAO.findById(legacyUid);
            assertTrue(migratedRecord != null && PasswordHasher.isBCryptHash(migratedRecord.getPassword()),
                    "HTTP-9: Legacy DB record immediately replaced with BCrypt hash via conditional update");

            HttpClient legacyClient2 = newSessionClient();
            HttpResponse<String> postMigrateLogin = send(legacyClient2, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"LegacyPass123!\"}", legacyUid));
            assertTrue(postMigrateLogin.statusCode() == 200,
                    "HTTP-10: Subsequent login succeeds against newly stored BCrypt hash");

            // 4. Cross-tenant isolation across all protected endpoints
            HttpResponse<String> t1Login = send(trader1Client, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"trader1\",\"password\":\"trader123\"}");
            assertTrue(t1Login.statusCode() == 200, "HTTP-11: Default trader1 logs in cleanly");

            assertTrue(send(trader1Client, base1 + "/api/portfolio?traderId=trader2", "GET", null).statusCode() == 403,
                    "HTTP-12: Cross-tenant GET /api/portfolio?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/portfolio/trader2", "GET", null).statusCode() == 403,
                    "HTTP-13: Cross-tenant GET /api/portfolio/trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/trades?traderId=trader2", "GET", null).statusCode() == 403,
                    "HTTP-14: Cross-tenant GET /api/trades?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/dashboard?traderId=trader2", "GET", null).statusCode() == 403,
                    "HTTP-15: Cross-tenant GET /api/dashboard?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/watchlist?userId=trader2", "GET", null).statusCode() == 403,
                    "HTTP-16: Cross-tenant GET /api/watchlist?userId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/notifications?userId=trader2", "GET", null).statusCode() == 403,
                    "HTTP-17: Cross-tenant GET /api/notifications?userId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/trades/buy", "POST",
                            "{\"traderId\":\"trader2\",\"symbol\":\"TCS\",\"quantity\":\"1\"}").statusCode() == 403,
                    "HTTP-18: Cross-tenant POST /api/trades/buy on trader2 rejected (403)");

            // Unauthenticated notifications & watchlist rejected
            assertTrue(send(anonClient, base1 + "/api/watchlist?userId=trader1", "GET", null).statusCode() == 401,
                    "HTTP-19: Unauthenticated GET /api/watchlist?userId=trader1 rejected (401)");
            assertTrue(send(anonClient, base1 + "/api/notifications?userId=trader1", "GET", null).statusCode() == 401,
                    "HTTP-20: Unauthenticated GET /api/notifications?userId=trader1 rejected (401)");

            // 5. Real Buy -> Sell -> DB balance & holdings + Persistence across full server restart
            HttpResponse<String> buyRes = send(regClient, base1 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"2\"}");
            assertTrue(buyRes.statusCode() == 200 && buyRes.body().contains("\"success\":true") && !buyRes.body().contains("\"cashBalance\":0.00"),
                    "HTTP-21: POST /api/trades/buy executes ACID JDBC trade and returns real updated cashBalance");

            User afterBuy = userDAO.findById(regUid);
            assertTrue(afterBuy != null && afterBuy.getBalance() < 50000.0,
                    "HTTP-22: Buy order persisted balance deduction directly in SQLite database");

            // Check session freshness returns DB balance (not stale login snapshot)
            HttpResponse<String> sessAfterBuy = send(regClient, base1 + "/api/auth/session", "GET", null);
            assertTrue(sessAfterBuy.statusCode() == 200 && !sessAfterBuy.body().contains("50000.00"),
                    "HTTP-23: GET /api/auth/session returns fresh post-trade DB cashBalance");

            // Sell 1 share
            HttpResponse<String> sellRes = send(regClient, base1 + "/api/trades/sell", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"1\"}");
            assertTrue(sellRes.statusCode() == 200 && sellRes.body().contains("\"success\":true"),
                    "HTTP-24: POST /api/trades/sell executes partial sell and updates portfolio in DB");
            double balanceAfterSell = userDAO.findById(regUid).getBalance();

            // Admin login succeeds & /api/admin/stats works and shows live SQLite engine
            HttpResponse<String> admLogin = send(adminClient, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"admin\",\"password\":\"admin123\"}");
            assertTrue(admLogin.statusCode() == 200, "HTTP-25: Admin authentication succeeds");
            HttpResponse<String> admStats = send(adminClient, base1 + "/api/admin/dashboard", "GET", null);
            assertTrue(admStats.statusCode() == 200 && admStats.body().contains("SQLite"),
                    "HTTP-26: Admin dashboard reports authentic live databaseEngine (SQLite)");

            // Stop server1 completely and launch fresh server2 to prove data survives server restart!
            server1.stop();
            server1 = null;

            server2 = new TradingPlatformHttpServer(port2);
            server2.start();

            HttpClient freshPostRestartClient = newSessionClient();
            HttpResponse<String> reLogin = send(freshPostRestartClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"E2ESecret123!\"}", regUid));
            assertTrue(reLogin.statusCode() == 200, "HTTP-27: Login works on fresh server instance after restart");

            HttpResponse<String> portAfterRestart = send(freshPostRestartClient, base2 + "/api/portfolio", "GET", null);
            assertTrue(portAfterRestart.statusCode() == 200 &&
                            portAfterRestart.body().contains("\"symbol\":\"TCS\"") &&
                            portAfterRestart.body().contains("\"quantity\":1") &&
                            portAfterRestart.body().contains(String.format(java.util.Locale.US, "%.2f", balanceAfterSell)),
                    "HTTP-28: Portfolio holdings (1 TCS) and DB balance survived full server restart via JDBC");

            HttpResponse<String> tradesAfterRestart = send(freshPostRestartClient, base2 + "/api/trades", "GET", null);
            assertTrue(tradesAfterRestart.statusCode() == 200 &&
                            tradesAfterRestart.body().contains("\"tradeType\":\"BUY\"") &&
                            tradesAfterRestart.body().contains("\"tradeType\":\"SELL\""),
                    "HTTP-29: Transaction audit trail persisted across full server restart via JDBC");

            // Logout clears server session
            HttpResponse<String> logoutRes = send(freshPostRestartClient, base2 + "/api/auth/logout", "POST", "{}");
            assertTrue(logoutRes.statusCode() == 200, "HTTP-30: POST /api/auth/logout succeeds");
            HttpResponse<String> postLogoutSession = send(freshPostRestartClient, base2 + "/api/auth/session", "GET", null);
            assertTrue(postLogoutSession.statusCode() == 401, "HTTP-31: Session invalidated after logout (401 on /api/auth/session)");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "HTTP Integration suite unexpected error: " + e.getMessage());
        } finally {
            if (server1 != null) server1.stop();
            if (server2 != null) server2.stop();
            deleteUserCascade(regUid);
            deleteUserCascade(plainUid);
            deleteUserCascade(legacyUid);
        }

        System.out.printf("\nEndToEndHttpIntegrationTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        if (testsPassed != testsTotal) {
            System.exit(1);
        }
    }
}
