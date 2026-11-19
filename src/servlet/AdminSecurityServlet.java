package servlet;

import model.SecurityIncident;
import model.SecuritySettings;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling Admin Financial Security controls and incidents.
 * Strictly enforces server-side session and ADMIN role verification via ensureAdmin.
 */
public class AdminSecurityServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }

        SecuritySettings sec = securityService.getSecuritySettings();
        List<SecurityIncident> incidents = securityService.getAllIncidents();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"settings\":").append(JsonHelper.securitySettingsToJson(sec)).append(",");
        sb.append("\"healthScore\":\"").append(JsonHelper.escape(securityService.getSecurityHealthScore())).append("\",");
        sb.append("\"openIncidentsCount\":").append(securityService.getOpenIncidentCount()).append(",");
        sb.append("\"incidents\":[");
        for (int i = 0; i < incidents.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonHelper.securityIncidentToJson(incidents.get(i)));
        }
        sb.append("]}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }

        Map<String, String> p = parseJsonBody(request);
        SecuritySettings cur = securityService.getSecuritySettings();

        try {
            if (p.containsKey("minimumPasswordLength")) {
                int minLen = Integer.parseInt(p.get("minimumPasswordLength"));
                if (minLen < 8 || minLen > 128) throw new IllegalArgumentException("minimumPasswordLength must be 8-128.");
                cur.setMinPasswordLength(minLen);
            }
            if (p.containsKey("requireSpecialCharacters")) {
                cur.setRequireSpecialChar("true".equalsIgnoreCase(p.get("requireSpecialCharacters")));
            }
            if (p.containsKey("passwordExpiryDays")) {
                int expiry = Integer.parseInt(p.get("passwordExpiryDays"));
                if (expiry < 1 || expiry > 3650) throw new IllegalArgumentException("passwordExpiryDays must be 1-3650.");
                cur.setPasswordExpiryDays(expiry);
            }
            if (p.containsKey("maxFailedLoginAttempts")) {
                int maxAttempts = Integer.parseInt(p.get("maxFailedLoginAttempts"));
                if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("maxFailedLoginAttempts must be 1-20.");
                cur.setMaxFailedLoginAttempts(maxAttempts);
            }
            if (p.containsKey("sessionTimeoutMinutes")) {
                int timeoutMin = Integer.parseInt(p.get("sessionTimeoutMinutes"));
                if (timeoutMin < 5 || timeoutMin > 1440) throw new IllegalArgumentException("sessionTimeoutMinutes must be 5-1440.");
                cur.setSessionTimeoutMinutes(timeoutMin);
            }
            if (p.containsKey("autoLockoutEnabled")) {
                cur.setAutomaticAccountLockout("true".equalsIgnoreCase(p.get("autoLockoutEnabled")));
            }
            if (p.containsKey("highValueTradeThreshold")) {
                double threshold = Double.parseDouble(p.get("highValueTradeThreshold"));
                if (!Double.isFinite(threshold) || threshold < 0) throw new IllegalArgumentException("highValueTradeThreshold must be non-negative.");
                cur.setHighValueTradeThreshold(threshold);
            }
            if (p.containsKey("requireTwoStepForHighValue")) {
                cur.setRequireTwoStepForHighValueTrades("true".equalsIgnoreCase(p.get("requireTwoStepForHighValue")));
            }
            if (p.containsKey("ipAnomalyDetectionEnabled")) {
                cur.setIpAnomalyDetection("true".equalsIgnoreCase(p.get("ipAnomalyDetectionEnabled")));
            }

            securityService.updateSecuritySettings(cur);
            sendJsonResponse(response, HttpServletResponse.SC_OK,
                    "{\"success\":true,\"message\":\"Security settings updated successfully\",\"settings\":" +
                            JsonHelper.securitySettingsToJson(cur) + "}");
        } catch (IllegalArgumentException ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"" + JsonHelper.escape(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            System.err.println("[AdminSecurityServlet] Error updating security configuration: " + ex.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to update security configuration.\"}");
        }
    }
}
