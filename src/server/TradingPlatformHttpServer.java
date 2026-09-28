package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import exception.*;
import model.*;
import service.*;
import servlet.*;
import util.DataPersistenceManager;

import javax.servlet.http.HttpServlet;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Embedded HTTP REST Server built on pure Java SE (com.sun.net.httpserver.HttpServer).
 * Exposes full REST API for the Online Stock Trading Platform, handling trading,
 * authentication, user management, security controls, real-time market updates,
 * alerts, notifications, and analytics reports.
 */
public class TradingPlatformHttpServer {
    private static final int DEFAULT_PORT = 8080;

    private final HttpServer server;
    private final int port;
    private final DataPersistenceManager persistenceManager;

    // Core Business Services
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

    // Background scheduler for live market simulation
    private final ScheduledExecutorService marketScheduler;

    public TradingPlatformHttpServer(int port) throws IOException {
        this.port = port;
        this.persistenceManager = new DataPersistenceManager();

        // 1. Load or initialize services
        List<User> initialUsers = persistenceManager.loadUsers();
        Map<String, User> userMap = new HashMap<>();
        if (initialUsers != null) {
            for (User u : initialUsers) {
                userMap.put(u.getUserId(), u);
            }
        }
        this.userService = new UserService(userMap);

        this.notificationService = new NotificationService();

        List<Stock> initialStocks = persistenceManager.loadStocks();
        Map<String, Stock> stockMap = new HashMap<>();
        if (initialStocks != null) {
            for (Stock s : initialStocks) {
                stockMap.put(s.getSymbol(), s);
            }
        }
        this.marketUpdateService = new MarketUpdateService(stockMap, null, notificationService);

        Map<String, Portfolio> initialPortfolios = persistenceManager.loadPortfolios();
        this.portfolioService = new PortfolioService(initialPortfolios);

        List<Trade> initialTrades = persistenceManager.loadTrades();
        this.tradingService = new TradingService(marketUpdateService, portfolioService, notificationService, userService, initialTrades);

        SecuritySettings secSettings = persistenceManager.loadSecuritySettings();
        this.securityService = new SecurityService(secSettings, null);

        SystemSettings sysSettings = persistenceManager.loadSystemSettings();
        this.systemSettingsService = new SystemSettingsService(sysSettings);

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

        // Setup ServletBridge & Register Servlets (Servlets & Web Integration - 7 marks)
        this.servletBridge = new ServletBridge();
        registerServlets();

        // Ensure persistence synchronization on bootstrap
        saveState();

        // 2. Setup HTTP Server
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(16));

        registerRoutes();

