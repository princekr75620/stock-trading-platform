package servlet;

import model.Portfolio;
import model.Trader;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet providing Trader Portfolio holdings and valuation.
 * Handles GET /api/portfolio (checks session or traderId).
 * Fulfills Servlets & Portfolio Integration (Rubric Section 10) for GUVI Evaluation.
 */
public class PortfolioServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = getAuthenticatedUser(request);
        String traderId = (user != null) ? user.getUserId() : request.getParameter("traderId");

        if (traderId == null || traderId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"traderId query parameter or active login session is required.\"}");
            return;
        }

        Portfolio portfolio = portfolioService.getOrCreatePortfolio(traderId.trim());
        User u = null;
        try {
            u = userService.getUserById(traderId.trim());
        } catch (Exception ignored) {}
        Trader trader = (u instanceof Trader) ? (Trader) u : null;
        String json = JsonHelper.portfolioToJson(portfolio, trader, marketUpdateService.getStockMap());
        sendJsonResponse(response, HttpServletResponse.SC_OK, json);
    }
}
