package service;

import model.Admin;
import model.Portfolio;
import model.Stock;
import model.Trade;
import model.TradeType;
import model.Trader;
import model.User;
import model.UserRole;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates structured administrative and executive reports across the platform:
 * 1. User Report
 * 2. Trade Report
 * 3. Portfolio Report
 * 4. Financial Report
 * 5. System Analytics Report
 */
public class ReportGenerator {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private IUserService userService;
    private ITradingService tradingService;
    private IPortfolioService portfolioService;
    private IMarketUpdateService marketUpdateService;
    private ISecurityService securityService;
    private ISystemSettingsService systemSettingsService;

    public ReportGenerator(IUserService userService,
                           ITradingService tradingService,
                           IPortfolioService portfolioService,
                           IMarketUpdateService marketUpdateService,
                           ISecurityService securityService,
                           ISystemSettingsService systemSettingsService) {
        this.userService = userService;
        this.tradingService = tradingService;
        this.portfolioService = portfolioService;
        this.marketUpdateService = marketUpdateService;
        this.securityService = securityService;
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * Generates User Report.
     */
    public String generateUserReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("                               EXECUTIVE USER AUDIT REPORT                              \n");
        sb.append("Generated On: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("========================================================================================\n");
        sb.append(String.format("Total Registered Users : %d\n", userService.getUserCount()));
        sb.append(String.format("Total Traders          : %d\n", userService.getTraderCount()));
        sb.append(String.format("Total Administrators   : %d\n", userService.getAdminCount()));
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format("%-12s | %-20s | %-26s | %-8s | %-12s\n", "User ID", "Full Name", "Email Address", "Role", "Balance / Dept"));
        sb.append("----------------------------------------------------------------------------------------\n");

        for (User u : userService.getAllUsers()) {
            String extra = "";
            if (u instanceof Trader) {
                extra = String.format("$%,.2f", ((Trader) u).getCashBalance());
            } else if (u instanceof Admin) {
                extra = ((Admin) u).getDepartment();
            }
            sb.append(String.format("%-12s | %-20s | %-26s | %-8s | %-12s\n",
                    u.getUserId(), u.getName(), u.getEmail(), u.getRole(), extra));
        }
        sb.append("========================================================================================\n");
        return sb.toString();
    }

    /**
     * Generates Trade Report.
     */
    public String generateTradeReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================================================================\n");
        sb.append("                                    PLATFORM TRADE EXECUTION REPORT                                 \n");
        sb.append("Generated On: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("====================================================================================================\n");
        sb.append(String.format("Total Trades Executed : %d\n", tradingService.getTotalTradeCount()));
        sb.append(String.format("BUY Transactions      : %d\n", tradingService.getBuyTradeCount()));
        sb.append(String.format("SELL Transactions     : %d\n", tradingService.getSellTradeCount()));
        sb.append(String.format("Total Trading Volume  : $%,.2f\n", tradingService.getTotalTradingVolume()));
        sb.append("----------------------------------------------------------------------------------------------------\n");
        sb.append(String.format("%-10s | %-10s | %-6s | %-20s | %-4s | %-5s | %-9s | %-11s | %-19s\n",
                "Trade ID", "Trader ID", "Symbol", "Stock Name", "Type", "Qty", "Price", "Total Amt", "Execution Time"));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        List<Trade> trades = tradingService.getAllTrades();
        if (trades.isEmpty()) {
            sb.append("No trades recorded yet.\n");
        } else {
            for (Trade t : trades) {
                sb.append(String.format("%-10s | %-10s | %-6s | %-20s | %-4s | %-5d | $%-8.2f | $%-10.2f | %-19s\n",
                        t.getTradeId(), t.getTraderId(), t.getStockSymbol(), t.getStockName(),
                        t.getTradeType(), t.getQuantity(), t.getPrice(), t.getTotalAmount(), t.getFormattedDateTime()));
            }
        }
        sb.append("====================================================================================================\n");
        return sb.toString();
    }

