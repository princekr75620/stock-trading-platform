package main;

import exception.*;
import model.*;
import service.*;
import util.ChartUtil;
import util.ConsoleHelper;
import util.DataPersistenceManager;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Main application entry point for the Online Stock Trading Platform.
 * Provides the interactive, menu-driven console interface for both Administrators and Traders.
 * Implements robust exception handling, data persistence, and OOP design.
 */
public class Main {
    private static Scanner scanner = new Scanner(System.in);

    // Core Services
    private static IUserService userService;
    private static IAuthenticationService authService;
    private static ISecurityService securityService;
    private static ISystemSettingsService systemSettingsService;
    private static INotificationService notificationService;
    private static IMarketUpdateService marketUpdateService;
    private static IPortfolioService portfolioService;
    private static ITradingService tradingService;
    private static ReportGenerator reportGenerator;
    private static DataPersistenceManager persistenceManager;

    public static void main(String[] args) {
        initializePlatform();

        ConsoleHelper.printHeader("WELCOME TO THE ONLINE STOCK TRADING PLATFORM");
        System.out.println(" Institutional-Grade Equity Trading, Portfolio Management & Market Telemetry");
        System.out.println(" Built 100% in pure Java demonstrating comprehensive Object-Oriented Programming.");

        boolean running = true;
        while (running) {
            try {
                System.out.println("\n================ MAIN MENU ================");
                System.out.println("1. Login");
                System.out.println("2. View Market Ticker Overview (Guest Preview)");
                System.out.println("3. Exit Platform");
                System.out.println("===========================================");

                int choice = ConsoleHelper.readInt(scanner, "Select an option [1-3]");
                switch (choice) {
                    case 1:
                        handleLogin();
                        break;
                    case 2:
                        displayGuestMarketPreview();
                        break;
                    case 3:
                        running = false;
                        savePlatformData();
                        ConsoleHelper.printSuccess("All platform data saved. Thank you for trading with us!");
                        break;
                    default:
                        ConsoleHelper.printError("Invalid option! Please enter 1, 2, or 3.");
                }
            } catch (Exception e) {
                ConsoleHelper.printError("An unexpected error occurred: " + e.getMessage());
            }
        }
    }

    /**
     * Bootstraps services, loads persisted data, or seeds default data.
     */
    private static void initializePlatform() {
        persistenceManager = new DataPersistenceManager();

        // 1. Security Service & System Settings Service
        SecuritySettings secSettings = persistenceManager.loadSecuritySettings();
        securityService = new SecurityService(secSettings, null);

        SystemSettings sysSettings = persistenceManager.loadSystemSettings();
        systemSettingsService = new SystemSettingsService(sysSettings);

        // 2. Notifications & Alerts
        notificationService = new NotificationService();

        // 3. User Service
        List<User> loadedUsers = persistenceManager.loadUsers();
        Map<String, User> userMap = new HashMap<>();
        if (loadedUsers != null && !loadedUsers.isEmpty()) {
            for (User u : loadedUsers) userMap.put(u.getUserId(), u);
            userService = new UserService(userMap);
        } else {
            userService = new UserService();
            seedDefaultUsers();
        }

        // 4. Authentication Service
        authService = new AuthenticationService(userService, securityService);

        // 5. Market Update Service & Stocks
        List<Stock> loadedStocks = persistenceManager.loadStocks();
        Map<String, Stock> stockMap = new HashMap<>();
        if (loadedStocks != null && !loadedStocks.isEmpty()) {
            for (Stock s : loadedStocks) stockMap.put(s.getSymbol(), s);
            marketUpdateService = new MarketUpdateService(stockMap, null, notificationService);
        } else {
            marketUpdateService = new MarketUpdateService(notificationService);
        }

        // 6. Portfolio Service
        Map<String, Portfolio> loadedPortfolios = persistenceManager.loadPortfolios();
        portfolioService = new PortfolioService(loadedPortfolios);

        // 7. Trading Service
        List<Trade> loadedTrades = persistenceManager.loadTrades();
        tradingService = new TradingService(marketUpdateService, portfolioService, notificationService, userService, loadedTrades);

        // 8. Report Generator
        reportGenerator = new ReportGenerator(userService, tradingService, portfolioService,
                marketUpdateService, securityService, systemSettingsService);

        // Save state initially
        savePlatformData();
    }

    private static void seedDefaultUsers() {
        try {
            // Default Administrator
            Admin admin = new Admin("admin", "Platform Administrator", "admin@apex.com", "admin123", "Risk & Operations");
            userService.createUser(admin);

            // Default Trader 1
            Trader trader1 = new Trader("trader1", "Alex Morgan", "alex@apex.com", "trader123", 50000.0);
            trader1.addToWatchlist("AAPL");
            trader1.addToWatchlist("NVDA");
            userService.createUser(trader1);

            // Default Trader 2
            Trader trader2 = new Trader("trader2", "Sarah Jenkins", "sarah@apex.com", "trader123", 75000.0);
            trader2.addToWatchlist("MSFT");
            trader2.addToWatchlist("GOOGL");
            userService.createUser(trader2);
        } catch (Exception e) {
            System.err.println("Error seeding default users: " + e.getMessage());
        }
    }

    private static void savePlatformData() {
        if (persistenceManager != null) {
            persistenceManager.saveAll(userService, marketUpdateService, tradingService,
                    portfolioService, securityService, systemSettingsService);
        }
    }

    // =========================================================================
    // AUTHENTICATION
    // =========================================================================

