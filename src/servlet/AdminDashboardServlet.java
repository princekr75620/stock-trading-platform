package servlet;

import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet handling Admin Dashboard metrics and platform analytics.
 * Fulfills AdminDashboardServlet requirement for GUVI Evaluation.
 */
public class AdminDashboardServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        int totalUsers = userService.getUserCount();
        int totalTraders = userService.getTraderCount();
        int totalAdmins = userService.getAdminCount();
        int totalTrades = tradingService.getTotalTradeCount();
        double totalVolume = tradingService.getTotalTradingVolume();
        int totalStocks = marketUpdateService.getAllStocks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"totalUsers\":").append(totalUsers).append(",");
        sb.append("\"activeTraders\":").append(totalTraders).append(",");
        sb.append("\"activeAdmins\":").append(totalAdmins).append(",");
        sb.append("\"totalTrades\":").append(totalTrades).append(",");
        sb.append("\"totalTradingVolume\":").append(totalVolume).append(",");
        sb.append("\"totalStocksListed\":").append(totalStocks).append(",");
        sb.append("\"systemStatus\":\"ONLINE\",");
        sb.append("\"databaseEngine\":\"MySQL / Relational JDBC\"");
        sb.append("}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
