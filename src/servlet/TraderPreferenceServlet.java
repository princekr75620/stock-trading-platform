package servlet;

import model.Trader;
import model.User;
import server.JsonHelper;
import util.MoneyUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Servlet handling Trader preferences and cash deposits.
 * Enforces authenticated session ownership, BigDecimal monetary bounds, and atomic JDBC updates.
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
        } else {
            targetTraderId = targetTraderId.trim();
        }

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
        if (amtStr == null || amtStr.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Deposit amount is required.\"}");
            return;
        }

        try {
            BigDecimal depositAmt = new BigDecimal(amtStr.trim()).setScale(MoneyUtil.CURRENCY_SCALE, MoneyUtil.ROUNDING);
            if (depositAmt.compareTo(BigDecimal.ZERO) <= 0 || depositAmt.compareTo(MoneyUtil.MAX_MONETARY_AMOUNT) > 0) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Deposit amount must be positive and within platform limits.\"}");
                return;
            }

            User u = userService.getUserById(traderId);
            if (u instanceof Trader) {
                Trader t = (Trader) u;
                BigDecimal newBalance = MoneyUtil.of(t.getCashBalance()).add(depositAmt);
                if (newBalance.compareTo(MoneyUtil.MAX_MONETARY_AMOUNT) > 0) {
                    sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                            "{\"success\":false,\"error\":\"Resulting cash balance exceeds platform safety limit.\"}");
                    return;
                }
                t.setBalance(newBalance.doubleValue());
                userService.updateUser(t);
                User sessionUser = getAuthenticatedUser(request);
                if (sessionUser != null && sessionUser.getUserId().equalsIgnoreCase(t.getUserId())) {
                    javax.servlet.http.HttpSession session = request.getSession(false);
                    if (session != null) session.setAttribute("user", t);
                }
                sendJsonResponse(response, HttpServletResponse.SC_OK,
                        "{\"success\":true,\"cashBalance\":" + String.format(java.util.Locale.US, "%.2f", newBalance) +
                                ",\"message\":\"Successfully deposited ₹" + String.format(java.util.Locale.US, "%.2f", depositAmt) + "\"}");
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Target user is not a trader.\"}");
            }
        } catch (NumberFormatException | ArithmeticException ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid deposit amount format.\"}");
        } catch (Exception ex) {
            System.err.println("[TraderPreferenceServlet] Deposit error: " + ex.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Internal server error processing deposit.\"}");
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
                    double threshold = Double.parseDouble(p.get("alertThresholdPercent"));
                    if (!Double.isFinite(threshold) || threshold < 0 || threshold > 100.0) {
                        sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                                "{\"success\":false,\"error\":\"Alert threshold must be between 0 and 100.\"}");
                        return;
                    }
                    t.setAlertThresholdPercent(threshold);
                }
                userService.updateUser(t);
                User sessionUser = getAuthenticatedUser(request);
                if (sessionUser != null && sessionUser.getUserId().equalsIgnoreCase(t.getUserId())) {
                    javax.servlet.http.HttpSession session = request.getSession(false);
                    if (session != null) session.setAttribute("user", t);
                }
                sendJsonResponse(response, HttpServletResponse.SC_OK,
                        "{\"success\":true,\"message\":\"Preferences updated successfully.\"}");
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Target user is not a trader.\"}");
            }
        } catch (NumberFormatException ex) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Invalid numeric preference value.\"}");
        } catch (Exception ex) {
            System.err.println("[TraderPreferenceServlet] Preferences error: " + ex.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Internal server error updating preferences.\"}");
        }
    }
}