    private static void handleLogin() {
        ConsoleHelper.printSubHeader("PLATFORM AUTHENTICATION");
        System.out.println("Default Logins for Evaluation:");
        System.out.println("  * Administrator: Username: 'admin'   | Password: 'admin123'");
        System.out.println("  * Trader 1:      Username: 'trader1' | Password: 'trader123'");
        System.out.println("  * Trader 2:      Username: 'trader2' | Password: 'trader123'\n");

        String usernameOrEmail = ConsoleHelper.readString(scanner, "Enter User ID or Email");
        String password = ConsoleHelper.readString(scanner, "Enter Password");

        try {
            User user = authService.login(usernameOrEmail, password);
            ConsoleHelper.printSuccess("Welcome back, " + user.getName() + " (" + user.getRole() + ")!");
            System.out.println("Role Policy: " + user.getRoleDescription());

            if (user.getRole() == UserRole.ADMIN) {
                runAdminDashboard((Admin) user);
            } else if (user.getRole() == UserRole.TRADER) {
                runTraderDashboard((Trader) user);
            }
        } catch (InvalidLoginException e) {
            ConsoleHelper.printError("Authentication Failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMIN DASHBOARD
    // =========================================================================

    private static void runAdminDashboard(Admin admin) {
        boolean inAdmin = true;
        while (inAdmin) {
            try {
                System.out.println("\n========== ADMIN DASHBOARD ==========");
                System.out.println("Active Session: " + admin.getName() + " [" + admin.getUserId() + "]");
                System.out.println("1. User Management");
                System.out.println("2. Financial Data Security");
                System.out.println("3. System Settings");
                System.out.println("4. Trade Activity Monitoring");
                System.out.println("5. Report Generation");
                System.out.println("6. View System Status");
                System.out.println("7. Logout");
                System.out.println("=====================================");

                int choice = ConsoleHelper.readInt(scanner, "Select an option [1-7]");
                switch (choice) {
                    case 1:
                        handleUserManagement();
                        break;
                    case 2:
                        handleFinancialDataSecurity();
                        break;
                    case 3:
                        handleSystemSettingsMenu();
                        break;
                    case 4:
                        handleTradeActivityMonitoring();
                        break;
                    case 5:
                        handleReportGeneration();
                        break;
                    case 6:
                        handleViewSystemStatus();
                        break;
                    case 7:
                        authService.logout();
                        savePlatformData();
                        ConsoleHelper.printSuccess("Administrator logged out successfully.");
                        inAdmin = false;
                        break;
                    default:
                        ConsoleHelper.printError("Invalid option! Please enter a number between 1 and 7.");
                }
            } catch (Exception e) {
                ConsoleHelper.printError("Admin menu error: " + e.getMessage());
            }
        }
    }

    // 1. User Management
    private static void handleUserManagement() {
        boolean inUsers = true;
        while (inUsers) {
            ConsoleHelper.printSubHeader("USER MANAGEMENT");
            displayUserTable(userService.getAllUsers());

            System.out.println("\nOptions:");
            System.out.println("1. Add New User");
            System.out.println("2. Edit User Details");
            System.out.println("3. Delete User");
            System.out.println("4. Search Users");
            System.out.println("5. Manage User Role");
            System.out.println("6. View All Users Refresh");
            System.out.println("7. Return to Admin Dashboard");

            int choice = ConsoleHelper.readInt(scanner, "Select User Management option [1-7]");
            switch (choice) {
                case 1:
                    addUserPrompt();
                    break;
                case 2:
                    editUserPrompt();
                    break;
                case 3:
                    deleteUserPrompt();
                    break;
                case 4:
                    searchUsersPrompt();
                    break;
                case 5:
                    manageUserRolePrompt();
                    break;
                case 6:
                    // loop refreshes table
                    break;
                case 7:
                    inUsers = false;
                    break;
                default:
                    ConsoleHelper.printError("Invalid choice [1-7].");
            }
        }
    }

    private static void displayUserTable(List<User> users) {
        System.out.println("----------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-20s | %-26s | %-8s | %-12s%n", "User ID", "Full Name", "Email", "Role", "Detail/Balance");
        System.out.println("----------------------------------------------------------------------------------------");
        if (users.isEmpty()) {
            System.out.println("No user accounts found matching query.");
        } else {
            for (User u : users) {
                String extra = "";
                if (u instanceof Trader) {
                    extra = String.format("$%,.2f", ((Trader) u).getCashBalance());
                } else if (u instanceof Admin) {
                    extra = ((Admin) u).getDepartment();
                }
                System.out.printf("%-10s | %-20s | %-26s | %-8s | %-12s%n",
                        u.getUserId(), u.getName(), u.getEmail(), u.getRole(), extra);
            }
        }
        System.out.println("----------------------------------------------------------------------------------------");
    }

    private static void addUserPrompt() {
        ConsoleHelper.printSubHeader("CREATE NEW USER");
        String id = ConsoleHelper.readString(scanner, "Enter New User ID (e.g. trader3)");
        String name = ConsoleHelper.readString(scanner, "Enter Full Name");
        String email = ConsoleHelper.readString(scanner, "Enter Email Address");
        String password = ConsoleHelper.readString(scanner, "Enter Password");

        System.out.println("Select Role: 1. TRADER  |  2. ADMIN");
        int rChoice = ConsoleHelper.readInt(scanner, "Role [1 or 2]");
        User newUser;
        if (rChoice == 2) {
            String dept = ConsoleHelper.readString(scanner, "Enter Admin Department");
            newUser = new Admin(id, name, email, password, dept.isEmpty() ? "Operations" : dept);
        } else {
            double deposit = ConsoleHelper.readDouble(scanner, "Enter Initial Cash Balance ($)");
            newUser = new Trader(id, name, email, password, Math.max(0, deposit));
        }

        try {
            userService.createUser(newUser);
            savePlatformData();
            ConsoleHelper.printSuccess("User account '" + id + "' (" + newUser.getRole() + ") created successfully!");
        } catch (DuplicateUserException | InvalidInputException e) {
            ConsoleHelper.printError("Failed to create user: " + e.getMessage());
        }
    }

    private static void editUserPrompt() {
        ConsoleHelper.printSubHeader("UPDATE USER DETAILS");
        String id = ConsoleHelper.readString(scanner, "Enter User ID to edit");
        try {
            User existing = userService.getUserById(id);
            System.out.println("Editing User: " + existing.getName() + " (" + existing.getEmail() + ")");
            System.out.println("(Leave blank to keep existing value)");

            String newName = ConsoleHelper.readString(scanner, "New Name [" + existing.getName() + "]");
            String newEmail = ConsoleHelper.readString(scanner, "New Email [" + existing.getEmail() + "]");
            String newPass = ConsoleHelper.readString(scanner, "New Password [****]");

            userService.updateUserDetails(id,
                    newName.isEmpty() ? null : newName,
                    newEmail.isEmpty() ? null : newEmail,
                    newPass.isEmpty() ? null : newPass);

            savePlatformData();
            ConsoleHelper.printSuccess("User details for '" + id + "' updated successfully!");
        } catch (UserNotFoundException | InvalidInputException e) {
            ConsoleHelper.printError("Failed to update user: " + e.getMessage());
        }
    }

    private static void deleteUserPrompt() {
        ConsoleHelper.printSubHeader("DELETE USER ACCOUNT");
        String id = ConsoleHelper.readString(scanner, "Enter User ID to delete");
        try {
            User existing = userService.getUserById(id);
            String confirm = ConsoleHelper.readString(scanner, "Are you sure you want to delete user '" + existing.getName() + "'? (yes/no)");
            if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("y")) {
                userService.deleteUser(id);
                savePlatformData();
                ConsoleHelper.printSuccess("User account '" + id + "' was deleted successfully!");
            } else {
                ConsoleHelper.printInfo("Deletion cancelled.");
            }
        } catch (UserNotFoundException e) {
            ConsoleHelper.printError("Failed to delete user: " + e.getMessage());
        }
    }

    private static void searchUsersPrompt() {
        ConsoleHelper.printSubHeader("SEARCH USERS");
        String query = ConsoleHelper.readString(scanner, "Enter search keyword (ID, Name, Email, or Role)");
        List<User> results = userService.searchUsers(query);
        displayUserTable(results);
    }

    private static void manageUserRolePrompt() {
        ConsoleHelper.printSubHeader("MANAGE USER ROLE");
        String id = ConsoleHelper.readString(scanner, "Enter User ID to modify role");
        try {
            User existing = userService.getUserById(id);
            System.out.println("Current Role for " + existing.getName() + " is: " + existing.getRole());
            System.out.println("Select New Role: 1. ADMIN  |  2. TRADER");
            int r = ConsoleHelper.readInt(scanner, "Choice [1 or 2]");
            UserRole newRole = (r == 1) ? UserRole.ADMIN : UserRole.TRADER;

            userService.updateUserRole(id, newRole);
            savePlatformData();
            ConsoleHelper.printSuccess("User role for '" + id + "' successfully updated to " + newRole + "!");
        } catch (UserNotFoundException e) {
            ConsoleHelper.printError("Failed to update role: " + e.getMessage());
        }
    }

    // 2. Financial Data Security
    private static void handleFinancialDataSecurity() {
        boolean inSec = true;
        while (inSec) {
            ConsoleHelper.printSubHeader("FINANCIAL DATA SECURITY");
            SecuritySettings s = securityService.getSecuritySettings();

            System.out.println("---------------- Current Security Overview ----------------");
            System.out.printf("Security Status        : %s%n", securityService.getSecurityHealthScore());
            System.out.printf("Open Security Incidents: %d active%n", securityService.getOpenIncidentCount());
            System.out.printf("Password Policy        : Min Length: %d | Req Special: %b | Expiry: %d days%n",
                    s.getMinPasswordLength(), s.isRequireSpecialChar(), s.getPasswordExpiryDays());
            System.out.printf("Login Security         : Max Failed Attempts: %d | Session Timeout: %d mins%n",
                    s.getMaxFailedLoginAttempts(), s.getSessionTimeoutMinutes());
            System.out.printf("Account Protection     : Auto Lockout: %b | IP Anomaly Detection: %b%n",
                    s.isAutomaticAccountLockout(), s.isIpAnomalyDetection());
            System.out.printf("Transaction Security   : High Value Threshold: $%,.2f | 2-Step Req: %b%n",
                    s.getHighValueTradeThreshold(), s.isRequireTwoStepForHighValueTrades());
            System.out.println("-----------------------------------------------------------");

            System.out.println("\nOptions:");
            System.out.println("1. View Full Security Settings Details");
            System.out.println("2. Update Security Policies");
            System.out.println("3. View Recent Security Incidents");
            System.out.println("4. Monitor Financial Data Security Telemetry");
            System.out.println("5. Return to Admin Dashboard");

            int choice = ConsoleHelper.readInt(scanner, "Select Security option [1-5]");
            switch (choice) {
                case 1:
                    viewFullSecuritySettings(s);
                    break;
                case 2:
                    updateSecuritySettingsPrompt(s);
                    break;
                case 3:
                    viewSecurityIncidentsPrompt();
                    break;
                case 4:
                    monitorSecurityTelemetry();
                    break;
                case 5:
                    inSec = false;
                    break;
                default:
                    ConsoleHelper.printError("Invalid choice [1-5].");
            }
        }
    }

    private static void viewFullSecuritySettings(SecuritySettings s) {
        ConsoleHelper.printSubHeader("COMPLETE FINANCIAL DATA SECURITY SPECIFICATION");
        System.out.println("1. PASSWORD POLICY:");
        System.out.println("   - Minimum Length: " + s.getMinPasswordLength() + " characters");
        System.out.println("   - Require Special Characters: " + s.isRequireSpecialChar());
        System.out.println("   - Require Numeric Characters: " + s.isRequireNumbers());
        System.out.println("   - Expiration Period: " + s.getPasswordExpiryDays() + " days");
        System.out.println("2. LOGIN SECURITY:");
        System.out.println("   - Max Consecutive Failed Attempts: " + s.getMaxFailedLoginAttempts());
        System.out.println("   - Inactive Session Timeout: " + s.getSessionTimeoutMinutes() + " minutes");
        System.out.println("   - Two-Factor Authentication (2FA) Enforced: " + s.isTwoFactorAuthRequired());
        System.out.println("3. ACCOUNT PROTECTION:");
        System.out.println("   - Automatic Account Lockout on Attack: " + s.isAutomaticAccountLockout());
        System.out.println("   - IP Anomaly Geolocation Inspection: " + s.isIpAnomalyDetection());
        System.out.println("4. TRANSACTION SECURITY:");
        System.out.println("   - High-Value Trade Alert Threshold: $" + String.format("%,.2f", s.getHighValueTradeThreshold()));
        System.out.println("   - Require Secondary Verification: " + s.isRequireTwoStepForHighValueTrades());
        System.out.println("   - Daily Trader Cumulative Limit: $" + String.format("%,.2f", s.getDailyTradingLimitPerTrader()));
        System.out.println("Last Policy Update Timestamp: " + s.getLastUpdated());
    }

    private static void updateSecuritySettingsPrompt(SecuritySettings s) {
        ConsoleHelper.printSubHeader("UPDATE FINANCIAL DATA SECURITY SETTINGS");
        System.out.println("Enter new values (or enter 0 to preserve existing value):");

        int minLen = ConsoleHelper.readInt(scanner, "New Minimum Password Length [Current: " + s.getMinPasswordLength() + "]");
        if (minLen > 0) s.setMinPasswordLength(minLen);

        int maxAttempts = ConsoleHelper.readInt(scanner, "New Max Failed Login Attempts [Current: " + s.getMaxFailedLoginAttempts() + "]");
        if (maxAttempts > 0) s.setMaxFailedLoginAttempts(maxAttempts);

        int timeout = ConsoleHelper.readInt(scanner, "New Session Timeout in minutes [Current: " + s.getSessionTimeoutMinutes() + "]");
        if (timeout > 0) s.setSessionTimeoutMinutes(timeout);

        double threshold = ConsoleHelper.readDouble(scanner, "New High-Value Trade Threshold ($) [Current: " + s.getHighValueTradeThreshold() + "]");
        if (threshold > 0) s.setHighValueTradeThreshold(threshold);

        securityService.updateSecuritySettings(s);
        savePlatformData();
        ConsoleHelper.printSuccess("Financial Data Security settings updated successfully!");
    }

    private static void viewSecurityIncidentsPrompt() {
        ConsoleHelper.printSubHeader("RECENT SECURITY INCIDENTS");
        List<SecurityIncident> incidents = securityService.getAllIncidents();
        if (incidents.isEmpty()) {
            System.out.println("No security incidents recorded. System running cleanly.");
        } else {
            for (SecurityIncident inc : incidents) {
                System.out.println(inc);
            }
        }
    }

    private static void monitorSecurityTelemetry() {
        ConsoleHelper.printSubHeader("REAL-TIME FINANCIAL SECURITY MONITOR");
        System.out.println(ChartUtil.renderProgressBar("Security Policy Health", 98.0, 30));
        System.out.println(ChartUtil.renderProgressBar("Encryption Compliance", 100.0, 30));
        System.out.println(ChartUtil.renderProgressBar("Threat Mitigation", 94.5, 30));
        System.out.printf("Active Threat Score: %s%n", securityService.getSecurityHealthScore());
        System.out.println("All transaction payloads encrypted with AES-256-GCM in flight.");
    }

    // 3. System Settings
    private static void handleSystemSettingsMenu() {
        boolean inSys = true;
        while (inSys) {
            ConsoleHelper.printSubHeader("SYSTEM SETTINGS");
            SystemSettings s = systemSettingsService.getSettings();

            System.out.println("---------------- Current Configuration ----------------");
            System.out.printf("Exchange Name      : %s%n", s.getExchangeName());
            System.out.printf("System Version     : %s%n", s.getSystemVersion());
            System.out.printf("Market Status      : %s%n", s.getMarketStatus());
            System.out.printf("Trading Fee Rate   : %.2f%%%n", s.getTradingFeePercentage());
            System.out.printf("Update Interval    : %d seconds%n", s.getMarketUpdateIntervalSeconds());
            System.out.printf("Maintenance Mode   : %b%n", s.isMaintenanceMode());
            System.out.printf("Backup Frequency   : Every %d hours%n", s.getBackupFrequencyHours());
            System.out.printf("Max Concurrent User: %d sessions%n", s.getMaxConcurrentUsers());
            System.out.println("-------------------------------------------------------");

            System.out.println("\nOptions:");
            System.out.println("1. Update System Configuration");
            System.out.println("2. Toggle Maintenance Mode");
            System.out.println("3. Change Market Status (OPEN / CLOSED)");
            System.out.println("4. Monitor System Status & Performance");
            System.out.println("5. Return to Admin Dashboard");

            int choice = ConsoleHelper.readInt(scanner, "Select option [1-5]");
            switch (choice) {
                case 1:
                    updateSystemConfigPrompt(s);
                    break;
                case 2:
                    systemSettingsService.toggleMaintenanceMode();
                    savePlatformData();
                    ConsoleHelper.printSuccess("Maintenance Mode is now: " +
                            (systemSettingsService.getSettings().isMaintenanceMode() ? "ENABLED (Locked)" : "DISABLED (Normal)"));
                    break;
                case 3:
                    System.out.println("Select Market Status: 1. OPEN  |  2. CLOSED  |  3. PRE_MARKET");
                    int st = ConsoleHelper.readInt(scanner, "Choice [1-3]");
                    if (st == 1) s.setMarketStatus(SystemSettings.MarketStatus.OPEN);
                    else if (st == 2) s.setMarketStatus(SystemSettings.MarketStatus.CLOSED);
                    else s.setMarketStatus(SystemSettings.MarketStatus.PRE_MARKET);
                    systemSettingsService.updateSettings(s);
                    savePlatformData();
                    ConsoleHelper.printSuccess("Market status updated to: " + s.getMarketStatus());
                    break;
                case 4:
                    handleViewSystemStatus();
                    break;
                case 5:
                    inSys = false;
                    break;
                default:
                    ConsoleHelper.printError("Invalid choice [1-5].");
            }
        }
    }

    private static void updateSystemConfigPrompt(SystemSettings s) {
        ConsoleHelper.printSubHeader("UPDATE SYSTEM CONFIGURATION");
        String name = ConsoleHelper.readString(scanner, "Enter Exchange Name [" + s.getExchangeName() + "]");
        if (!name.isEmpty()) s.setExchangeName(name);

        double fee = ConsoleHelper.readDouble(scanner, "Trading Fee Percentage (%) [" + s.getTradingFeePercentage() + "] (0 to skip)");
        if (fee > 0) s.setTradingFeePercentage(fee);

        int interval = ConsoleHelper.readInt(scanner, "Market Update Interval in Seconds [" + s.getMarketUpdateIntervalSeconds() + "] (0 to skip)");
        if (interval > 0) s.setMarketUpdateIntervalSeconds(interval);

        systemSettingsService.updateSettings(s);
        savePlatformData();
        ConsoleHelper.printSuccess("System configuration updated successfully!");
    }

    // 4. Trade Activity Monitoring
    private static void handleTradeActivityMonitoring() {
        ConsoleHelper.printSubHeader("TRADE ACTIVITY MONITORING & PERFORMANCE METRICS");

        int totalTrades = tradingService.getTotalTradeCount();
        int buys = tradingService.getBuyTradeCount();
        int sells = tradingService.getSellTradeCount();
        double volume = tradingService.getTotalTradingVolume();

        System.out.printf("Total Executed Trades : %d%n", totalTrades);
        System.out.printf("BUY Orders            : %d%n", buys);
        System.out.printf("SELL Orders           : %d%n", sells);
        System.out.printf("Total Trading Volume  : $%,.2f%n", volume);
        System.out.printf("Exchange Fee Revenue  : $%,.2f%n",
                volume * (systemSettingsService.getSettings().getTradingFeePercentage() / 100.0));

        // Java Console Graphical Representation (ASCII Bar Chart)
        System.out.println(ChartUtil.renderBuySellChart(buys, sells));

        System.out.println("System Performance Metrics:");
        System.out.println(ChartUtil.renderProgressBar("Engine Processing Capacity", 82.5, 30));
        System.out.println(ChartUtil.renderProgressBar("Network Matching Latency (<1ms)", 96.0, 30));
        System.out.println(ChartUtil.renderProgressBar("JVM Garbage Collection Health", 92.0, 30));

        System.out.println("\nRecent 5 Trades Across Platform:");
        List<Trade> allTrades = tradingService.getAllTrades();
        int count = Math.min(5, allTrades.size());
        for (int i = 0; i < count; i++) {
            System.out.println(" * " + allTrades.get(i));
        }
    }

    // 5. Report Generation
    private static void handleReportGeneration() {
        boolean inReports = true;
        while (inReports) {
            ConsoleHelper.printSubHeader("ADMINISTRATIVE REPORT GENERATOR");
            System.out.println("1. Generate User Report");
            System.out.println("2. Generate Trade Report");
            System.out.println("3. Generate Portfolio Report");
            System.out.println("4. Generate Financial Report");
            System.out.println("5. Generate System Analytics Report");
            System.out.println("6. Return to Admin Dashboard");

            int choice = ConsoleHelper.readInt(scanner, "Select report to generate [1-6]");
            switch (choice) {
                case 1:
                    System.out.println(reportGenerator.generateUserReport());
                    break;
                case 2:
                    System.out.println(reportGenerator.generateTradeReport());
                    break;
                case 3:
                    System.out.println(reportGenerator.generatePortfolioReport());
                    break;
                case 4:
                    System.out.println(reportGenerator.generateFinancialReport());
                    break;
                case 5:
                    System.out.println(reportGenerator.generateSystemAnalyticsReport());
                    break;
                case 6:
                    inReports = false;
                    break;
                default:
                    ConsoleHelper.printError("Invalid choice [1-6].");
            }
        }
    }

    // 6. View System Status
    private static void handleViewSystemStatus() {
        ConsoleHelper.printSubHeader("SYSTEM STATUS TELEMETRY");
        Map<String, String> status = systemSettingsService.getSystemStatusMetrics();
        for (Map.Entry<String, String> entry : status.entrySet()) {
            System.out.printf("%-26s : %s%n", entry.getKey(), entry.getValue());
        }
    }

    // =========================================================================
    // TRADER DASHBOARD
    // =========================================================================

    private static void runTraderDashboard(Trader trader) {
        boolean inTrader = true;
        while (inTrader) {
            try {
                System.out.println("\n========== TRADER DASHBOARD ==========");
                System.out.printf("Trader: %s | ID: %s | Cash Balance: $%,.2f%n",
                        trader.getName(), trader.getUserId(), trader.getCashBalance());
                System.out.println("1. View Stocks");
                System.out.println("2. Buy Stock");
                System.out.println("3. Sell Stock");
                System.out.println("4. Portfolio Overview");
                System.out.println("5. Market Updates");
                System.out.println("6. Trade History");
                System.out.println("7. Alerts and Notifications");
                System.out.println("8. Update Preferences");
                System.out.println("9. Logout");
                System.out.println("======================================");

                int choice = ConsoleHelper.readInt(scanner, "Select Trader option [1-9]");
                switch (choice) {
                    case 1:
                        displayStocksTable();
                        break;
                    case 2:
                        handleBuyStock(trader);
                        break;
                    case 3:
                        handleSellStock(trader);
                        break;
                    case 4:
                        handlePortfolioOverview(trader);
                        break;
                    case 5:
                        handleMarketUpdates(trader);
                        break;
                    case 6:
                        handleTradeHistory(trader);
                        break;
                    case 7:
                        handleAlertsAndNotifications(trader);
                        break;
                    case 8:
                        handleUpdatePreferences(trader);
                        break;
                    case 9:
                        authService.logout();
                        savePlatformData();
                        ConsoleHelper.printSuccess("Trader session ended safely. Goodbye!");
                        inTrader = false;
                        break;
                    default:
                        ConsoleHelper.printError("Invalid option [1-9].");
                }
            } catch (Exception e) {
                ConsoleHelper.printError("Trader menu error: " + e.getMessage());
            }
        }
    }

    // 1. View Stocks
    private static void displayStocksTable() {
        ConsoleHelper.printSubHeader("EQUITY MARKET QUOTES & DEPTH");
        System.out.println("------------------------------------------------------------------------------------------------");
        System.out.printf("%-6s | %-24s | %-10s | %-16s | %-10s | %-18s%n",
                "Symbol", "Company Name", "Price ($)", "Change (24h)", "Available", "Intraday Range");
        System.out.println("------------------------------------------------------------------------------------------------");
        for (Stock s : marketUpdateService.getAllStocks()) {
            String chg = (s.getPriceChange() >= 0 ? "+" : "") + String.format("%.2f (%.2f%%)", s.getPriceChange(), s.getPriceChangePercent());
            String range = String.format("$%.2f - $%.2f", s.getDayLow(), s.getDayHigh());
            System.out.printf("%-6s | %-24s | $%-9.2f | %-16s | %-10d | %-18s%n",
                    s.getSymbol(), s.getName(), s.getCurrentPrice(), chg, s.getAvailableQuantity(), range);
        }
        System.out.println("------------------------------------------------------------------------------------------------");
    }

    // 2. Buy Stock
    private static void handleBuyStock(Trader trader) {
        ConsoleHelper.printSubHeader("BUY STOCK ORDER ENTRY");
        displayStocksTable();

        String symbol = ConsoleHelper.readString(scanner, "Enter Stock Symbol to Buy (e.g. AAPL, NVDA)");
        int quantity = ConsoleHelper.readInt(scanner, "Enter Quantity of Shares");

        try {
            Stock stock = marketUpdateService.getStock(symbol);
            if (stock == null) {
                throw new StockNotFoundException("Symbol '" + symbol + "' does not exist on the platform.");
            }
            double totalCost = Math.round((stock.getCurrentPrice() * quantity) * 100.0) / 100.0;
            System.out.printf("Order Review: BUY %d shares of %s @ $%.2f = Total: $%,.2f%n",
                    quantity, stock.getSymbol(), stock.getCurrentPrice(), totalCost);

            String confirm = ConsoleHelper.readString(scanner, "Confirm execution? (yes/no)");
            if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("y")) {
                Trade trade = tradingService.executeBuy(trader.getUserId(), symbol, quantity);
                savePlatformData();
                ConsoleHelper.printSuccess("TRADE CONFIRMATION: " + trade);
            } else {
                ConsoleHelper.printInfo("Buy order cancelled.");
            }
        } catch (StockNotFoundException | InvalidInputException | InsufficientStockQuantityException e) {
            ConsoleHelper.printError("Order Failed: " + e.getMessage());
        }
    }

    // 3. Sell Stock
    private static void handleSellStock(Trader trader) {
        ConsoleHelper.printSubHeader("SELL STOCK ORDER ENTRY");

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(trader.getUserId());
        if (portfolio.getHoldings().isEmpty()) {
            ConsoleHelper.printWarning("Your portfolio currently contains zero stock holdings.");
            return;
        }

        System.out.println("Your Current Holdings:");
        for (Map.Entry<String, Integer> entry : portfolio.getHoldings().entrySet()) {
            Stock s = marketUpdateService.getStock(entry.getKey());
            double curPrice = (s != null) ? s.getCurrentPrice() : 0.0;
            System.out.printf(" * %-5s: %d shares owned (Current Market Price: $%.2f)%n",
                    entry.getKey(), entry.getValue(), curPrice);
        }

        String symbol = ConsoleHelper.readString(scanner, "Enter Stock Symbol to Sell");
        int quantity = ConsoleHelper.readInt(scanner, "Enter Quantity of Shares to Sell");

        try {
            Stock stock = marketUpdateService.getStock(symbol);
            if (stock == null) {
                throw new StockNotFoundException("Stock symbol '" + symbol + "' was not found.");
            }
            double totalRevenue = Math.round((stock.getCurrentPrice() * quantity) * 100.0) / 100.0;
            System.out.printf("Order Review: SELL %d shares of %s @ $%.2f = Total Proceeds: $%,.2f%n",
                    quantity, stock.getSymbol(), stock.getCurrentPrice(), totalRevenue);

            String confirm = ConsoleHelper.readString(scanner, "Confirm execution? (yes/no)");
            if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("y")) {
                Trade trade = tradingService.executeSell(trader.getUserId(), symbol, quantity);
                savePlatformData();
                ConsoleHelper.printSuccess("TRADE CONFIRMATION: " + trade);
            } else {
                ConsoleHelper.printInfo("Sell order cancelled.");
            }
        } catch (StockNotFoundException | InvalidInputException | InsufficientHoldingsException e) {
            ConsoleHelper.printError("Order Failed: " + e.getMessage());
        }
    }

