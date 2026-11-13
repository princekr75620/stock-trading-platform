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
        if (user == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in to view portfolio.\"}");
            return;
        }

        String requestedId = request.getParameter("traderId");
        if (requestedId == null || requestedId.trim().isEmpty()) {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/portfolio/") && path.length() > "/api/portfolio/".length()) {
                requestedId = path.substring("/api/portfolio/".length());
            }
        }

        String targetTraderId = (requestedId != null && !requestedId.trim().isEmpty())
                ? requestedId.trim()
                : user.getUserId();

        if (!ensureOwnerOrAdmin(request, response, targetTraderId)) {
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
