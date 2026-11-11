package servlet;

import model.Trade;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Servlet providing Trade and Transaction History.
 * Handles GET /api/trades and GET /api/transactions.
 * Fulfills Servlets & Collections/Generics (List<Trade>) for GUVI Evaluation.
 */
public class TransactionServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User user = getAuthenticatedUser(request);
        if (user == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required to access trade records.\"}");
            return;
        }

        String traderId = request.getParameter("traderId");
        if (traderId != null && !traderId.trim().isEmpty()) {
            if (!user.hasAdminPrivileges() && !user.getUserId().equalsIgnoreCase(traderId.trim())) {
                sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                        "{\"success\":false,\"error\":\"Access denied: Cannot access another user's transaction history.\"}");
                return;
            }
        } else if (!user.hasAdminPrivileges()) {
            traderId = user.getUserId();
        }

        List<Trade> trades;
        if (traderId != null && !traderId.trim().isEmpty()) {
            trades = tradingService.getTradesForTrader(traderId.trim());
        } else {
            trades = tradingService.getAllTrades();
        }

        sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.tradesToJson(trades));
    }
}