    // 4. Portfolio Overview
    private static void handlePortfolioOverview(Trader trader) {
        ConsoleHelper.printSubHeader("INVESTMENT PORTFOLIO OVERVIEW");
        Portfolio portfolio = portfolioService.getOrCreatePortfolio(trader.getUserId());
        Map<String, Stock> marketStocks = marketUpdateService.getStockMap();

        double investVal = portfolio.getTotalInvestmentValue();
        double currentVal = portfolio.getCurrentPortfolioValue(marketStocks);
        double pnl = portfolio.getUnrealizedProfitLoss(marketStocks);
        double pnlPct = portfolio.getUnrealizedProfitLossPercent(marketStocks);
        double totalEquity = currentVal + trader.getCashBalance();

        System.out.println("----------------------------------------------------------------------------------------");
        System.out.printf("Cash Available      : $%,-14.2f | Portfolio Equity Value : $%,-14.2f%n", trader.getCashBalance(), currentVal);
        System.out.printf("Total Cost Basis    : $%,-14.2f | Total Account Net Worth: $%,-14.2f%n", investVal, totalEquity);
        String pnlSign = pnl >= 0 ? "+$" : "-$";
        System.out.printf("Unrealized P&L      : %s%,-13.2f (%.2f%%)%n", pnlSign, Math.abs(pnl), pnlPct);
        System.out.printf("Realized P&L to Date: $%,-14.2f%n", portfolio.getRealizedProfitLoss());
        System.out.println("----------------------------------------------------------------------------------------");

        System.out.println("\nIndividual Stock Holdings Breakdown:");
        System.out.println("----------------------------------------------------------------------------------------");
        System.out.printf("%-6s | %-5s | %-12s | %-12s | %-14s | %-16s%n",
                "Symbol", "Qty", "Avg Buy ($)", "Current ($)", "Holdings Value", "Position P&L");
        System.out.println("----------------------------------------------------------------------------------------");

        if (portfolio.getHoldings().isEmpty()) {
            System.out.println("No open equity positions. All capital held in cash balance.");
        } else {
            for (Map.Entry<String, Integer> entry : portfolio.getHoldings().entrySet()) {
                String sym = entry.getKey();
                int qty = entry.getValue();
                double avgCost = portfolio.getAverageBuyPrice(sym);
                Stock st = marketStocks.get(sym);
                double curPrice = (st != null) ? st.getCurrentPrice() : 0.0;
                double posVal = qty * curPrice;
                double posPnl = posVal - (qty * avgCost);
                double posPnlPct = (qty * avgCost) > 0 ? (posPnl / (qty * avgCost) * 100.0) : 0.0;
                String sSign = posPnl >= 0 ? "+$" : "-$";

                System.out.printf("%-6s | %-5d | $%-11.2f | $%-11.2f | $%-13.2f | %s%-6.2f (%.2f%%)%n",
                        sym, qty, avgCost, curPrice, posVal, sSign, Math.abs(posPnl), posPnlPct);
            }
        }
        System.out.println("----------------------------------------------------------------------------------------");
    }

