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
        sb.append("\"healthScore\":\"").append(securityService.getSecurityHealthScore()).append("\",");
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
                cur.setMinPasswordLength(Integer.parseInt(p.get("minimumPasswordLength")));
            }
            if (p.containsKey("requireSpecialCharacters")) {
                cur.setRequireSpecialChar("true".equalsIgnoreCase(p.get("requireSpecialCharacters")));
            }
            if (p.containsKey("passwordExpiryDays")) {
                cur.setPasswordExpiryDays(Integer.parseInt(p.get("passwordExpiryDays")));
            }
            if (p.containsKey("maxFailedLoginAttempts")) {
                cur.setMaxFailedLoginAttempts(Integer.parseInt(p.get("maxFailedLoginAttempts")));
            }
            if (p.containsKey("sessionTimeoutMinutes")) {
                cur.setSessionTimeoutMinutes(Integer.parseInt(p.get("sessionTimeoutMinutes")));
            }
            if (p.containsKey("autoLockoutEnabled")) {
                cur.setAutomaticAccountLockout("true".equalsIgnoreCase(p.get("autoLockoutEnabled")));
            }
            if (p.containsKey("highValueTradeThreshold")) {
                cur.setHighValueTradeThreshold(Double.parseDouble(p.get("highValueTradeThreshold")));
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
        } catch (Exception ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid security configuration: " + JsonHelper.escape(ex.getMessage()) + "\"}");
        }
    }
}
