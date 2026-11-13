package servlet;

import model.SystemSettings;
import server.JsonHelper;
import util.DBConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Servlet handling Admin Dashboard metrics and platform analytics.
 * Strictly enforces ADMIN role via ensureAdmin and reports live DB engine metadata.
 */
public class AdminDashboardServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!ensureAdmin(request, response)) {
            return;
        }

        int totalUsers = userService.getUserCount();
        int totalTraders = userService.getTraderCount();
        int totalAdmins = userService.getAdminCount();
        int totalTrades = tradingService.getTotalTradeCount();
        int buyTrades = tradingService.getBuyTradeCount();
        int sellTrades = tradingService.getSellTradeCount();
        double totalVolume = tradingService.getTotalTradingVolume();
        int totalStocks = marketUpdateService.getAllStocks().size();

        SystemSettings settings = systemSettingsService.getSettings();
        double feeRevenue = (totalVolume * settings.getTradingFeePercentage()) / 100.0;
        String secScore = securityService.getSecurityHealthScore();
        int openIncidents = securityService.getOpenIncidentCount();

        Map<String, Object> dbHealth = DBConnection.getHealthStatus();
        String engineType = String.valueOf(dbHealth.getOrDefault("engineType", "SQLite (Embedded Relational)"));
        String sysStatus = settings.isMaintenanceMode()
                ? "MAINTENANCE"
                : (settings.getMarketStatus() == SystemSettings.MarketStatus.OPEN ? "OPERATIONAL" : settings.getMarketStatus().name());

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalUsers\":").append(totalUsers).append(",");
        sb.append("\"totalTraders\":").append(totalTraders).append(",");
        sb.append("\"activeTraders\":").append(totalTraders).append(",");
        sb.append("\"totalAdmins\":").append(totalAdmins).append(",");
        sb.append("\"activeAdmins\":").append(totalAdmins).append(",");
        sb.append("\"totalTrades\":").append(totalTrades).append(",");
        sb.append("\"buyTrades\":").append(buyTrades).append(",");
        sb.append("\"buyCount\":").append(buyTrades).append(",");
        sb.append("\"sellTrades\":").append(sellTrades).append(",");
        sb.append("\"sellCount\":").append(sellTrades).append(",");
        sb.append("\"totalTradingVolume\":").append(String.format(Locale.US, "%.2f", totalVolume)).append(",");
        sb.append("\"totalVolumeUSD\":").append(String.format(Locale.US, "%.2f", totalVolume)).append(",");
        sb.append("\"feeRevenue\":").append(String.format(Locale.US, "%.2f", feeRevenue)).append(",");
        sb.append("\"totalStocksListed\":").append(totalStocks).append(",");
        sb.append("\"systemStatus\":\"").append(JsonHelper.escape(sysStatus)).append("\",");
        sb.append("\"tradingStatus\":\"").append(settings.getMarketStatus().name()).append("\",");
        sb.append("\"securityStatus\":\"").append(JsonHelper.escape(secScore)).append("\",");
        sb.append("\"openIncidents\":").append(openIncidents).append(",");
        sb.append("\"databaseEngine\":\"").append(JsonHelper.escape(engineType)).append("\"");
        sb.append("}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