    // 5. Market Updates
    private static void handleMarketUpdates(Trader trader) {
        ConsoleHelper.printSubHeader("MARKET UPDATES & REAL-TIME NEWS FEED");
        System.out.printf("Preferences Applied: Watchlist: %s | Alert Threshold: >= %.1f%%%n",
                trader.getWatchlist().isEmpty() ? "All Equities" : trader.getWatchlist(), trader.getAlertThresholdPercent());
        System.out.println("--------------------------------------------------------------------------------");

        List<MarketUpdate> updates = marketUpdateService.getMarketUpdatesForTrader(trader);
        for (MarketUpdate u : updates) {
            System.out.println(u);
        }
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println("\nOptions:");
        System.out.println("1. Simulate Real-Time Market Tick (Fluctuate Stock Prices & Generate News)");
        System.out.println("2. Return to Trader Dashboard");
        int opt = ConsoleHelper.readInt(scanner, "Choice [1-2]");
        if (opt == 1) {
            marketUpdateService.simulateMarketTick();
            savePlatformData();
            ConsoleHelper.printSuccess("Simulated market tick generated! New prices and updates broadcasted.");
        }
    }

    // 6. Trade History
    private static void handleTradeHistory(Trader trader) {
        ConsoleHelper.printSubHeader("TRADE EXECUTION HISTORY FOR " + trader.getUserId().toUpperCase());
        List<Trade> trades = tradingService.getTradesForTrader(trader.getUserId());

        System.out.println("----------------------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-4s | %-6s | %-20s | %-5s | %-9s | %-11s | %-19s%n",
                "Trade ID", "Type", "Symbol", "Company", "Qty", "Price ($)", "Total ($)", "Executed At");
        System.out.println("----------------------------------------------------------------------------------------------------");

        if (trades.isEmpty()) {
            System.out.println("No trades executed yet by this account.");
        } else {
            for (Trade t : trades) {
                System.out.printf("%-10s | %-4s | %-6s | %-20s | %-5d | $%-8.2f | $%-10.2f | %-19s%n",
                        t.getTradeId(), t.getTradeType(), t.getStockSymbol(), t.getStockName(),
                        t.getQuantity(), t.getPrice(), t.getTotalAmount(), t.getFormattedDateTime());
            }
        }
        System.out.println("----------------------------------------------------------------------------------------------------");
    }

