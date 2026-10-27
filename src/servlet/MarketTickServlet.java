package servlet;

import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet handling manual or automated Market Price Ticks.
 */
public class MarketTickServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            marketUpdateService.simulateMarketTick();
            sendJsonResponse(response, HttpServletResponse.SC_OK,
                    "{\"success\":true,\"message\":\"Market prices recalculated across Indian equities.\"}");
        } catch (Exception ex) {
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Market tick failed: " + JsonHelper.escape(ex.getMessage()) + "\"}");
        }
    }
}
