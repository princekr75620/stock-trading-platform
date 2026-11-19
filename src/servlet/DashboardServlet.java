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
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import util.MoneyUtil;

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
            User dbUser = userService.getUserById(userId);
            if (dbUser != null) {
                targetUser = dbUser;
            }
        } catch (Exception ex) {
            System.err.println("[DashboardServlet] Failed to refresh user '" + userId + "': " + ex.getMessage());
        }

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(userId);
        List<Trade> userTrades = tradingService.getTradesForTrader(userId);
        List<Stock> stocks = marketUpdateService.getAllStocks();

        BigDecimal portfolioVal = (portfolio != null)
                ? portfolio.getCurrentPortfolioValueDecimal(marketUpdateService.getStockMap())
                : MoneyUtil.ZERO;
        BigDecimal unrealizedPnL = (portfolio != null)
                ? portfolio.getUnrealizedProfitLossDecimal(marketUpdateService.getStockMap())
                : MoneyUtil.ZERO;
        BigDecimal cashBalance = (targetUser != null)
                ? MoneyUtil.round(targetUser.getBalanceDecimal())
                : MoneyUtil.ZERO;
        BigDecimal totalNetWorth = MoneyUtil.round(cashBalance.add(portfolioVal));
        model.Trader trader = (targetUser instanceof model.Trader) ? (model.Trader) targetUser : null;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"userId\":\"").append(JsonHelper.escape(userId)).append("\",");
        sb.append("\"cashBalance\":").append(String.format(Locale.US, "%.2f", cashBalance)).append(",");
        sb.append("\"portfolioValue\":").append(String.format(Locale.US, "%.2f", portfolioVal)).append(",");
        sb.append("\"unrealizedPnL\":").append(String.format(Locale.US, "%.2f", unrealizedPnL)).append(",");
        sb.append("\"totalNetWorth\":").append(String.format(Locale.US, "%.2f", totalNetWorth)).append(",");
        sb.append("\"tradeCount\":").append(userTrades.size()).append(",");
        sb.append("\"availableStocksCount\":").append(stocks.size()).append(",");
        sb.append("\"portfolio\":").append(JsonHelper.portfolioToJson(portfolio, trader, marketUpdateService.getStockMap())).append(",");
        sb.append("\"recentTrades\":").append(JsonHelper.tradesToJson(userTrades.subList(0, Math.min(5, userTrades.size()))));
        sb.append("}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