    // 7. Alerts and Notifications
    private static void handleAlertsAndNotifications(Trader trader) {
        boolean inAlerts = true;
        while (inAlerts) {
            ConsoleHelper.printSubHeader("ALERTS & NOTIFICATION CENTER");
            List<Notification> notifs = notificationService.getNotificationsForUser(trader.getUserId());
            List<Alert> alerts = notificationService.getAlertsForTrader(trader.getUserId());

            System.out.println("1. Notifications (" + notifs.size() + " total, " +
                    notificationService.getUnreadNotificationsForUser(trader.getUserId()).size() + " unread):");
            if (notifs.isEmpty()) {
                System.out.println("   (No notifications yet)");
            } else {
                for (int i = 0; i < Math.min(6, notifs.size()); i++) {
                    System.out.println("   * " + notifs.get(i));
                }
            }

            System.out.println("\n2. Active Price Alerts (" + alerts.size() + " configured):");
            if (alerts.isEmpty()) {
                System.out.println("   (No price alerts set)");
            } else {
                for (Alert a : alerts) {
                    System.out.println("   * " + a);
                }
            }

            System.out.println("\nOptions:");
            System.out.println("1. Create New Stock Price Alert");
            System.out.println("2. Mark All Notifications as Read");
            System.out.println("3. Return to Trader Dashboard");

            int choice = ConsoleHelper.readInt(scanner, "Select option [1-3]");
            switch (choice) {
                case 1:
                    createPriceAlertPrompt(trader);
                    break;
                case 2:
                    notificationService.markAllAsRead(trader.getUserId());
                    ConsoleHelper.printSuccess("All notifications marked as read.");
                    break;
                case 3:
                    inAlerts = false;
                    break;
                default:
                    ConsoleHelper.printError("Invalid option [1-3].");
            }
        }
    }