        // 3. Setup background market price ticker simulation (runs every 10 seconds)
        this.marketScheduler = Executors.newSingleThreadScheduledExecutor();
        this.marketScheduler.scheduleAtFixedRate(this::simulateMarketTick, 10, 10, TimeUnit.SECONDS);
    }

    private synchronized void saveState() {
        try {
            persistenceManager.saveAll(userService, marketUpdateService, tradingService,
                    portfolioService, securityService, systemSettingsService);
        } catch (Exception e) {
            System.err.println("[TradingPlatformHttpServer] Error saving state: " + e.getMessage());
        }
    }

    private void simulateMarketTick() {
        try {
            SystemSettings settings = systemSettingsService.getSettings();
            if (settings.isMaintenanceMode() || settings.getMarketStatus() != SystemSettings.MarketStatus.OPEN) {
                return;
            }
            marketUpdateService.simulateMarketTick();
        } catch (Exception e) {
            System.err.println("[MarketTicker] Tick error: " + e.getMessage());
        }
    }

    private void registerRoutes() {
        server.createContext("/api/", new ApiHandler());
    }

    private void registerServlets() {
        servletBridge.registerServlet("/api/auth/login", new LoginServlet());
        servletBridge.registerServlet("/api/auth/register", new RegisterServlet());
        servletBridge.registerServlet("/api/auth/logout", new LogoutServlet());
        servletBridge.registerServlet("/api/dashboard", new DashboardServlet());
        servletBridge.registerServlet("/api/stocks", new StockServlet());
        servletBridge.registerServlet("/api/trades/buy", new BuyStockServlet());
        servletBridge.registerServlet("/api/trades/sell", new SellStockServlet());
        servletBridge.registerServlet("/api/portfolio", new PortfolioServlet());
        servletBridge.registerServlet("/api/trades", new TransactionServlet());
        servletBridge.registerServlet("/api/transactions", new TransactionServlet());
        servletBridge.registerServlet("/api/admin/dashboard", new AdminDashboardServlet());
        servletBridge.registerServlet("/api/admin/stats", new AdminDashboardServlet());
        servletBridge.registerServlet("/api/admin/users", new UserManagementServlet());
        servletBridge.registerServlet("/api/admin/stocks/add", new AddStockServlet());
        servletBridge.registerServlet("/api/admin/stocks/update", new UpdateStockServlet());
        servletBridge.registerServlet("/api/admin/stocks/delete", new DeleteStockServlet());
        System.out.println("[TradingPlatformHttpServer] Successfully registered 16 Java Servlets with AuthenticationFilter.");
    }

    public void start() {
        server.start();
        System.out.println("[TradingPlatformHttpServer] Java REST Server running on http://0.0.0.0:" + port);
    }

    public void stop() {
        marketScheduler.shutdown();
        server.stop(1);
        System.out.println("[TradingPlatformHttpServer] Java REST Server stopped.");
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS preflight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String body = readRequestBody(exchange);

            try {
                handleEndpoint(exchange, path, method, queryParams, body);
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"Internal Server Error: " + JsonHelper.escape(ex.getMessage()) + "\"}");
            }
        }

        private void handleEndpoint(HttpExchange exchange, String path, String method,
                                    Map<String, String> query, String body) throws IOException {

            // Health check
            if (path.equals("/api/health") && method.equals("GET")) {
                sendJsonResponse(exchange, 200,
                        "{\"status\":\"UP\",\"platform\":\"Online Stock Trading Platform\",\"backend\":\"Java 17 (OpenJDK)\",\"timestamp\":\"" + LocalDateTime.now() + "\"}");
                return;
            }

            // Check if a Java EE HttpServlet is registered to process this request
            HttpServlet servlet = servletBridge.getServlet(path);
            if (servlet != null) {
                servletBridge.dispatch(exchange, servlet, body);
                return;
            }

            // Authentication
            if (path.equals("/api/auth/login") && method.equals("POST")) {
                Map<String, String> params = JsonHelper.parseSimpleJson(body);
                String identifier = params.get("usernameOrEmail");
                if (identifier == null) identifier = params.get("email");
                if (identifier == null) identifier = params.get("username");
                String password = params.get("password");

                try {
                    User user = authService.login(identifier, password);
                    String userJson = JsonHelper.userToJson(user);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"user\":" + userJson + "}");
                } catch (InvalidLoginException ile) {
                    sendJsonResponse(exchange, 401, "{\"success\":false,\"error\":\"" + JsonHelper.escape(ile.getMessage()) + "\"}");
                }
                return;
            }

            if (path.equals("/api/auth/logout") && method.equals("POST")) {
                authService.logout();
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Logged out successfully\"}");
                return;
            }

            // Stocks
            if (path.equals("/api/stocks") && method.equals("GET")) {
                List<Stock> stocks = marketUpdateService.getAllStocks();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < stocks.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.stockToJson(stocks.get(i)));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Trades BUY
            if ((path.equals("/api/trades/buy") || path.equals("/api/trade/buy")) && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String traderId = p.get("traderId");
                String symbol = p.get("symbol");
                int qty = parseInt(p.get("quantity"), 0);

                try {
                    Trade trade = tradingService.executeBuy(traderId, symbol, qty);
                    saveState();
                    User u = userService.getUserById(traderId);
                    Trader trader = (u instanceof Trader) ? (Trader) u : null;
                    Portfolio port = portfolioService.getOrCreatePortfolio(traderId);

                    Map<String, Stock> stockMap = marketUpdateService.getStockMap();
                    String portJson = JsonHelper.portfolioToJson(port, trader, stockMap);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"trade\":" + JsonHelper.tradeToJson(trade) +
                            ",\"portfolio\":" + portJson + ",\"message\":\"Successfully bought " + qty + " shares of " + symbol + "\"}");
                } catch (TradingPlatformException tpe) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + JsonHelper.escape(tpe.getMessage()) + "\"}");
                }
                return;
            }

            // Trades SELL
            if ((path.equals("/api/trades/sell") || path.equals("/api/trade/sell")) && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String traderId = p.get("traderId");
                String symbol = p.get("symbol");
                int qty = parseInt(p.get("quantity"), 0);

                try {
                    Trade trade = tradingService.executeSell(traderId, symbol, qty);
                    saveState();
                    User u = userService.getUserById(traderId);
                    Trader trader = (u instanceof Trader) ? (Trader) u : null;
                    Portfolio port = portfolioService.getOrCreatePortfolio(traderId);

                    Map<String, Stock> stockMap = marketUpdateService.getStockMap();
                    String portJson = JsonHelper.portfolioToJson(port, trader, stockMap);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"trade\":" + JsonHelper.tradeToJson(trade) +
                            ",\"portfolio\":" + portJson + ",\"message\":\"Successfully sold " + qty + " shares of " + symbol + "\"}");
                } catch (TradingPlatformException tpe) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + JsonHelper.escape(tpe.getMessage()) + "\"}");
                }
                return;
            }

            // Trades list
            if (path.startsWith("/api/trades") && method.equals("GET")) {
                String traderId = query.get("traderId");
                if ((traderId == null || traderId.isEmpty()) && path.startsWith("/api/trades/") && path.length() > 12) {
                    traderId = path.substring(12);
                }
                List<Trade> trades = (traderId != null && !traderId.isEmpty())
                        ? tradingService.getTradesForTrader(traderId)
                        : tradingService.getAllTrades();

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < trades.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.tradeToJson(trades.get(i)));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Portfolio Overview
            if (path.startsWith("/api/portfolio") && method.equals("GET")) {
                String traderId = query.get("traderId");
                if ((traderId == null || traderId.isEmpty()) && path.startsWith("/api/portfolio/") && path.length() > 15) {
                    traderId = path.substring(15);
                }
                if (traderId == null || traderId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Missing traderId parameter\"}");
                    return;
                }

                Portfolio port = portfolioService.getOrCreatePortfolio(traderId);
                Trader trader = null;
                try {
                    User u = userService.getUserById(traderId);
                    if (u instanceof Trader) trader = (Trader) u;
                } catch (UserNotFoundException ignored) {}

                Map<String, Stock> stockMap = marketUpdateService.getStockMap();
                String json = JsonHelper.portfolioToJson(port, trader, stockMap);
                sendJsonResponse(exchange, 200, json);
                return;
            }

            // Market Updates feed
            if (path.equals("/api/market/updates") && method.equals("GET")) {
                List<MarketUpdate> updates = marketUpdateService.getAllMarketUpdates();
                // Return up to 30 most recent
                int limit = Math.min(30, updates.size());
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < limit; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.marketUpdateToJson(updates.get(i)));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Trigger Market Price Tick manually
            if (path.equals("/api/market/tick") && method.equals("POST")) {
                simulateMarketTick();
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Market prices recalculated\"}");
                return;
            }

            // Notifications
            if (path.startsWith("/api/notifications") && method.equals("GET")) {
                String userId = query.get("userId");
                if ((userId == null || userId.isEmpty()) && path.startsWith("/api/notifications/") && path.length() > 19) {
                    userId = path.substring(19);
                }
                List<Notification> notifs = (userId != null && !userId.isEmpty())
                        ? notificationService.getNotificationsForUser(userId)
                        : new ArrayList<>();

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < notifs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.notificationToJson(notifs.get(i)));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Mark Notification Read
            if (path.equals("/api/notifications/read") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String userId = p.get("userId");
                if (userId != null) {
                    notificationService.markAllAsRead(userId);
                }
                sendJsonResponse(exchange, 200, "{\"success\":true}");
                return;
            }

            // Trader deposit funds
            if (path.equals("/api/trader/deposit") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String traderId = p.get("traderId");
                double amt = parseDouble(p.get("amount"), 0.0);
                if (amt <= 0) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Deposit amount must be positive\"}");
                    return;
                }
                try {
                    User u = userService.getUserById(traderId);
                    if (u instanceof Trader) {
                        Trader t = (Trader) u;
                        t.deposit(amt);
                        saveState();
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"cashBalance\":" + t.getCashBalance() + "}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"User is not a trader\"}");
                    }
                } catch (UserNotFoundException unfe) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"Trader not found\"}");
                }
                return;
            }

            // Trader notification preferences
            if (path.equals("/api/trader/preferences") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String traderId = p.get("traderId");
                boolean emailAlerts = "true".equalsIgnoreCase(p.get("emailAlertsEnabled"));
                double threshold = parseDouble(p.get("alertThresholdPercent"), 2.0);

                try {
                    User u = userService.getUserById(traderId);
                    if (u instanceof Trader) {
                        Trader t = (Trader) u;
                        t.setEmailAlertsEnabled(emailAlerts);
                        t.setAlertThresholdPercent(threshold);
                        saveState();
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Preferences saved\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"User is not a trader\"}");
                    }
                } catch (UserNotFoundException unfe) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"Trader not found\"}");
                }
                return;
            }

            // Admin Dashboard Stats Overview
            if (path.equals("/api/admin/stats") && method.equals("GET")) {
                int totalUsers = userService.getUserCount();
                int totalTraders = userService.getTraderCount();
                int totalAdmins = userService.getAdminCount();
                int totalTrades = tradingService.getTotalTradeCount();
                int buyTrades = tradingService.getBuyTradeCount();
                int sellTrades = tradingService.getSellTradeCount();
                double volume = tradingService.getTotalTradingVolume();
                SystemSettings settings = systemSettingsService.getSettings();
                double feeRevenue = (volume * settings.getTradingFeePercentage()) / 100.0;
                String secScore = securityService.getSecurityHealthScore();

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"totalUsers\":").append(totalUsers).append(",");
                sb.append("\"totalTraders\":").append(totalTraders).append(",");
                sb.append("\"totalAdmins\":").append(totalAdmins).append(",");
                sb.append("\"totalTrades\":").append(totalTrades).append(",");
                sb.append("\"buyTrades\":").append(buyTrades).append(",");
                sb.append("\"sellTrades\":").append(sellTrades).append(",");
                sb.append("\"totalTradingVolume\":").append(String.format(Locale.US, "%.2f", volume)).append(",");
                sb.append("\"feeRevenue\":").append(String.format(Locale.US, "%.2f", feeRevenue)).append(",");
                sb.append("\"systemStatus\":\"").append(settings.isMaintenanceMode() ? "MAINTENANCE" : (settings.getMarketStatus() == SystemSettings.MarketStatus.OPEN ? "OPERATIONAL" : settings.getMarketStatus().name())).append("\",");
                sb.append("\"tradingStatus\":\"").append(settings.getMarketStatus().name()).append("\",");
                sb.append("\"securityStatus\":\"").append(secScore).append("\",");
                sb.append("\"openIncidents\":").append(securityService.getOpenIncidentCount());
                sb.append("}");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Admin User Management - List Users
            if (path.equals("/api/admin/users") && method.equals("GET")) {
                List<User> users = userService.getAllUsers();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.userToJson(users.get(i)));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // Admin User Management - Add User
            if (path.equals("/api/admin/users") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String userId = p.get("userId");
                String name = p.get("name");
                String email = p.get("email");
                String password = p.get("password");
                String roleStr = p.get("role");
                double initialBalance = parseDouble(p.get("initialBalance"), 25000.0);
                String department = p.get("department");
                if (department == null) department = "Risk & Operations";

                if (userId == null || userId.trim().isEmpty()) {
                    userId = "user_" + (System.currentTimeMillis() % 100000);
                }

                User newUser;
                if ("ADMIN".equalsIgnoreCase(roleStr)) {
                    newUser = new Admin(userId, name, email, password, department);
                } else {
                    newUser = new Trader(userId, name, email, password, initialBalance);
                }

                try {
                    userService.createUser(newUser);
                    saveState();
                    sendJsonResponse(exchange, 201, "{\"success\":true,\"user\":" + JsonHelper.userToJson(newUser) + "}");
                } catch (DuplicateUserException | InvalidInputException ex) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + JsonHelper.escape(ex.getMessage()) + "\"}");
                }
                return;
            }

            // Admin User Management - Edit User
            if (path.startsWith("/api/admin/users") && (method.equals("PUT") || (method.equals("POST") && path.endsWith("/update")))) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                String userId = p.get("userId");
                String name = p.get("name");
                String email = p.get("email");
                String password = p.get("password");

                try {
                    userService.updateUserDetails(userId, name, email, password);
                    saveState();
                    User updated = userService.getUserById(userId);
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"user\":" + JsonHelper.userToJson(updated) + "}");
                } catch (UserNotFoundException | InvalidInputException ex) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + JsonHelper.escape(ex.getMessage()) + "\"}");
                }
                return;
            }

            // Admin User Management - Delete User
            if (path.startsWith("/api/admin/users") && (method.equals("DELETE") || (method.equals("POST") && path.endsWith("/delete")))) {
                String userId = query.get("userId");
                if (userId == null) {
                    Map<String, String> p = JsonHelper.parseSimpleJson(body);
                    userId = p.get("userId");
                }
                try {
                    userService.deleteUser(userId);
                    saveState();
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"User deleted successfully\"}");
                } catch (UserNotFoundException unfe) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"" + JsonHelper.escape(unfe.getMessage()) + "\"}");
                }
                return;
            }

            // Admin Financial Security
            if (path.equals("/api/admin/security") && method.equals("GET")) {
                SecuritySettings sec = securityService.getSecuritySettings();
                List<SecurityIncident> incidents = securityService.getAllIncidents();
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"settings\":").append(JsonHelper.securitySettingsToJson(sec)).append(",");
                sb.append("\"healthScore\":\"").append(securityService.getSecurityHealthScore()).append("\",");
                sb.append("\"openIncidentsCount\":").append(securityService.getOpenIncidentCount()).append(",");
                sb.append("\"incidents\":[");
                for (int i = 0; i < incidents.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(JsonHelper.securityIncidentToJson(incidents.get(i)));
                }
                sb.append("]}");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            if (path.equals("/api/admin/security") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                SecuritySettings cur = securityService.getSecuritySettings();
                if (p.containsKey("minimumPasswordLength")) cur.setMinPasswordLength(parseInt(p.get("minimumPasswordLength"), 8));
                if (p.containsKey("requireSpecialCharacters")) cur.setRequireSpecialChar("true".equalsIgnoreCase(p.get("requireSpecialCharacters")));
                if (p.containsKey("passwordExpiryDays")) cur.setPasswordExpiryDays(parseInt(p.get("passwordExpiryDays"), 90));
                if (p.containsKey("maxFailedLoginAttempts")) cur.setMaxFailedLoginAttempts(parseInt(p.get("maxFailedLoginAttempts"), 3));
                if (p.containsKey("sessionTimeoutMinutes")) cur.setSessionTimeoutMinutes(parseInt(p.get("sessionTimeoutMinutes"), 15));
                if (p.containsKey("autoLockoutEnabled")) cur.setAutomaticAccountLockout("true".equalsIgnoreCase(p.get("autoLockoutEnabled")));
                if (p.containsKey("highValueTradeThreshold")) cur.setHighValueTradeThreshold(parseDouble(p.get("highValueTradeThreshold"), 25000.0));
                if (p.containsKey("requireTwoStepForHighValue")) cur.setRequireTwoStepForHighValueTrades("true".equalsIgnoreCase(p.get("requireTwoStepForHighValue")));
                if (p.containsKey("ipAnomalyDetectionEnabled")) cur.setIpAnomalyDetection("true".equalsIgnoreCase(p.get("ipAnomalyDetectionEnabled")));

                securityService.updateSecuritySettings(cur);
                saveState();
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Security settings updated successfully\",\"settings\":" + JsonHelper.securitySettingsToJson(cur) + "}");
                return;
            }

            // Admin System Settings
            if (path.equals("/api/admin/settings") && method.equals("GET")) {
                SystemSettings sys = systemSettingsService.getSettings();
                Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
                sendJsonResponse(exchange, 200, JsonHelper.systemSettingsToJson(sys, metrics));
                return;
            }

            if (path.equals("/api/admin/settings") && method.equals("POST")) {
                Map<String, String> p = JsonHelper.parseSimpleJson(body);
                SystemSettings cur = systemSettingsService.getSettings();
                if (p.containsKey("exchangeName")) cur.setExchangeName(p.get("exchangeName"));
                if (p.containsKey("tradingFeePercent")) cur.setTradingFeePercentage(parseDouble(p.get("tradingFeePercent"), 0.15));
                if (p.containsKey("marketUpdateIntervalSeconds")) cur.setMarketUpdateIntervalSeconds(parseInt(p.get("marketUpdateIntervalSeconds"), 10));
                if (p.containsKey("maintenanceMode")) cur.setMaintenanceMode("true".equalsIgnoreCase(p.get("maintenanceMode")));
                if (p.containsKey("tradingStatus")) {
                    try {
                        cur.setMarketStatus(SystemSettings.MarketStatus.valueOf(p.get("tradingStatus").toUpperCase()));
                    } catch (Exception ignored) {}
                }

                systemSettingsService.updateSettings(cur);
                saveState();
                Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"System settings updated successfully\",\"settings\":" + JsonHelper.systemSettingsToJson(cur, metrics) + "}");
                return;
            }

            // Admin Report Generation
            if (path.equals("/api/admin/reports") && method.equals("GET")) {
                String type = query.get("type");
                if (type == null) type = "user";
                String textReport;
                String reportTitle;

                switch (type.toLowerCase()) {
                    case "trade":
                        textReport = reportService.generateTradeReport();
                        reportTitle = "Executive Trade & Transaction Audit";
                        break;
                    case "portfolio":
                        textReport = reportService.generatePortfolioReport();
                        reportTitle = "Portfolio Valuation & Holdings Report";
                        break;
                    case "financial":
                        textReport = reportService.generateFinancialReport();
                        reportTitle = "Platform Financial & Revenue Report";
                        break;
                    case "system":
                    case "analytics":
                        textReport = reportService.generateSystemAnalyticsReport();
                        reportTitle = "System Health & Analytics Report";
                        break;
                    case "user":
                    default:
                        textReport = reportService.generateUserReport();
                        reportTitle = "User Account & Role Audit Report";
                        break;
                }

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"type\":\"").append(JsonHelper.escape(type)).append("\",");
                sb.append("\"title\":\"").append(JsonHelper.escape(reportTitle)).append("\",");
                sb.append("\"generatedAt\":\"").append(LocalDateTime.now()).append("\",");
                sb.append("\"reportText\":\"").append(JsonHelper.escape(textReport)).append("\"");
                sb.append("}");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            // 404 fallback
            sendJsonResponse(exchange, 404, "{\"error\":\"Endpoint not found: " + JsonHelper.escape(path) + "\"}");
        }

        private void sendCors(HttpExchange ex, int code, String response) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length > 0 ? bytes.length : -1);
            if (bytes.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private void sendJsonResponse(HttpExchange ex, int code, String json) throws IOException {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
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

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isEmpty()) return map;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    try {
                        String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                        String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                        map.put(key, val);
                    } catch (Exception ignored) {}
                }
            }
            return map;
        }

        private int parseInt(String s, int def) {
            if (s == null) return def;
            try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
        }

        private double parseDouble(String s, double def) {
            if (s == null) return def;
            try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
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
        }
    }
}
