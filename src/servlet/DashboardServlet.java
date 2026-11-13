package servlet;

import model.Portfolio;
import model.Stock;
import model.Trade;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Servlet providing Trader Dashboard telemetry and summarized metrics.
 * Fulfills DashboardServlet requirement for GUVI Evaluation.
 */
public class DashboardServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = getAuthenticatedUser(request);
        if (user == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required for dashboard access.\"}");
            return;
        }

        String requestedId = request.getParameter("traderId");
        if (requestedId == null || requestedId.trim().isEmpty()) {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/dashboard/") && path.length() > "/api/dashboard/".length()) {
                requestedId = path.substring("/api/dashboard/".length());
            }
        }

        String userId = (requestedId != null && !requestedId.trim().isEmpty())
                ? requestedId.trim()
                : user.getUserId();

        if (!ensureOwnerOrAdmin(request, response, userId)) {
            return;
        }

        User targetUser = user;
        try {
            targetUser = userService.getUserById(userId);
        } catch (Exception ignored) {}

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(userId);
        List<Trade> userTrades = tradingService.getTradesForTrader(userId);
        List<Stock> stocks = marketUpdateService.getAllStocks();

        double portfolioVal = (portfolio != null) ? portfolio.getCurrentPortfolioValue(marketUpdateService.getStockMap()) : 0.0;
        double unrealizedPnL = (portfolio != null) ? portfolio.getUnrealizedProfitLoss(marketUpdateService.getStockMap()) : 0.0;
        double cashBalance = (targetUser != null) ? targetUser.getBalance() : 0.0;
        model.Trader trader = (targetUser instanceof model.Trader) ? (model.Trader) targetUser : null;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"userId\":\"").append(JsonHelper.escape(userId)).append("\",");
        sb.append("\"cashBalance\":").append(cashBalance).append(",");
        sb.append("\"portfolioValue\":").append(portfolioVal).append(",");
        sb.append("\"unrealizedPnL\":").append(unrealizedPnL).append(",");
        sb.append("\"totalNetWorth\":").append(cashBalance + portfolioVal).append(",");
        sb.append("\"tradeCount\":").append(userTrades.size()).append(",");
        sb.append("\"availableStocksCount\":").append(stocks.size()).append(",");
        sb.append("\"portfolio\":").append(JsonHelper.portfolioToJson(portfolio, trader, marketUpdateService.getStockMap())).append(",");
        sb.append("\"recentTrades\":").append(JsonHelper.tradesToJson(userTrades.subList(0, Math.min(5, userTrades.size()))));
        sb.append("}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
