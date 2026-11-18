package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import filter.AuthenticationFilter;
import service.*;
import servlet.*;
import util.DBConnection;

import javax.servlet.http.HttpServlet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP REST Server built on Java SE (com.sun.net.httpserver.HttpServer)
 * dispatching all API routes through ServletBridge + AuthenticationFilter + Servlets
 * backed authoritatively by JDBC persistence (SQLite default / MySQL optional).
 */
public class TradingPlatformHttpServer {
    private static final int DEFAULT_PORT = 8765;

    private final HttpServer server;
    private final int port;
    private final String bindHost;

    // Authoritative JDBC-backed Services
    private final IUserService userService;
    private final IMarketUpdateService marketUpdateService;
    private final IPortfolioService portfolioService;
    private final INotificationService notificationService;
    private final ITradingService tradingService;
    private final ISecurityService securityService;
    private final ISystemSettingsService systemSettingsService;
    private final IReportService reportService;
    private final IAuthenticationService authService;
    private final ServletBridge servletBridge;

    // Background scheduler for simulated intraday market quote ticks
    private final ScheduledExecutorService marketScheduler;
    private final ExecutorService httpExecutor;

    public TradingPlatformHttpServer(int port) throws IOException {
        this.port = port;
        this.bindHost = resolveSafeBindHost(System.getenv("JAVA_HOST"));

        // 1. Initialize relational database schema & seed initial defaults if empty
        DBConnection.initializeDatabase();

        // 2. Construct authoritative JDBC-backed services (no .txt / HashMap state overrides)
        this.userService = new UserService();
        this.notificationService = new NotificationService();
        this.marketUpdateService = new MarketUpdateService(notificationService);
        this.portfolioService = new PortfolioService();
        this.tradingService = new TradingService(
                marketUpdateService, portfolioService, notificationService, userService
        );
        this.securityService = new SecurityService(new model.SecuritySettings(), null);
        this.systemSettingsService = new SystemSettingsService(new model.SystemSettings());
        this.reportService = new ReportService(
                userService, tradingService, portfolioService,
                marketUpdateService, securityService, systemSettingsService
        );
        this.authService = new AuthenticationService(userService, securityService);

        // Initialize BaseServlet shared services
        BaseServlet.initializeServices(
                userService, marketUpdateService, portfolioService,
                notificationService, (TradingService) tradingService,
                securityService, systemSettingsService, reportService, authService
        );

        // 3. Setup ServletBridge & register all Java Servlets wrapped by AuthenticationFilter
        this.servletBridge = new ServletBridge();
        registerServlets();

        // 4. Setup HTTP Server bound cleanly to configured internal host (default 127.0.0.1)
        this.httpExecutor = Executors.newFixedThreadPool(16);
        this.server = HttpServer.create(new InetSocketAddress(this.bindHost, port), 0);
        this.server.setExecutor(this.httpExecutor);
        this.server.createContext("/api/", new ApiHandler());

        // 5. Setup background simulated market tick scheduler (flushes quote ticks to StockDAO)
        this.marketScheduler = Executors.newSingleThreadScheduledExecutor();
        this.marketScheduler.scheduleAtFixedRate(this::simulateMarketTick, 10, 10, TimeUnit.SECONDS);
    }

    private void simulateMarketTick() {
        try {
            model.SystemSettings settings = systemSettingsService.getSettings();
            if (settings.isMaintenanceMode() || settings.getMarketStatus() != model.SystemSettings.MarketStatus.OPEN) {
                return;
            }
            marketUpdateService.simulateMarketTick();
        } catch (Exception e) {
            System.err.println("[MarketTicker] Tick error: " + e.getMessage());
        }
    }

    private void registerServlets() {
        servletBridge.registerServlet("/api/auth/login", new LoginServlet());
        servletBridge.registerServlet("/api/auth/register", new RegisterServlet());
        servletBridge.registerServlet("/api/auth/logout", new LogoutServlet());
        servletBridge.registerServlet("/api/auth/session", new AuthSessionServlet());
        servletBridge.registerServlet("/api/dashboard", new DashboardServlet());
        servletBridge.registerServlet("/api/stocks", new StockServlet());
        servletBridge.registerServlet("/api/trades/buy", new BuyStockServlet());
        servletBridge.registerServlet("/api/trade/buy", new BuyStockServlet());
        servletBridge.registerServlet("/api/trades/sell", new SellStockServlet());
        servletBridge.registerServlet("/api/trade/sell", new SellStockServlet());
        servletBridge.registerServlet("/api/portfolio", new PortfolioServlet());
        servletBridge.registerServlet("/api/trades", new TransactionServlet());
        servletBridge.registerServlet("/api/transactions", new TransactionServlet());
        servletBridge.registerServlet("/api/admin/dashboard", new AdminDashboardServlet());
        servletBridge.registerServlet("/api/admin/stats", new AdminDashboardServlet());
        servletBridge.registerServlet("/api/admin/users", new UserManagementServlet());
        servletBridge.registerServlet("/api/admin/users/update", new UserManagementServlet());
        servletBridge.registerServlet("/api/admin/users/delete", new UserManagementServlet());
        servletBridge.registerServlet("/api/admin/stocks", new AddStockServlet());
        servletBridge.registerServlet("/api/admin/stocks/add", new AddStockServlet());
        servletBridge.registerServlet("/api/admin/stocks/update", new UpdateStockServlet());
        servletBridge.registerServlet("/api/admin/stocks/delete", new DeleteStockServlet());
        servletBridge.registerServlet("/api/admin/security", new AdminSecurityServlet());
        servletBridge.registerServlet("/api/admin/settings", new AdminSettingsServlet());
        servletBridge.registerServlet("/api/admin/reports", new AdminReportServlet());
        servletBridge.registerServlet("/api/market/tick", new MarketTickServlet());
        servletBridge.registerServlet("/api/market/updates", new MarketUpdateServlet());
        servletBridge.registerServlet("/api/notifications", new NotificationServlet());
        servletBridge.registerServlet("/api/notifications/read", new NotificationServlet());
        servletBridge.registerServlet("/api/trader/deposit", new TraderPreferenceServlet());
        servletBridge.registerServlet("/api/trader/preferences", new TraderPreferenceServlet());
        servletBridge.registerServlet("/api/watchlist", new WatchlistServlet());
        servletBridge.registerServlet("/api/db/health", new DatabaseHealthServlet());
        System.out.println("[TradingPlatformHttpServer] Registered all Java Servlets via ServletBridge & AuthenticationFilter (JDBC authoritative).");
    }

