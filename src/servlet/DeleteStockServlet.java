package servlet;

import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling delisting/deleting stocks from the exchange.
 * Fulfills DeleteStockServlet requirement for GUVI Evaluation.
 */
public class DeleteStockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);
        String symbol = body.get("symbol");
        if (symbol == null) symbol = request.getParameter("symbol");

        if (symbol == null || symbol.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Stock symbol is required.\"}");
            return;
        }

        String sym = symbol.toUpperCase().trim();
        boolean deleted = marketUpdateService.deleteStock(sym);

        if (deleted) {
            sendJsonResponse(response, HttpServletResponse.SC_OK,
                    "{\"success\":true,\"message\":\"Stock " + sym + " successfully deleted from exchange.\"}");
        } else {
            sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND,
                    "{\"success\":false,\"error\":\"Stock " + sym + " was not found.\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }
}
