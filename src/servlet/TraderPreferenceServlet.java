package servlet;

import model.Trader;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling Trader preferences and cash deposits.
 * Enforces authenticated session ownership so traders cannot deposit or alter other users' preferences.
 */
public class TraderPreferenceServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Map<String, String> p = parseJsonBody(request);

        User sessionUser = getAuthenticatedUser(request);
        if (sessionUser == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in.\"}");
            return;
        }

        String targetTraderId = p.get("traderId");
        if (targetTraderId == null || targetTraderId.trim().isEmpty()) {
            targetTraderId = sessionUser.getUserId();
        }

        // Enforce that trader can only deposit or modify preferences on their own account
        if (!ensureOwnerOrAdmin(request, response, targetTraderId)) {
            return;
        }

        if (path.endsWith("/deposit")) {
            handleDeposit(request, response, targetTraderId, p);
        } else if (path.endsWith("/preferences")) {
            handlePreferences(request, response, targetTraderId, p);
        } else {
            sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND,
                    "{\"success\":false,\"error\":\"Endpoint not found.\"}");
        }
    }

    private void handleDeposit(HttpServletRequest request, HttpServletResponse response,
                               String traderId, Map<String, String> p) throws IOException {
        String amtStr = p.get("amount");
        if (amtStr == null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Deposit amount is required.\"}");
            return;
        }

        try {
            double amt = Double.parseDouble(amtStr);
            if (amt <= 0) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Deposit amount must be positive.\"}");
                return;
            }

            User u = userService.getUserById(traderId);
            if (u instanceof Trader) {
                Trader t = (Trader) u;
                t.deposit(amt);
                userService.updateUser(t);
                sendJsonResponse(response, HttpServletResponse.SC_OK,
                        "{\"success\":true,\"cashBalance\":" + t.getCashBalance() +
                                ",\"message\":\"Successfully deposited ₹" + String.format("%.2f", amt) + "\"}");
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Target user is not a trader.\"}");
            }
        } catch (Exception ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Deposit failed: " + JsonHelper.escape(ex.getMessage()) + "\"}");
        }
    }

    private void handlePreferences(HttpServletRequest request, HttpServletResponse response,
                                  String traderId, Map<String, String> p) throws IOException {
        try {
            User u = userService.getUserById(traderId);
            if (u instanceof Trader) {
                Trader t = (Trader) u;
                if (p.containsKey("emailAlertsEnabled")) {
                    t.setEmailAlertsEnabled("true".equalsIgnoreCase(p.get("emailAlertsEnabled")));
                }
                if (p.containsKey("alertThresholdPercent")) {
                    t.setAlertThresholdPercent(Double.parseDouble(p.get("alertThresholdPercent")));
                }
                userService.updateUser(t);
                sendJsonResponse(response, HttpServletResponse.SC_OK,
                        "{\"success\":true,\"message\":\"Preferences updated successfully.\"}");
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Target user is not a trader.\"}");
            }
        } catch (Exception ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Preferences update failed: " + JsonHelper.escape(ex.getMessage()) + "\"}");
        }
    }
}
