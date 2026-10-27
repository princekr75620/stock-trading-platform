package servlet;

import model.Portfolio;
import model.Trader;
import model.User;
import model.UserRole;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet providing Trader Portfolio holdings and valuation.
 * Enforces cross-tenant portfolio isolation: users can only view their own portfolio,
 * while Administrators can audit any trader portfolio.
 * Fulfills Servlets & Portfolio Integration for GUVI Evaluation.
 */
public class PortfolioServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = getAuthenticatedUser(request);
        String requestedId = request.getParameter("traderId");

        String targetTraderId;
        if (user != null) {
            if (requestedId != null && !requestedId.trim().isEmpty()) {
                // If requesting a specific portfolio, verify caller is owner or admin
                if (user.getRole() != UserRole.ADMIN && !user.getUserId().equalsIgnoreCase(requestedId.trim())) {
                    sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                            "{\"success\":false,\"error\":\"Access denied: Cannot access another user's portfolio.\"}");
                    return;
                }
                targetTraderId = requestedId.trim();
            } else {
                targetTraderId = user.getUserId();
            }
        } else if (requestedId != null && !requestedId.trim().isEmpty()) {
            // Unauthenticated request attempting to view portfolio
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in to view portfolio.\"}");
            return;
        } else {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in.\"}");
            return;
        }

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(targetTraderId);
        User u = null;
        try {
            u = userService.getUserById(targetTraderId);
        } catch (Exception ignored) {}
        Trader trader = (u instanceof Trader) ? (Trader) u : null;
        String json = JsonHelper.portfolioToJson(portfolio, trader, marketUpdateService.getStockMap());
        sendJsonResponse(response, HttpServletResponse.SC_OK, json);
    }
}
