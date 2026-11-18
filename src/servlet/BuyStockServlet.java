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
import java.util.Map;

/**
 * Servlet handling BUY Stock orders.
 * Workflow:
 * 1. Validate session user and input parameters (derives traderId strictly from session)
 * 2. Extract optional Idempotency-Key header / idempotencyKey body parameter
 * 3. Call TradingService to execute transactional Buy (ACID JDBC commit/rollback + BigDecimal fees)
 * 4. Return updated trade details and portfolio state
 */
public class BuyStockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);

        String symbol = body.get("symbol");
        if (symbol == null) symbol = request.getParameter("symbol");

        String qtyStr = body.get("quantity");
        if (qtyStr == null) qtyStr = request.getParameter("quantity");

        // Strictly derive authenticated traderId from server-side session
        User sessionUser = getAuthenticatedUser(request);
        if (sessionUser == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Please log in to execute trades.\"}");
            return;
        }

        String requestedTraderId = body.get("traderId");
        if (requestedTraderId == null) requestedTraderId = request.getParameter("traderId");
        if (requestedTraderId != null && !requestedTraderId.trim().isEmpty()
                && !sessionUser.getUserId().equalsIgnoreCase(requestedTraderId.trim())) {
            sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"success\":false,\"error\":\"Access denied: Cannot execute trades on another user's account.\"}");
            return;
        }
        String traderId = sessionUser.getUserId().trim();

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = body.get("idempotencyKey");
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

        String normalizedSymbol = symbol.toUpperCase().trim();
        try {
            model.Transaction tx = tradingService.executeBuyTransaction(traderId, normalizedSymbol, quantity, idempotencyKey);
            model.Stock st = marketUpdateService.getStock(normalizedSymbol);
            Trade trade = tx.toTrade(st != null ? st.getName() : normalizedSymbol);

            Portfolio portfolio = portfolioService.getOrCreatePortfolio(traderId);
            User freshUser = userService.getUserById(traderId);
            model.Trader freshTrader = (freshUser instanceof model.Trader) ? (model.Trader) freshUser : null;
            if (freshUser != null && sessionUser.getUserId().equalsIgnoreCase(freshUser.getUserId())) {
                javax.servlet.http.HttpSession session = request.getSession(false);
                if (session != null) {
                    session.setAttribute("user", freshUser);
                }
            }

            String json = String.format("{\"success\":true,\"message\":\"Successfully purchased %d shares of %s.\",\"trade\":%s,\"portfolio\":%s}",
                    quantity, normalizedSymbol,
                    JsonHelper.tradeToJson(trade),
                    JsonHelper.portfolioToJson(portfolio, freshTrader, marketUpdateService.getStockMap()));

            sendJsonResponse(response, HttpServletResponse.SC_OK, json);

        } catch (DuplicateOrderException e) {
            sendJsonResponse(response, HttpServletResponse.SC_CONFLICT,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
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
            System.err.println("[BuyStockServlet] Unexpected trade execution error: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Internal server error executing buy order.\"}");
        }
    }
}