    /**
     * Generates Portfolio Report.
     */
    public String generatePortfolioReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================================================================\n");
        sb.append("                                   TRADER PORTFOLIO AUDIT REPORT                                    \n");
        sb.append("Generated On: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("====================================================================================================\n");

        Map<String, Portfolio> portfolios = portfolioService.getAllPortfolios();
        Map<String, Stock> marketStocks = marketUpdateService.getStockMap();

        double platformAUM = portfolioService.calculateTotalPlatformAUM(marketStocks);
        sb.append(String.format("Total Active Portfolios : %d\n", portfolios.size()));
        sb.append(String.format("Total Platform AUM      : $%,.2f\n", platformAUM));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        if (portfolios.isEmpty()) {
            sb.append("No active portfolios found.\n");
        } else {
            for (Portfolio p : portfolios.values()) {
                double investVal = p.getTotalInvestmentValue();
                double currentVal = p.getCurrentPortfolioValue(marketStocks);
                double pnl = p.getUnrealizedProfitLoss(marketStocks);
                double pnlPct = p.getUnrealizedProfitLossPercent(marketStocks);
                String pnlSign = pnl >= 0 ? "+$" : "-$";

                sb.append(String.format("Trader: %-12s | Holdings: %-2d positions | Cost: $%,-10.2f | Current: $%,-10.2f | P&L: %s%,-8.2f (%.2f%%)\n",
                        p.getTraderId(), p.getHoldings().size(), investVal, currentVal, pnlSign, Math.abs(pnl), pnlPct));

                if (!p.getHoldings().isEmpty()) {
                    for (Map.Entry<String, Integer> entry : p.getHoldings().entrySet()) {
                        String sym = entry.getKey();
                        int qty = entry.getValue();
                        double avgCost = p.getAverageBuyPrice(sym);
                        Stock st = marketStocks.get(sym);
                        double curPrice = st != null ? st.getCurrentPrice() : 0.0;
                        double posVal = qty * curPrice;
                        double posPnl = posVal - (qty * avgCost);
                        String sSign = posPnl >= 0 ? "+" : "-";

                        sb.append(String.format("   -> %-5s | Qty: %-4d | Avg Buy: $%-7.2f | Current: $%-7.2f | Val: $%-9.2f | P&L: %s$%.2f\n",
                                sym, qty, avgCost, curPrice, posVal, sSign, Math.abs(posPnl)));
                    }
                } else {
                    sb.append("   -> (All cash / No active equity positions)\n");
                }
                sb.append("   -----------------------------------------------------------------------------------------\n");
            }
        }
        sb.append("====================================================================================================\n");
        return sb.toString();
    }

    /**
     * Generates Financial Report.
     */
    public String generateFinancialReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("                             FINANCIAL PERFORMANCE & REVENUE REPORT                     \n");
        sb.append("Generated On: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("========================================================================================\n");

        double totalVol = tradingService.getTotalTradingVolume();
        double feePct = (systemSettingsService != null) ? systemSettingsService.getSettings().getTradingFeePercentage() : 0.15;
        double estimatedFeeRevenue = Math.round((totalVol * (feePct / 100.0)) * 100.0) / 100.0;
        int totalTrades = tradingService.getTotalTradeCount();
        int buyCount = tradingService.getBuyTradeCount();
        int sellCount = tradingService.getSellTradeCount();

        sb.append(String.format("Gross Trading Volume      : $%,.2f\n", totalVol));
        sb.append(String.format("Platform Trading Fee Rate : %.2f%%\n", feePct));
        sb.append(String.format("Estimated Exchange Revenue: $%,.2f\n", estimatedFeeRevenue));
        sb.append(String.format("Total Executed Trades     : %d\n", totalTrades));
        sb.append(String.format("BUY Volume Breakdown      : %d orders (%.1f%%)\n",
                buyCount, totalTrades > 0 ? (buyCount * 100.0 / totalTrades) : 0));
        sb.append(String.format("SELL Volume Breakdown     : %d orders (%.1f%%)\n",
                sellCount, totalTrades > 0 ? (sellCount * 100.0 / totalTrades) : 0));
        sb.append(String.format("Average Order Value       : $%,.2f\n",
                totalTrades > 0 ? (totalVol / totalTrades) : 0.0));
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append("Financial Security State: ").append(securityService != null ? securityService.getSecurityHealthScore() : "HEALTHY").append("\n");
        sb.append("========================================================================================\n");
        return sb.toString();
    }

    /**
     * Generates System Analytics Report.
     */
    public String generateSystemAnalyticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("                             SYSTEM ANALYTICS & HEALTH AUDIT                            \n");
        sb.append("Generated On: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("========================================================================================\n");

        if (systemSettingsService != null) {
            Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                sb.append(String.format("%-26s : %s\n", entry.getKey(), entry.getValue()));
            }
        }
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format("Security Incidents Count   : %d open / %d total\n",
                securityService != null ? securityService.getOpenIncidentCount() : 0,
                securityService != null ? securityService.getAllIncidents().size() : 0));
        sb.append(String.format("Total Market Instruments   : %d listed equities\n",
                marketUpdateService.getAllStocks().size()));
        sb.append("========================================================================================\n");
        return sb.toString();
    }
}
