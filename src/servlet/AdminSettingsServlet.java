package servlet;

import model.SystemSettings;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling System Settings configuration for Administrators.
 * Strictly enforces server-side session and ADMIN role verification via ensureAdmin.
 */
public class AdminSettingsServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }

        SystemSettings sys = systemSettingsService.getSettings();
        Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
        sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.systemSettingsToJson(sys, metrics));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }

        Map<String, String> p = parseJsonBody(request);
        SystemSettings cur = systemSettingsService.getSettings();

        try {
            if (p.containsKey("exchangeName")) {
                cur.setExchangeName(p.get("exchangeName"));
            }
            if (p.containsKey("tradingFeePercent")) {
                cur.setTradingFeePercentage(Double.parseDouble(p.get("tradingFeePercent")));
            }
            if (p.containsKey("marketUpdateIntervalSeconds")) {
                cur.setMarketUpdateIntervalSeconds(Integer.parseInt(p.get("marketUpdateIntervalSeconds")));
            }
            if (p.containsKey("maintenanceMode")) {
                cur.setMaintenanceMode("true".equalsIgnoreCase(p.get("maintenanceMode")));
            }
            if (p.containsKey("tradingStatus")) {
                try {
                    cur.setMarketStatus(SystemSettings.MarketStatus.valueOf(p.get("tradingStatus").toUpperCase()));
                } catch (Exception ignored) {}
            }

            systemSettingsService.updateSettings(cur);
            Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
            sendJsonResponse(response, HttpServletResponse.SC_OK,
                    "{\"success\":true,\"message\":\"System settings updated successfully\",\"settings\":" +
                            JsonHelper.systemSettingsToJson(cur, metrics) + "}");
        } catch (Exception ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid system settings: " + JsonHelper.escape(ex.getMessage()) + "\"}");
        }
    }
}
