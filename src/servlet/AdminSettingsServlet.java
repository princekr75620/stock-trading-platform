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
                String exName = p.get("exchangeName");
                if (exName == null || exName.trim().isEmpty()) {
                    throw new IllegalArgumentException("exchangeName cannot be empty.");
                }
                cur.setExchangeName(exName.trim());
            }
            if (p.containsKey("tradingFeePercent")) {
                double feePct = Double.parseDouble(p.get("tradingFeePercent"));
                if (!Double.isFinite(feePct) || feePct < 0 || feePct > 10.0) {
                    throw new IllegalArgumentException("tradingFeePercent must be between 0 and 10.");
                }
                cur.setTradingFeePercentage(feePct);
            }
            if (p.containsKey("marketUpdateIntervalSeconds")) {
                int interval = Integer.parseInt(p.get("marketUpdateIntervalSeconds"));
                if (interval < 1 || interval > 3600) {
                    throw new IllegalArgumentException("marketUpdateIntervalSeconds must be between 1 and 3600.");
                }
                cur.setMarketUpdateIntervalSeconds(interval);
            }
            if (p.containsKey("maintenanceMode")) {
                cur.setMaintenanceMode("true".equalsIgnoreCase(p.get("maintenanceMode")));
            }
            if (p.containsKey("tradingStatus")) {
                String rawStatus = p.get("tradingStatus");
                try {
                    cur.setMarketStatus(SystemSettings.MarketStatus.valueOf(rawStatus.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (Exception badStatus) {
                    throw new IllegalArgumentException("Unsupported tradingStatus '" + rawStatus + "'. Allowed values: OPEN, CLOSED, PRE_MARKET, AFTER_HOURS.");
                }
            }

            systemSettingsService.updateSettings(cur);
            Map<String, String> metrics = systemSettingsService.getSystemStatusMetrics();
            sendJsonResponse(response, HttpServletResponse.SC_OK,
                    "{\"success\":true,\"message\":\"System settings updated successfully\",\"settings\":" +
                            JsonHelper.systemSettingsToJson(cur, metrics) + "}");
        } catch (IllegalArgumentException ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"" + JsonHelper.escape(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            System.err.println("[AdminSettingsServlet] Error updating system settings: " + ex.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to update system settings.\"}");
        }
    }
}