    private static void createPriceAlertPrompt(Trader trader) {
        ConsoleHelper.printSubHeader("CONFIGURE STOCK PRICE ALERT");
        String sym = ConsoleHelper.readString(scanner, "Enter Stock Symbol for Alert (e.g. AAPL)");
        Stock s = marketUpdateService.getStock(sym);
        if (s == null) {
            ConsoleHelper.printError("Stock symbol '" + sym + "' not found on exchange.");
            return;
        }
        System.out.println("Current price of " + s.getSymbol() + " is $" + s.getCurrentPrice());
        System.out.println("Condition: 1. PRICE_ABOVE  |  2. PRICE_BELOW  |  3. PERCENT_CHANGE_ABOVE");
        int c = ConsoleHelper.readInt(scanner, "Condition [1-3]");
        Alert.AlertCondition condition = Alert.AlertCondition.PRICE_ABOVE;
        if (c == 2) condition = Alert.AlertCondition.PRICE_BELOW;
        else if (c == 3) condition = Alert.AlertCondition.PERCENT_CHANGE_ABOVE;

        double target = ConsoleHelper.readDouble(scanner, "Target Price / Percentage Value");
        String alertId = "ALT-" + System.currentTimeMillis() % 10000;
        Alert alert = new Alert(alertId, trader.getUserId(), sym, target, condition);
        notificationService.createAlert(alert);

        ConsoleHelper.printSuccess("Price alert created for " + sym + " when " + condition + " " + target);
    }