    public void start() {
        server.start();
        System.out.println("[TradingPlatformHttpServer] Java REST Server running on http://" + bindHost + ":" + port);
    }

    public void stop() {
        marketScheduler.shutdownNow();
        server.stop(1);
        httpExecutor.shutdownNow();
        System.out.println("[TradingPlatformHttpServer] Java REST Server stopped.");
    }

    private static String resolveSafeBindHost(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            return "127.0.0.1";
        }
        String candidate = requested.trim();
        boolean allowPublic = "true".equalsIgnoreCase(System.getenv("ALLOW_PUBLIC_JAVA_BIND"));
        if (!allowPublic && ("0.0.0.0".equals(candidate) || "::".equals(candidate) || "[::]".equals(candidate))) {
            System.err.println("[TradingPlatformHttpServer] Refusing wildcard bind '" + candidate + "' without ALLOW_PUBLIC_JAVA_BIND=true; binding to 127.0.0.1.");
            return "127.0.0.1";
        }
        return candidate;
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            applyCorsHeaders(exchange);
            applySecurityHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            String body;
            try {
                body = readRequestBody(exchange);
            } catch (PayloadTooLargeException pte) {
                sendJsonResponse(exchange, 413,
                        "{\"success\":false,\"error\":\"Request payload exceeds maximum allowed size.\"}");
                return;
            }

            try {
                if (path.equals("/api/health") && method.equals("GET")) {
                    Map<String, Object> dbHealth = DBConnection.getHealthStatus();
                    boolean dbConnected = Boolean.TRUE.equals(dbHealth.get("connected"));
                    String engineType = String.valueOf(dbHealth.getOrDefault("engineType", "SQLite"));
                    String json = String.format(
                            "{\"status\":\"%s\",\"databaseConnected\":%b,\"databaseEngine\":\"%s\",\"platform\":\"Online Stock Trading Platform\",\"timestamp\":\"%s\"}",
                            dbConnected ? "UP" : "DOWN",
                            dbConnected,
                            JsonHelper.escape(engineType),
                            LocalDateTime.now()
                    );
                    sendJsonResponse(exchange, dbConnected ? 200 : 503, json);
                    return;
                }

                HttpServlet servlet = servletBridge.getServlet(path);
                if (servlet != null) {
                    servletBridge.dispatch(exchange, servlet, body);
                    return;
                }

                sendJsonResponse(exchange, 404,
                        "{\"success\":false,\"error\":\"Endpoint not found: " + JsonHelper.escape(path) + "\"}");
            } catch (Exception ex) {
                System.err.println("[TradingPlatformHttpServer] Unhandled API error on " + path + ": " + ex.getMessage());
                sendJsonResponse(exchange, 500,
                        "{\"success\":false,\"error\":\"Internal server error processing request.\"}");
            }
        }

        private void applySecurityHeaders(HttpExchange ex) {
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
            ex.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
            ex.getResponseHeaders().set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        }

        private void applyCorsHeaders(HttpExchange ex) {
            List<String> origins = ex.getRequestHeaders().get("Origin");
            String origin = (origins != null && !origins.isEmpty()) ? origins.get(0).trim() : null;
            if (origin != null && !origin.isEmpty()) {
                Set<String> allowedOrigins = AuthenticationFilter.getConfiguredAllowedOrigins();
                if (allowedOrigins.contains(origin.toLowerCase())) {
                    ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                    ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                    ex.getResponseHeaders().set("Vary", "Origin");
                }
            }
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-Token, Idempotency-Key");
        }

        private void sendJsonResponse(HttpExchange ex, int code, String json) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            applySecurityHeaders(ex);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String readRequestBody(HttpExchange ex) throws IOException, PayloadTooLargeException {
            int maxBytes = ServletBridge.resolveMaxBodyBytes();
            try (InputStream is = ex.getRequestBody()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = is.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) {
                        throw new PayloadTooLargeException();
                    }
                    baos.write(buf, 0, n);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private static class PayloadTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception ignored) {}
        } else if (System.getenv("JAVA_PORT") != null) {
            try {
                port = Integer.parseInt(System.getenv("JAVA_PORT").trim());
            } catch (Exception ignored) {}
        }
        try {
            TradingPlatformHttpServer server = new TradingPlatformHttpServer(port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        } catch (Exception e) {
            System.err.println("Fatal error starting TradingPlatformHttpServer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
