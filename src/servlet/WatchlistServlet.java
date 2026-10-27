package servlet;

import dao.WatchlistDAO;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Trader Stock Watchlist management.
 * Provides GET /api/watchlist and POST /api/watchlist (toggle, add, remove).
 * Queries real relational database via WatchlistDAO using PreparedStatements.
 */
public class WatchlistServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final WatchlistDAO watchlistDAO = new WatchlistDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = getAuthenticatedUser(request);
        String targetUserId = (user != null) ? user.getUserId() : request.getParameter("userId");

        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Please log in to view your watchlist.\"}");
            return;
        }

        try {
            List<String> list = watchlistDAO.getWatchlist(targetUserId.trim());
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"watchlist\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(JsonHelper.escape(list.get(i))).append("\"");
            }
            sb.append("]}");
            sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
        } catch (SQLException e) {
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    String.format("{\"success\":false,\"error\":\"Database error: %s\"}", JsonHelper.escape(e.getMessage())));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = getAuthenticatedUser(request);
        Map<String, String> body = parseJsonBody(request);

        String targetUserId = (user != null) ? user.getUserId() : body.get("userId");
        if (targetUserId == null) targetUserId = request.getParameter("userId");

        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Please log in to manage your watchlist.\"}");
            return;
        }

        String symbol = body.get("symbol");
        if (symbol == null) symbol = request.getParameter("symbol");

        if (symbol == null || symbol.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Stock symbol is required.\"}");
            return;
        }

        String action = body.getOrDefault("action", "toggle");
        String sym = symbol.toUpperCase().trim();

        try {
            boolean inList = watchlistDAO.isInWatchlist(targetUserId, sym);
            boolean updated;
            if ("add".equalsIgnoreCase(action) || (!inList && "toggle".equalsIgnoreCase(action))) {
                updated = watchlistDAO.addToWatchlist(targetUserId, sym);
            } else {
                updated = watchlistDAO.removeFromWatchlist(targetUserId, sym);
            }

            List<String> list = watchlistDAO.getWatchlist(targetUserId);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"updated\":").append(updated).append(",\"watchlist\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(JsonHelper.escape(list.get(i))).append("\"");
            }
            sb.append("]}");
            sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
        } catch (SQLException e) {
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    String.format("{\"success\":false,\"error\":\"Database error: %s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
