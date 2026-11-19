package servlet;

import model.Stock;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling adding new equity stocks to the exchange.
 * Fulfills AddStockServlet requirement for GUVI Evaluation.
 */
public class AddStockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!ensureAdmin(request, response)) {
            return;
        }

        Map<String, String> body = parseJsonBody(request);

        String symbol = body.get("symbol");
        String name = body.get("companyName");
        if (name == null) name = body.get("name");

        String priceStr = body.get("price");
        String qtyStr = body.get("availableQuantity");

        if (symbol == null || symbol.trim().isEmpty() ||
            name == null || name.trim().isEmpty() ||
            priceStr == null || qtyStr == null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Symbol, company name, price, and available quantity are required.\"}");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);
            int quantity = Integer.parseInt(qtyStr);

            if (!Double.isFinite(price) || price <= 0 || quantity < 0) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Price must be finite positive and quantity must be non-negative.\"}");
                return;
            }

            String sym = symbol.toUpperCase().trim();
            if (!sym.matches("^[A-Z0-9._-]{1,20}$")) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Ticker symbol must be 1-20 valid characters.\"}");
                return;
            }
            if (marketUpdateService.getStock(sym) != null) {
                sendJsonResponse(response, HttpServletResponse.SC_CONFLICT,
                        "{\"success\":false,\"error\":\"Stock with symbol " + JsonHelper.escape(sym) + " already exists.\"}");
                return;
            }

            Stock newStock = new Stock(sym, name.trim(), price, quantity);
            marketUpdateService.addStock(newStock);

            String json = String.format("{\"success\":true,\"message\":\"Stock %s added successfully.\",\"stock\":%s}",
                    sym, JsonHelper.stockToJson(newStock));
            sendJsonResponse(response, HttpServletResponse.SC_CREATED, json);

        } catch (NumberFormatException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid numeric value for price or quantity.\"}");
        } catch (Exception e) {
            System.err.println("[AddStockServlet] Unexpected error: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Internal server error adding stock.\"}");
        }
    }
}
