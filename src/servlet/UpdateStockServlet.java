package servlet;

import model.Stock;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling updating existing stock quotes and share float.
 * Fulfills UpdateStockServlet requirement for GUVI Evaluation.
 */
public class UpdateStockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);

        String symbol = body.get("symbol");
        if (symbol == null || symbol.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Stock symbol is required.\"}");
            return;
        }

        String sym = symbol.toUpperCase().trim();
        Stock existing = marketUpdateService.getStock(sym);
        if (existing == null) {
            sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND,
                    "{\"success\":false,\"error\":\"Stock not found: " + sym + "\"}");
            return;
        }

        try {
            if (body.containsKey("companyName")) {
                existing.setName(body.get("companyName"));
            } else if (body.containsKey("name")) {
                existing.setName(body.get("name"));
            }

            if (body.containsKey("price")) {
                double price = Double.parseDouble(body.get("price"));
                if (price > 0) existing.updatePrice(price);
            }

            if (body.containsKey("availableQuantity")) {
                int qty = Integer.parseInt(body.get("availableQuantity"));
                if (qty >= 0) existing.setAvailableQuantity(qty);
            }

            marketUpdateService.updateStock(existing);

            String json = String.format("{\"success\":true,\"message\":\"Stock %s updated successfully.\",\"stock\":%s}",
                    sym, JsonHelper.stockToJson(existing));
            sendJsonResponse(response, HttpServletResponse.SC_OK, json);

        } catch (NumberFormatException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid numeric value provided.\"}");
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
