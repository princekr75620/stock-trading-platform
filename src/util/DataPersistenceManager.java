package util;

import model.*;
import service.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles persistent file storage and retrieval using Java File I/O.
 * Persists Users, Stocks, Trades, Portfolios, Security Settings, and System Settings.
 * Creates 'data/' directory automatically and loads previously saved data on application restart.
 */
public class DataPersistenceManager {
    private static final String DATA_DIR = "data";
    private static final String USERS_FILE = DATA_DIR + File.separator + "users.txt";
    private static final String STOCKS_FILE = DATA_DIR + File.separator + "stocks.txt";
    private static final String TRADES_FILE = DATA_DIR + File.separator + "trades.txt";
    private static final String PORTFOLIOS_FILE = DATA_DIR + File.separator + "portfolios.txt";
    private static final String SECURITY_FILE = DATA_DIR + File.separator + "security.txt";
    private static final String SYSTEM_FILE = DATA_DIR + File.separator + "settings.txt";

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DataPersistenceManager() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Saves all platform state to disk files.
     */
    public synchronized void saveAll(IUserService userService,
                                    IMarketUpdateService marketUpdateService,
                                    ITradingService tradingService,
                                    IPortfolioService portfolioService,
                                    ISecurityService securityService,
                                    ISystemSettingsService systemSettingsService) {
        saveUsers(userService.getAllUsers());
        saveStocks(marketUpdateService.getAllStocks());
        saveTrades(tradingService.getAllTrades());
        savePortfolios(portfolioService.getAllPortfolios());
        if (securityService != null) saveSecuritySettings(securityService.getSecuritySettings());
        if (systemSettingsService != null) saveSystemSettings(systemSettingsService.getSettings());
    }