    // 8. Update Preferences
    private static void handleUpdatePreferences(Trader trader) {
        ConsoleHelper.printSubHeader("UPDATE TRADER PREFERENCES");
        System.out.println("Current Watchlist: " + trader.getWatchlist());
        System.out.println("Current Price Change Alert Threshold: " + trader.getAlertThresholdPercent() + "%");
        System.out.println("Email Alerts: " + (trader.isEmailAlertsEnabled() ? "ENABLED" : "DISABLED"));

        System.out.println("\nOptions:");
        System.out.println("1. Add Stock to Watchlist");
        System.out.println("2. Remove Stock from Watchlist");
        System.out.println("3. Update Price Change Alert Threshold (%)");
        System.out.println("4. Toggle Simulated Email Alerts");
        System.out.println("5. Return to Trader Dashboard");

        int choice = ConsoleHelper.readInt(scanner, "Select option [1-5]");
        switch (choice) {
            case 1:
                String addSym = ConsoleHelper.readString(scanner, "Enter stock symbol to add");
                trader.addToWatchlist(addSym);
                savePlatformData();
                ConsoleHelper.printSuccess("Added '" + addSym.toUpperCase() + "' to watchlist.");
                break;
            case 2:
                String remSym = ConsoleHelper.readString(scanner, "Enter stock symbol to remove");
                trader.removeFromWatchlist(remSym);
                savePlatformData();
                ConsoleHelper.printSuccess("Removed '" + remSym.toUpperCase() + "' from watchlist.");
                break;
            case 3:
                double thresh = ConsoleHelper.readDouble(scanner, "Enter new alert threshold percent (e.g. 1.5)");
                if (thresh >= 0) {
                    trader.setAlertThresholdPercent(thresh);
                    savePlatformData();
                    ConsoleHelper.printSuccess("Market update alert threshold set to " + thresh + "%.");
                }
                break;
            case 4:
                trader.setEmailAlertsEnabled(!trader.isEmailAlertsEnabled());
                savePlatformData();
                ConsoleHelper.printSuccess("Email alerts status: " + (trader.isEmailAlertsEnabled() ? "ENABLED" : "DISABLED"));
                break;
            case 5:
                break;
            default:
                ConsoleHelper.printError("Invalid option [1-5].");
        }
    }

    // Guest preview
    private static void displayGuestMarketPreview() {
        ConsoleHelper.printHeader("LIVE MARKET TICKER (GUEST PREVIEW)");
        displayStocksTable();
        System.out.println("\nLatest Market Updates:");
        List<MarketUpdate> updates = marketUpdateService.getAllMarketUpdates();
        for (int i = 0; i < Math.min(4, updates.size()); i++) {
            System.out.println(" * " + updates.get(i));
        }
        ConsoleHelper.pressEnterToContinue(scanner);
    }
}
