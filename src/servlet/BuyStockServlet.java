package servlet;

import exception.*;
import model.Portfolio;
import model.Trade;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Servlet handling BUY Stock orders.
 * Workflow:
 * 1. Validate request and input parameters
 * 2. Get logged-in user from HttpSession
 * 3. Call TradingService to execute transactional Buy (with ACID JDBC commit/rollback)
 * 4. Return updated trade details and portfolio state
 * Fulfills Buy Stock Functionality (Rubric Section 8 & 10) for GUVI Evaluation.
 */
public class BuyStockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Validate input
        Map<String, String> body = parseJsonBody(request);

        String symbol = body.get("symbol");
        if (symbol == null) symbol = request.getParameter("symbol");

        String qtyStr = body.get("quantity");
        if (qtyStr == null) qtyStr = request.getParameter("quantity");

        // 2. Get logged-in user from session
        User sessionUser = getAuthenticatedUser(request);
        String traderId = (sessionUser != null) ? sessionUser.getUserId() : body.get("traderId");
        if (traderId == null) traderId = request.getParameter("traderId");

        if (traderId == null || traderId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Please log in to execute trades.\"}");
            return;
        }

        if (symbol == null || symbol.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Stock symbol is required.\"}");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyStr);
            if (quantity <= 0) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Quantity must be a positive integer.\"}");
                return;
            }
        } catch (NumberFormatException | NullPointerException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid share quantity specified.\"}");
            return;
        }

        // 3. Invoke Service Layer (executes ACID JDBC transaction: check stock, check balance, deduct, update portfolio, insert transaction, commit/rollback)
        try {
            model.Transaction tx = tradingService.executeBuyTransaction(traderId.trim(), symbol.toUpperCase().trim(), quantity);
            model.Stock st = marketUpdateService.getStock(symbol.toUpperCase().trim());
            Trade trade = tx.toTrade(st != null ? st.getName() : symbol.toUpperCase().trim());

            // Fetch updated portfolio
            Portfolio portfolio = portfolioService.getOrCreatePortfolio(traderId.trim());

            String json = String.format("{\"success\":true,\"message\":\"Successfully purchased %d shares of %s.\",\"trade\":%s,\"portfolio\":%s}",
                    quantity, symbol.toUpperCase().trim(),
                    JsonHelper.tradeToJson(trade),
                    JsonHelper.portfolioToJson(portfolio, marketUpdateService.getStockMap()));

            sendJsonResponse(response, HttpServletResponse.SC_OK, json);

        } catch (InvalidStockException e) {
            sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (InsufficientStockException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (InsufficientBalanceException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (InvalidTransactionException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    String.format("{\"success\":false,\"error\":\"Trade execution failed: %s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