    public synchronized void saveUsers(List<User> users) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(USERS_FILE)))) {
            for (User u : users) {
                if (u instanceof Admin) {
                    Admin a = (Admin) u;
                    pw.printf(Locale.US, "ADMIN|%s|%s|%s|%s|%s|%s%n",
                            a.getUserId(), a.getName(), a.getEmail(), a.getPassword(),
                            a.getDepartment(), a.getCreatedAt().format(DTF));
                } else if (u instanceof Trader) {
                    Trader t = (Trader) u;
                    String watchlistJoined = String.join(",", t.getWatchlist());
                    pw.printf(Locale.US, "TRADER|%s|%s|%s|%s|%.2f|%s|%.2f|%s|%s%n",
                            t.getUserId(), t.getName(), t.getEmail(), t.getPassword(),
                            t.getCashBalance(), watchlistJoined, t.getAlertThresholdPercent(),
                            t.isEmailAlertsEnabled(), t.getCreatedAt().format(DTF));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to persist users: " + e.getMessage());
        }
    }

    public List<User> loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) return null;

        List<User> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 6 && parts[0].equalsIgnoreCase("ADMIN")) {
                    LocalDateTime dt = parts.length >= 7 ? LocalDateTime.parse(parts[6], DTF) : LocalDateTime.now();
                    Admin admin = new Admin(parts[1], parts[2], parts[3], parts[4], parts[5], dt);
                    list.add(admin);
                } else if (parts.length >= 6 && parts[0].equalsIgnoreCase("TRADER")) {
                    double balance = Double.parseDouble(parts[5]);
                    Set<String> wl = new HashSet<>();
                    if (parts.length >= 7 && !parts[6].isEmpty()) {
                        wl.addAll(Arrays.asList(parts[6].split(",")));
                    }
                    double threshold = parts.length >= 8 ? Double.parseDouble(parts[7]) : 2.0;
                    boolean emailAlerts = parts.length < 9 || Boolean.parseBoolean(parts[8]);
                    LocalDateTime dt = parts.length >= 10 ? LocalDateTime.parse(parts[9], DTF) : LocalDateTime.now();

                    Trader trader = new Trader(parts[1], parts[2], parts[3], parts[4], balance, wl, threshold, emailAlerts, dt);
                    list.add(trader);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading users file: " + e.getMessage());
        }
        return list;
    }

    public synchronized void saveStocks(List<Stock> stocks) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(STOCKS_FILE)))) {
            for (Stock s : stocks) {
                pw.printf(Locale.US, "%s|%s|%.2f|%d|%.2f|%.2f|%.2f|%d%n",
                        s.getSymbol(), s.getName(), s.getCurrentPrice(), s.getAvailableQuantity(),
                        s.getPreviousPrice(), s.getDayHigh(), s.getDayLow(), s.getVolumeTraded());
            }
        } catch (IOException e) {
            System.err.println("Failed to persist stocks: " + e.getMessage());
        }
    }

    public List<Stock> loadStocks() {
        File file = new File(STOCKS_FILE);
        if (!file.exists()) return null;

        List<Stock> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String sym = parts[0];
                    String name = parts[1];
                    double price = Double.parseDouble(parts[2]);
                    int qty = Integer.parseInt(parts[3]);
                    double prevPrice = parts.length >= 5 ? Double.parseDouble(parts[4]) : price;
                    double dayHigh = parts.length >= 6 ? Double.parseDouble(parts[5]) : price;
                    double dayLow = parts.length >= 7 ? Double.parseDouble(parts[6]) : price;
                    long vol = parts.length >= 8 ? Long.parseLong(parts[7]) : 0;

                    list.add(new Stock(sym, name, price, qty, prevPrice, dayHigh, dayLow, vol));
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading stocks file: " + e.getMessage());
        }
        return list;
    }

    public synchronized void saveTrades(List<Trade> trades) {
        if (trades == null) return;
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(TRADES_FILE)))) {
            java.util.Set<String> savedIds = new java.util.HashSet<>();
            for (Trade t : trades) {
                if (t != null && t.getTradeId() != null && savedIds.add(t.getTradeId())) {
                    pw.printf(Locale.US, "%s|%s|%s|%s|%d|%s|%.2f|%.2f|%s%n",
                            t.getTradeId(), t.getTraderId(), t.getStockSymbol(), t.getStockName(),
                            t.getQuantity(), t.getTradeType().name(), t.getPrice(), t.getTotalAmount(),
                            t.getDateTime().format(DTF));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to persist trades: " + e.getMessage());
        }
    }

    public List<Trade> loadTrades() {
        File file = new File(TRADES_FILE);
        if (!file.exists()) return null;

        List<Trade> list = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 9) {
                    String tradeId = parts[0].trim();
                    if (!seenIds.add(tradeId)) {
                        continue;
                    }
                    TradeType type = TradeType.valueOf(parts[5]);
                    LocalDateTime dt = LocalDateTime.parse(parts[8], DTF);
                    Trade t = new Trade(tradeId, parts[1], parts[2], parts[3],
                            Integer.parseInt(parts[4]), type, Double.parseDouble(parts[6]),
                            Double.parseDouble(parts[7]), dt);
                    list.add(t);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading trades file: " + e.getMessage());
        }
        return list;
    }

    public synchronized void savePortfolios(Map<String, Portfolio> portfolios) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(PORTFOLIOS_FILE)))) {
            for (Portfolio p : portfolios.values()) {
                StringBuilder holdingsStr = new StringBuilder();
                for (Map.Entry<String, Integer> h : p.getHoldings().entrySet()) {
                    if (holdingsStr.length() > 0) holdingsStr.append(";");
                    holdingsStr.append(h.getKey()).append(":").append(h.getValue());
                }

                StringBuilder costStr = new StringBuilder();
                for (Map.Entry<String, Double> c : p.getCostBasisMap().entrySet()) {
                    if (costStr.length() > 0) costStr.append(";");
                    costStr.append(c.getKey()).append(":").append(String.format(Locale.US, "%.2f", c.getValue()));
                }

                pw.printf(Locale.US, "%s|%s|%s|%.2f%n",
                        p.getTraderId(), holdingsStr.toString(), costStr.toString(), p.getRealizedProfitLoss());
            }
        } catch (IOException e) {
            System.err.println("Failed to persist portfolios: " + e.getMessage());
        }
    }

    public Map<String, Portfolio> loadPortfolios() {
        File file = new File(PORTFOLIOS_FILE);
        if (!file.exists()) return null;

        Map<String, Portfolio> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    String traderId = parts[0];
                    Map<String, Integer> holdings = new HashMap<>();
                    if (!parts[1].isEmpty()) {
                        for (String item : parts[1].split(";")) {
                            String[] kv = item.split(":");
                            if (kv.length == 2) holdings.put(kv[0], Integer.parseInt(kv[1]));
                        }
                    }
                    Map<String, Double> cost = new HashMap<>();
                    if (!parts[2].isEmpty()) {
                        for (String item : parts[2].split(";")) {
                            String[] kv = item.split(":");
                            if (kv.length == 2) cost.put(kv[0], Double.parseDouble(kv[1]));
                        }
                    }
                    double realized = Double.parseDouble(parts[3]);
                    map.put(traderId, new Portfolio(traderId, holdings, cost, realized));
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading portfolios file: " + e.getMessage());
        }
        return map;
    }

    public synchronized void saveSecuritySettings(SecuritySettings s) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(SECURITY_FILE)))) {
            pw.printf(Locale.US, "%d|%b|%b|%d|%d|%d|%b|%b|%b|%.2f|%b|%.2f%n",
                    s.getMinPasswordLength(), s.isRequireSpecialChar(), s.isRequireNumbers(),
                    s.getPasswordExpiryDays(), s.getMaxFailedLoginAttempts(), s.getSessionTimeoutMinutes(),
                    s.isTwoFactorAuthRequired(), s.isAutomaticAccountLockout(), s.isIpAnomalyDetection(),
                    s.getHighValueTradeThreshold(), s.isRequireTwoStepForHighValueTrades(),
                    s.getDailyTradingLimitPerTrader());
        } catch (IOException e) {
            System.err.println("Failed to persist security settings: " + e.getMessage());
        }
    }

    public SecuritySettings loadSecuritySettings() {
        File file = new File(SECURITY_FILE);
        if (!file.exists()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String[] p = line.trim().split("\\|");
                if (p.length >= 12) {
                    SecuritySettings s = new SecuritySettings();
                    s.setMinPasswordLength(Integer.parseInt(p[0]));
                    s.setRequireSpecialChar(Boolean.parseBoolean(p[1]));
                    s.setRequireNumbers(Boolean.parseBoolean(p[2]));
                    s.setPasswordExpiryDays(Integer.parseInt(p[3]));
                    s.setMaxFailedLoginAttempts(Integer.parseInt(p[4]));
                    s.setSessionTimeoutMinutes(Integer.parseInt(p[5]));
                    s.setTwoFactorAuthRequired(Boolean.parseBoolean(p[6]));
                    s.setAutomaticAccountLockout(Boolean.parseBoolean(p[7]));
                    s.setIpAnomalyDetection(Boolean.parseBoolean(p[8]));
                    s.setHighValueTradeThreshold(Double.parseDouble(p[9]));
                    s.setRequireTwoStepForHighValueTrades(Boolean.parseBoolean(p[10]));
                    s.setDailyTradingLimitPerTrader(Double.parseDouble(p[11]));
                    return s;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading security file: " + e.getMessage());
        }
        return null;
    }

    public synchronized void saveSystemSettings(SystemSettings s) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(SYSTEM_FILE)))) {
            pw.printf(Locale.US, "%s|%.4f|%s|%d|%b|%d|%s|%d%n",
                    s.getExchangeName(), s.getTradingFeePercentage(), s.getMarketStatus().name(),
                    s.getMarketUpdateIntervalSeconds(), s.isMaintenanceMode(),
                    s.getBackupFrequencyHours(), s.getSystemVersion(), s.getMaxConcurrentUsers());
        } catch (IOException e) {
            System.err.println("Failed to persist system settings: " + e.getMessage());
        }
    }

    public SystemSettings loadSystemSettings() {
        File file = new File(SYSTEM_FILE);
        if (!file.exists()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String[] p = line.trim().split("\\|");
                if (p.length >= 8) {
                    SystemSettings s = new SystemSettings();
                    s.setExchangeName(p[0]);
                    s.setTradingFeePercentage(Double.parseDouble(p[1]));
                    s.setMarketStatus(SystemSettings.MarketStatus.valueOf(p[2]));
                    s.setMarketUpdateIntervalSeconds(Integer.parseInt(p[3]));
                    s.setMaintenanceMode(Boolean.parseBoolean(p[4]));
                    s.setBackupFrequencyHours(Integer.parseInt(p[5]));
                    s.setSystemVersion(p[6]);
                    s.setMaxConcurrentUsers(Integer.parseInt(p[7]));
                    return s;
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading system settings file: " + e.getMessage());
        }
        return null;
    }
}
