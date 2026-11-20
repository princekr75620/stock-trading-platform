package test;

import dao.LoginRateLimitDAO;
import dao.StockDAO;
import dao.UserDAO;
import model.Stock;
import model.Trader;
import model.User;
import server.TradingPlatformHttpServer;
import util.DBConnection;
import util.MoneyUtil;
import util.PasswordHasher;

import java.math.BigDecimal;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-End HTTP-to-JDBC Integration Test Suite.
 * Boots the real TradingPlatformHttpServer (HttpServer + ServletBridge + AuthenticationFilter + Servlets + JDBC),
 * exercises session cookies, CSRF token enforcement, order idempotency, fee precision, cross-tenant checks,
 * session expiration, and DB persistence across server restarts.
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

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static HttpResponse<String> send(HttpClient client, String url, String method, String jsonBody,
                                             String csrfToken, String... extraHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5));

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json");
        }
        if (csrfToken != null && !csrfToken.isEmpty()) {
            builder.header("X-CSRF-Token", csrfToken);
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
        } catch (SQLException ignored) {}
    }

    public static void main(String[] args) {
        if (!runAll()) {
            System.exit(1);
        }
    }

    public static boolean runAll() {
        testsPassed = 0;
        testsTotal = 0;

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
        String haltSym = "HLTE2E" + (System.currentTimeMillis() % 1000);

        TradingPlatformHttpServer server1 = null;
        TradingPlatformHttpServer server2 = null;

        try {
            DBConnection.initializeDatabase();
            server1 = new TradingPlatformHttpServer(port1);
            server1.start();

            UserDAO userDAO = new UserDAO();
            StockDAO stockDAO = new StockDAO();
            HttpClient anonClient = newSessionClient();
            HttpClient regClient = newSessionClient();
            HttpClient trader1Client = newSessionClient();
            HttpClient adminClient = newSessionClient();

            // 1. Live /api/health & /api/db/health return UP + sanitized DB metadata with security headers
            HttpResponse<String> healthRes = send(anonClient, base1 + "/api/health", "GET", null, null);
            assertTrue(healthRes.statusCode() == 200 && healthRes.body().contains("\"status\":\"UP\"")
                            && "nosniff".equalsIgnoreCase(healthRes.headers().firstValue("X-Content-Type-Options").orElse(""))
                            && "SAMEORIGIN".equalsIgnoreCase(healthRes.headers().firstValue("X-Frame-Options").orElse("")),
                    "HTTP-1: GET /api/health returns 200 UP with strict security headers");

            HttpResponse<String> dbHealthRes = send(anonClient, base1 + "/api/db/health", "GET", null, null);
            assertTrue(dbHealthRes.statusCode() == 200
                            && !dbHealthRes.body().contains("stock_trading.db")
                            && dbHealthRes.body().contains("jdbc:sqlite:[embedded-storage]"),
                    "HTTP-1b: GET /api/db/health masks local filesystem paths");

            // 2. Public registration prevents ADMIN escalation & validates fields, stores BCrypt in SQLite
            HttpResponse<String> badReg = send(anonClient, base1 + "/api/auth/register", "POST",
                    "{\"name\":\"Bad\",\"email\":\"invalid-email\",\"password\":\"123\",\"role\":\"ADMIN\"}", null);
            assertTrue(badReg.statusCode() == 400,
                    "HTTP-2: POST /api/auth/register rejects malformed email / short password (400)");

            HttpResponse<String> regRes = send(regClient, base1 + "/api/auth/register", "POST",
                    String.format("{\"userId\":\"%s\",\"name\":\"E2E Trader\",\"email\":\"%s@test.in\",\"password\":\"E2ESecret123!\",\"role\":\"ADMIN\",\"initialBalance\":99999999}",
                            regUid, regUid), null);
            String regCsrf = extractJsonString(regRes.body(), "csrfToken");
            assertTrue(regRes.statusCode() == 201
                            && regRes.body().contains("\"role\":\"TRADER\"")
                            && !regRes.body().contains("\"role\":\"ADMIN\"")
                            && regCsrf != null && !regCsrf.isEmpty(),
                    "HTTP-3: POST /api/auth/register forces TRADER role and issues session csrfToken");

            User persistedReg = userDAO.findById(regUid);
            assertTrue(persistedReg != null && PasswordHasher.isBCryptHash(persistedReg.getPassword()) && Math.abs(persistedReg.getBalance() - 50000.0) < 0.01,
                    "HTTP-4: Registered user persisted in SQLite with BCrypt hash and default 50000.00 balance");

            // Registered trader is denied admin endpoint even with forged header
            HttpResponse<String> regAdminAttempt = send(regClient, base1 + "/api/admin/stats", "GET", null, null, "X-User-Id", "admin");
            assertTrue(regAdminAttempt.statusCode() == 403,
                    "HTTP-5: Registered TRADER denied /api/admin/stats even with forged X-User-Id: admin header (403)");

            // Unauthenticated forged header is denied
            HttpResponse<String> anonForged = send(anonClient, base1 + "/api/admin/users", "GET", null, null, "X-User-Id", "admin");
            assertTrue(anonForged.statusCode() == 401,
                    "HTTP-6: Anonymous request with forged X-User-Id: admin rejected with HTTP 401");

            // 3. Unflagged plaintext password in DB MUST fail normal login; $LEGACY$-marked record migrates to BCrypt
            User plainUser = new Trader(plainUid, "Plain DB User", plainUid + "@test.in", "RawPlainPass123", 10000.0);
            userDAO.insert(plainUser);
            HttpClient plainClient = newSessionClient();
            HttpResponse<String> plainLogin = send(plainClient, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"RawPlainPass123\"}", plainUid), null);
            assertTrue(plainLogin.statusCode() == 401,
                    "HTTP-7: Unflagged plaintext password in DB is strictly rejected on POST /api/auth/login (401)");

            String markedLegacy = PasswordHasher.markLegacyPassword("LegacyPass123!");
            User legacyUser = new Trader(legacyUid, "Legacy DB User", legacyUid + "@test.in", markedLegacy, 15000.0);
            userDAO.insert(legacyUser);
            HttpClient legacyClient = newSessionClient();
            HttpResponse<String> legacyLogin = send(legacyClient, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"LegacyPass123!\"}", legacyUid), null);
            assertTrue(legacyLogin.statusCode() == 200 && legacyLogin.body().contains("\"success\":true"),
                    "HTTP-8: Explicitly marked $LEGACY$ user authenticates once via isolated migration path");

            User migratedRecord = userDAO.findById(legacyUid);
            assertTrue(migratedRecord != null && PasswordHasher.isBCryptHash(migratedRecord.getPassword()),
                    "HTTP-9: Legacy DB record immediately replaced with BCrypt hash via conditional update");

            HttpClient legacyClient2 = newSessionClient();
            HttpResponse<String> postMigrateLogin = send(legacyClient2, base1 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"LegacyPass123!\"}", legacyUid), null);
            assertTrue(postMigrateLogin.statusCode() == 200,
                    "HTTP-10: Subsequent login succeeds against newly stored BCrypt hash");

            // 4. Cross-tenant isolation across all protected endpoints
            HttpResponse<String> t1Login = send(trader1Client, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"trader1\",\"password\":\"trader123\"}", null);
            String t1Csrf = extractJsonString(t1Login.body(), "csrfToken");
            assertTrue(t1Login.statusCode() == 200 && t1Csrf != null, "HTTP-11: Default trader1 logs in cleanly with csrfToken");

            assertTrue(send(trader1Client, base1 + "/api/portfolio?traderId=trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-12: Cross-tenant GET /api/portfolio?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/portfolio/trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-13: Cross-tenant GET /api/portfolio/trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/trades?traderId=trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-14: Cross-tenant GET /api/trades?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/dashboard?traderId=trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-15: Cross-tenant GET /api/dashboard?traderId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/watchlist?userId=trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-16: Cross-tenant GET /api/watchlist?userId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/notifications?userId=trader2", "GET", null, null).statusCode() == 403,
                    "HTTP-17: Cross-tenant GET /api/notifications?userId=trader2 blocked (403)");
            assertTrue(send(trader1Client, base1 + "/api/trades/buy", "POST",
                            "{\"traderId\":\"trader2\",\"symbol\":\"TCS\",\"quantity\":\"1\"}", t1Csrf).statusCode() == 403,
                    "HTTP-18: Cross-tenant POST /api/trades/buy on trader2 rejected (403)");
            assertTrue(send(trader1Client, base1 + "/api/notifications/read", "POST",
                            "{\"userId\":\"trader2\"}", t1Csrf).statusCode() == 403,
                    "HTTP-19: Cross-tenant POST /api/notifications/read on trader2 rejected (403)");

            // Unauthenticated notifications & watchlist rejected
            assertTrue(send(anonClient, base1 + "/api/watchlist?userId=trader1", "GET", null, null).statusCode() == 401,
                    "HTTP-20: Unauthenticated GET /api/watchlist?userId=trader1 rejected (401)");
            assertTrue(send(anonClient, base1 + "/api/notifications?userId=trader1", "GET", null, null).statusCode() == 401,
                    "HTTP-21: Unauthenticated GET /api/notifications?userId=trader1 rejected (401)");

            // 5. CSRF Enforcement on HTTP POST routes
            HttpResponse<String> noCsrfBuy = send(regClient, base1 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"2\"}", null);
            assertTrue(noCsrfBuy.statusCode() == 403,
                    "HTTP-22: POST /api/trades/buy without X-CSRF-Token rejected (403)");

            HttpResponse<String> badCsrfBuy = send(regClient, base1 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"2\"}", "forged-csrf-token");
            assertTrue(badCsrfBuy.statusCode() == 403,
                    "HTTP-23: POST /api/trades/buy with wrong X-CSRF-Token rejected (403)");

            // 6. Real Buy with Idempotency-Key -> Duplicate Replay -> Fee Verification -> Sell -> Restart Persistence
            Stock tcsBefore = stockDAO.findBySymbol("TCS");
            BigDecimal tcsPrice = MoneyUtil.of(tcsBefore.getCurrentPrice());
            BigDecimal expectedBuyTotal = MoneyUtil.calculateBuyTotal(tcsPrice, 2);

            String idemBuyKey = "e2e-buy-" + ts;
            HttpResponse<String> buyRes = send(regClient, base1 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"2\"}", regCsrf, "Idempotency-Key", idemBuyKey);
            assertTrue(buyRes.statusCode() == 200 && buyRes.body().contains("\"success\":true"),
                    "HTTP-24: POST /api/trades/buy with valid X-CSRF-Token executes ACID JDBC trade");

            User afterBuy = userDAO.findById(regUid);
            BigDecimal expectedBalanceAfterBuy = new BigDecimal("50000.00").subtract(expectedBuyTotal);
            assertTrue(afterBuy != null && Math.abs(afterBuy.getBalance() - expectedBalanceAfterBuy.doubleValue()) < 0.01,
                    "HTTP-25: Buy order persisted exact BigDecimal fee-inclusive balance deduction in SQLite");

            HttpResponse<String> dupBuyRes = send(regClient, base1 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"2\"}", regCsrf, "Idempotency-Key", idemBuyKey);
            User afterDupBuy = userDAO.findById(regUid);
            assertTrue(dupBuyRes.statusCode() == 409 && Math.abs(afterDupBuy.getBalance() - expectedBalanceAfterBuy.doubleValue()) < 0.01,
                    "HTTP-26: Replayed Buy order with same Idempotency-Key returned 409 Conflict without double charge");

            // Halted stock blocked over HTTP
            Stock halted = new Stock(haltSym, "Halted E2E Equity", "NSE", 500.0, 100);
            halted.setStatus("HALTED");
            stockDAO.insert(halted);
            HttpResponse<String> haltBuyRes = send(regClient, base1 + "/api/trades/buy", "POST",
                    String.format("{\"symbol\":\"%s\",\"quantity\":\"1\"}", haltSym), regCsrf);
            assertTrue(haltBuyRes.statusCode() == 400,
                    "HTTP-27: Buying HALTED stock rejected (400)");

            // Check session freshness returns DB balance (not stale login snapshot)
            HttpResponse<String> sessAfterBuy = send(regClient, base1 + "/api/auth/session", "GET", null, null);
            assertTrue(sessAfterBuy.statusCode() == 200 && !sessAfterBuy.body().contains("50000.00"),
                    "HTTP-28: GET /api/auth/session returns fresh post-trade DB cashBalance");

            // Sell 1 share
            HttpResponse<String> sellRes = send(regClient, base1 + "/api/trades/sell", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"1\"}", regCsrf, "Idempotency-Key", "e2e-sell-" + ts);
            assertTrue(sellRes.statusCode() == 200 && sellRes.body().contains("\"success\":true"),
                    "HTTP-29: POST /api/trades/sell executes partial sell and updates portfolio in DB");
            double balanceAfterSell = userDAO.findById(regUid).getBalance();

            // Untrusted X-Forwarded-Proto: https MUST NOT mark cookie Secure by default (TRUST_PROXY=false)
            HttpClient untrustedProtoClient = newSessionClient();
            HttpResponse<String> untrustedProtoLogin = send(untrustedProtoClient, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"admin\",\"password\":\"admin123\"}", null,
                    "X-Forwarded-Proto", "https");
            String untrustedCookie = untrustedProtoLogin.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(untrustedProtoLogin.statusCode() == 200
                            && untrustedCookie.contains("JSESSIONID=")
                            && !untrustedCookie.contains("; Secure"),
                    "HTTP-30: Untrusted X-Forwarded-Proto: https is ignored when TRUST_PROXY=false");

            // Check Secure cookie attribute when behind an explicitly configured HTTPS reverse proxy
            System.setProperty("TRUST_PROXY", "true");
            System.setProperty("TRUSTED_PROXIES", "127.0.0.1");
            HttpClient httpsHeaderCheckClient = newSessionClient();
            HttpResponse<String> httpsAdmLogin = send(httpsHeaderCheckClient, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"admin\",\"password\":\"admin123\"}", null,
                    "X-Forwarded-Proto", "https");
            System.clearProperty("TRUST_PROXY");
            System.clearProperty("TRUSTED_PROXIES");
            String httpsAdmCookie = httpsAdmLogin.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(httpsAdmLogin.statusCode() == 200
                            && httpsAdmCookie.contains("JSESSIONID=")
                            && httpsAdmCookie.contains("HttpOnly")
                            && httpsAdmCookie.contains("SameSite=Lax")
                            && httpsAdmCookie.contains("Path=/")
                            && httpsAdmCookie.contains("; Secure"),
                    "HTTP-30a: Trusted HTTPS proxy peer sets HttpOnly, SameSite=Lax, Path=/, and Secure");

            // Admin login over plain HTTP test listener so Java CookieManager attaches JSESSIONID on follow-up requests
            HttpResponse<String> admLogin = send(adminClient, base1 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"admin\",\"password\":\"admin123\"}", null);
            String admCsrf = extractJsonString(admLogin.body(), "csrfToken");
            assertTrue(admLogin.statusCode() == 200 && admCsrf != null,
                    "HTTP-30a: Admin authentication succeeds over HTTP test transport");

            // Admin PUT & DELETE CSRF protection over live HTTP
            String managedUid = "e2e_mgmt_" + ts;
            HttpResponse<String> admCreate = send(adminClient, base1 + "/api/admin/users", "POST",
                    String.format("{\"userId\":\"%s\",\"name\":\"Managed User\",\"email\":\"%s@test.in\",\"password\":\"Manage123!\",\"role\":\"TRADER\"}", managedUid, managedUid),
                    admCsrf);
            assertTrue(admCreate.statusCode() == 201, "HTTP-30b: Admin POST /api/admin/users with CSRF creates trader");

            HttpResponse<String> putNoCsrf = send(adminClient, base1 + "/api/admin/users", "PUT",
                    String.format("{\"userId\":\"%s\",\"name\":\"Updated Name\"}", managedUid), null);
            HttpResponse<String> putWithCsrf = send(adminClient, base1 + "/api/admin/users", "PUT",
                    String.format("{\"userId\":\"%s\",\"name\":\"Updated Name\"}", managedUid), admCsrf);
            assertTrue(putNoCsrf.statusCode() == 403 && putWithCsrf.statusCode() == 200,
                    "HTTP-30c: PUT /api/admin/users enforces CSRF (403 without token, 200 with valid token)");

            HttpResponse<String> delNoCsrf = send(adminClient, base1 + "/api/admin/users", "DELETE",
                    String.format("{\"userId\":\"%s\"}", managedUid), null);
            HttpResponse<String> delWithCsrf = send(adminClient, base1 + "/api/admin/users", "DELETE",
                    String.format("{\"userId\":\"%s\"}", managedUid), admCsrf);
            assertTrue(delNoCsrf.statusCode() == 403 && delWithCsrf.statusCode() == 200,
                    "HTTP-30d: DELETE /api/admin/users enforces CSRF (403 without token, 200 with valid token)");

            HttpResponse<String> admStats = send(adminClient, base1 + "/api/admin/dashboard", "GET", null, null);
            assertTrue(admStats.statusCode() == 200 && admStats.body().contains("SQLite"),
                    "HTTP-31: Admin dashboard reports authentic live databaseEngine (SQLite)");

            // Stop server1 completely and launch fresh server2 to prove data survives server restart!
            server1.stop();
            server1 = null;

            server2 = new TradingPlatformHttpServer(port2);
            server2.start();

            HttpClient freshPostRestartClient = newSessionClient();
            HttpResponse<String> reLogin = send(freshPostRestartClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"E2ESecret123!\"}", regUid), null);
            String postRestartCsrf = extractJsonString(reLogin.body(), "csrfToken");
            assertTrue(reLogin.statusCode() == 200 && postRestartCsrf != null,
                    "HTTP-32: Login works on fresh server instance after restart");

            HttpResponse<String> portAfterRestart = send(freshPostRestartClient, base2 + "/api/portfolio", "GET", null, null);
            assertTrue(portAfterRestart.statusCode() == 200 &&
                            portAfterRestart.body().contains("\"symbol\":\"TCS\"") &&
                            portAfterRestart.body().contains("\"quantity\":1") &&
                            portAfterRestart.body().contains(String.format(java.util.Locale.US, "%.2f", balanceAfterSell)),
                    "HTTP-33: Portfolio holdings (1 TCS) and DB balance survived full server restart via JDBC");

            HttpResponse<String> tradesAfterRestart = send(freshPostRestartClient, base2 + "/api/trades", "GET", null, null);
            assertTrue(tradesAfterRestart.statusCode() == 200 &&
                            tradesAfterRestart.body().contains("\"tradeType\":\"BUY\"") &&
                            tradesAfterRestart.body().contains("\"tradeType\":\"SELL\""),
                    "HTTP-34: Transaction audit trail persisted across full server restart via JDBC");

            // GET /api/auth/logout rejected (405); POST logout with valid CSRF succeeds (200)
            HttpResponse<String> getLogout = send(freshPostRestartClient, base2 + "/api/auth/logout", "GET", null, null);
            assertTrue(getLogout.statusCode() == 405, "HTTP-35: GET /api/auth/logout rejected with 405 Method Not Allowed");

            HttpResponse<String> logoutRes = send(freshPostRestartClient, base2 + "/api/auth/logout", "POST", "{}", postRestartCsrf);
            String logoutCookie = logoutRes.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(logoutRes.statusCode() == 200 && logoutCookie.contains("Max-Age=0"),
                    "HTTP-36: POST /api/auth/logout with CSRF token succeeds and clears JSESSIONID cookie (Max-Age=0)");
            HttpResponse<String> postLogoutSession = send(freshPostRestartClient, base2 + "/api/auth/session", "GET", null, null);
            assertTrue(postLogoutSession.statusCode() == 401, "HTTP-37: Session invalidated after logout (401 on /api/auth/session)");

            // Oversized request body protection (413 Payload Too Large)
            System.setProperty("max.request.body.bytes", "256");
            StringBuilder bigPayload = new StringBuilder("{\"usernameOrEmail\":\"");
            for (int i = 0; i < 400; i++) bigPayload.append('a');
            bigPayload.append("\",\"password\":\"Secret123!\"}");
            HttpResponse<String> oversizedRes = send(anonClient, base2 + "/api/auth/login", "POST", bigPayload.toString(), null);
            System.clearProperty("max.request.body.bytes");
            assertTrue(oversizedRes.statusCode() == 413,
                    "HTTP-37b: Oversized JSON body exceeding max.request.body.bytes rejected with 413 Payload Too Large");

            // Real HTTP Session Inactivity Timeout Test
            System.setProperty("session.timeout.seconds", "1");
            HttpClient expClient = newSessionClient();
            HttpResponse<String> expLogin = send(expClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"E2ESecret123!\"}", regUid), null);
            assertTrue(expLogin.statusCode() == 200, "HTTP-38: Session created with 1s inactivity timeout");
            Thread.sleep(1250);
            HttpResponse<String> expSess = send(expClient, base2 + "/api/auth/session", "GET", null, null);
            HttpResponse<String> expPortfolio = send(expClient, base2 + "/api/portfolio", "GET", null, null);
            assertTrue(expSess.statusCode() == 401 && expPortfolio.statusCode() == 401,
                    "HTTP-39: Expired session returns 401 on /api/auth/session and /api/portfolio");

            // 7. Verify ServletBridge sendError(405), duplicate register 409, UpdateStock 400, Watchlist 404
            HttpResponse<String> method405 = send(regClient, base2 + "/api/trades/buy", "GET", null, null);
            assertTrue(method405.statusCode() == 405,
                    "HTTP-40: Unsupported GET /api/trades/buy returns HTTP 405 via ServletBridge sendError()");

            HttpResponse<String> dupReg = send(anonClient, base2 + "/api/auth/register", "POST",
                    String.format("{\"name\":\"Dup Email\",\"email\":\"%s@test.in\",\"password\":\"StrongDup@2026!\"}", regUid), null);
            assertTrue(dupReg.statusCode() == 409,
                    "HTTP-41: Duplicate email registration returns HTTP 409 Conflict");

            HttpResponse<String> badWatch = send(regClient, base2 + "/api/watchlist", "POST",
                    "{\"symbol\":\"NONEXISTENT99\",\"action\":\"add\"}", regCsrf);
            assertTrue(badWatch.statusCode() == 404,
                    "HTTP-42: Adding nonexistent ticker to watchlist returns HTTP 404 Not Found");

            HttpClient admClient2 = newSessionClient();
            HttpResponse<String> admLogin2 = send(admClient2, base2 + "/api/auth/login", "POST",
                    "{\"usernameOrEmail\":\"admin\",\"password\":\"admin123\"}", null);
            String admCsrf2 = extractJsonString(admLogin2.body(), "csrfToken");
            HttpResponse<String> badStockPrice = send(admClient2, base2 + "/api/admin/stocks/update", "POST",
                    "{\"symbol\":\"TCS\",\"price\":\"-50\"}", admCsrf2);
            assertTrue(badStockPrice.statusCode() == 400,
                    "HTTP-43: Admin updating stock with negative price returns HTTP 400 Bad Request");

            // 8. Spoofed X-Forwarded-Host / Origin / Forwarded header trust checks over live HTTP
            HttpResponse<String> spoofXfhOrigin = send(regClient, base2 + "/api/trades/buy", "POST",
                    "{\"symbol\":\"TCS\",\"quantity\":\"1\"}", regCsrf,
                    "X-Forwarded-Host", "evil.com",
                    "Origin", "http://evil.com");
            assertTrue(spoofXfhOrigin.statusCode() == 403,
                    "HTTP-44: Spoofed X-Forwarded-Host: evil.com + Origin: http://evil.com rejected with 403");

            HttpResponse<String> spoofXfhHealth = send(anonClient, base2 + "/api/health", "GET", null, null,
                    "X-Forwarded-Host", "evil.com");
            assertTrue(spoofXfhHealth.statusCode() == 403,
                    "HTTP-45: Spoofed X-Forwarded-Host: evil.com on /api/health rejected with 403");

            // 9. Login anti-enumeration & DB-backed progressive rate limiting over live HTTP
            LoginRateLimitDAO rateDao = new LoginRateLimitDAO();
            rateDao.reset("ip:127.0.0.1");
            rateDao.reset("acct:" + regUid.toLowerCase());
            rateDao.reset("acct:e2e_unknown_" + ts);

            HttpResponse<String> badPwResp = send(anonClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"WrongPassword!\"}", regUid), null);
            rateDao.reset("ip:127.0.0.1");
            HttpResponse<String> unknownUserResp = send(anonClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"e2e_unknown_%s\",\"password\":\"WrongPassword!\"}", ts), null);
            assertTrue(badPwResp.statusCode() == 401
                            && unknownUserResp.statusCode() == 401
                            && badPwResp.body().equals(unknownUserResp.body()),
                    "HTTP-46: Live POST /api/auth/login emits identical 401 generic JSON for bad password and nonexistent account");

            rateDao.reset("acct:" + regUid.toLowerCase());
            rateDao.reset("ip:127.0.0.1");
            for (int i = 0; i < 5; i++) {
                send(anonClient, base2 + "/api/auth/login", "POST",
                        String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"WrongAttempt%d!\"}", regUid, i), null);
            }
            HttpResponse<String> throttledResp = send(anonClient, base2 + "/api/auth/login", "POST",
                    String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"E2ESecret123!\"}", regUid), null);
            assertTrue(throttledResp.statusCode() == 429
                            && throttledResp.headers().firstValue("Retry-After").isPresent()
                            && throttledResp.body().contains("Too many authentication requests"),
                    "HTTP-47: Repeated failed logins trigger HTTP 429 with Retry-After and non-leaky message");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "HTTP Integration suite unexpected error: " + e.getMessage());
        } finally {
            System.clearProperty("session.timeout.seconds");
            System.clearProperty("max.request.body.bytes");
            System.clearProperty("TRUST_PROXY");
            System.clearProperty("TRUSTED_PROXIES");
            if (server1 != null) server1.stop();
            if (server2 != null) server2.stop();
            LoginRateLimitDAO cleanupRate = new LoginRateLimitDAO();
            for (String key : new String[]{
                    "ip:127.0.0.1",
                    "acct:" + regUid.toLowerCase(),
                    "acct:" + plainUid.toLowerCase(),
                    "acct:" + legacyUid.toLowerCase(),
                    "acct:e2e_unknown_" + ts,
                    "acct:admin",
                    "acct:trader1"
            }) {
                try {
                    cleanupRate.reset(key);
                } catch (SQLException ignored) {}
            }
            deleteUserCascade(regUid);
            deleteUserCascade(plainUid);
            deleteUserCascade(legacyUid);
            deleteUserCascade("e2e_mgmt_" + ts);
            try {
                new StockDAO().delete(haltSym);
            } catch (SQLException ignored) {}
        }

        System.out.printf("\nEndToEndHttpIntegrationTest Results: %d/%d Passed.\n\n", testsPassed, testsTotal);
        return testsPassed == testsTotal;
    }
}
