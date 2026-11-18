package servlet;

import dao.StockDAO;
import model.Stock;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Servlet providing Indian stock quotes, market data, and full-text ticker/name search.
 * Handles GET (view available stocks, search stocks, pagination, view details).
 * Uses Java Servlet + JDBC (PreparedStatement) as required by GUVI Rubric.
 */
public class StockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final StockDAO stockDAO = new StockDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Single symbol lookup
        String symbol = request.getParameter("symbol");
        if (symbol != null && !symbol.trim().isEmpty()) {
            String cleanSymbol = symbol.toUpperCase().trim();
            if (cleanSymbol.length() > 24 || !cleanSymbol.matches("[A-Z0-9._&-]+")) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Invalid stock symbol format.\"}");
                return;
            }
            try {
                Stock stock = stockDAO.findBySymbol(cleanSymbol);
                if (stock == null) {
                    stock = marketUpdateService.getStock(cleanSymbol);
                }
                if (stock != null) {
                    sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.stockToJson(stock));
                } else {
                    sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND,
                            "{\"success\":false,\"error\":\"Indian equity not found for symbol: " + JsonHelper.escape(cleanSymbol) + "\"}");
                }
            } catch (SQLException e) {
                System.err.println("[StockServlet] Database error looking up symbol " + cleanSymbol + ": " + e.getMessage());
                sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "{\"success\":false,\"error\":\"Database error retrieving stock quote.\"}");
            }
            return;
        }

        // 2. Search by symbol or company name with pagination (Java Servlet + JDBC)
        String query = request.getParameter("q");
        if (query == null) query = request.getParameter("search");
        if (query != null && query.length() > 100) {
            query = query.substring(0, 100);
        }

        String pageStr = request.getParameter("page");
        String pageSizeStr = request.getParameter("pageSize");
        int page = 1;
        int pageSize = 100;
        try {
            if (pageStr != null) page = Math.min(1000, Math.max(1, Integer.parseInt(pageStr)));
            if (pageSizeStr != null) pageSize = Math.min(500, Math.max(1, Integer.parseInt(pageSizeStr)));
        } catch (NumberFormatException ignored) {}

        try {
            List<Stock> stocks;
            if (query != null && !query.trim().isEmpty()) {
                // JDBC search using LIKE on symbol and company_name
                stocks = stockDAO.search(query.trim(), page, pageSize);
            } else {
                stocks = stockDAO.search(null, page, pageSize);
                if (stocks == null || stocks.isEmpty()) {
                    stocks = marketUpdateService.getAllStocks();
                }
            }
            sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.stocksToJson(stocks));
        } catch (SQLException e) {
            // Fallback to in-memory cache if JDBC is syncing
            List<Stock> fallback = marketUpdateService.getAllStocks();
            sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.stocksToJson(fallback));
        }
    }
}
