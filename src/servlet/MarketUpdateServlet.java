package servlet;

import model.MarketUpdate;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Servlet serving live simulated market updates and price alert feed entries.
 */
public class MarketUpdateServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<MarketUpdate> updates = marketUpdateService.getAllMarketUpdates();
        int limit = Math.min(30, updates.size());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonHelper.marketUpdateToJson(updates.get(i)));
        }
        sb.append("]");
        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
