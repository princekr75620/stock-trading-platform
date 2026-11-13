package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Map;
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

        // 4. Setup HTTP Server
        this.httpExecutor = Executors.newFixedThreadPool(16);
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
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
        System.out.println("[TradingPlatformHttpServer] Java REST Server running on http://0.0.0.0:" + port);
    }

    public void stop() {
        marketScheduler.shutdownNow();
        server.stop(1);
        httpExecutor.shutdownNow();
        System.out.println("[TradingPlatformHttpServer] Java REST Server stopped.");
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange, 204);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            String body = readRequestBody(exchange);

            try {
                if (path.equals("/api/health") && method.equals("GET")) {
                    Map<String, Object> dbHealth = DBConnection.getHealthStatus();
                    boolean dbConnected = Boolean.TRUE.equals(dbHealth.get("connected"));
                    String engineType = String.valueOf(dbHealth.getOrDefault("engineType", "SQLite"));
                    String json = String.format(
                            "{\"status\":\"%s\",\"databaseConnected\":%b,\"databaseEngine\":\"%s\",\"platform\":\"Online Stock Trading Platform\",\"backend\":\"Java 17 (HttpServer + ServletBridge)\",\"timestamp\":\"%s\"}",
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
                sendJsonResponse(exchange, 500,
                        "{\"success\":false,\"error\":\"Internal Server Error: " + JsonHelper.escape(ex.getMessage()) + "\"}");
            }
        }

        private void sendCors(HttpExchange ex, int code) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-CSRF-Token");
            ex.sendResponseHeaders(code, -1);
        }

        private void sendJsonResponse(HttpExchange ex, int code, String json) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "SAMEORIGIN");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String readRequestBody(HttpExchange ex) throws IOException {
            try (InputStream is = ex.getRequestBody()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
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
