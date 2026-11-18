package servlet;

import model.User;
import model.UserRole;
import server.JsonHelper;
import service.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Abstract Base Servlet providing common utilities for Request/Response handling,
 * Session management, cryptographic CSRF token generation, JSON processing, and RBAC helpers.
 */
public abstract class BaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom CSRF_RANDOM = new SecureRandom();

    protected static IUserService userService;
    protected static IMarketUpdateService marketUpdateService;
    protected static IPortfolioService portfolioService;
    protected static INotificationService notificationService;
    protected static TradingService tradingService;
    protected static ISecurityService securityService;
    protected static ISystemSettingsService systemSettingsService;
    protected static IReportService reportService;
    protected static IAuthenticationService authService;

    public static void initializeServices(
            IUserService uService,
            IMarketUpdateService mService,
            IPortfolioService pService,
            INotificationService nService,
            TradingService tService,
            ISecurityService sService,
            ISystemSettingsService sysService,
            IReportService rService,
            IAuthenticationService aService) {
        userService = uService;
        marketUpdateService = mService;
        portfolioService = pService;
        notificationService = nService;
        tradingService = tService;
        securityService = sService;
        systemSettingsService = sysService;
        reportService = rService;
        authService = aService;
    }

    private static String generateCsrfToken() {
        byte[] bytes = new byte[32];
        CSRF_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    protected String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    protected Map<String, String> parseJsonBody(HttpServletRequest request) throws IOException {
        String body = readRequestBody(request);
        return JsonHelper.parseSimpleJson(body);
    }

    protected void sendJsonResponse(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
        response.getWriter().flush();
    }

    protected User getAuthenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object u = session.getAttribute("user");
            if (u instanceof User) {
                return (User) u;
            }
        }
        return null;
    }

    protected String getCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "";
        Object token = session.getAttribute("csrfToken");
        if (token instanceof String && !((String) token).isEmpty()) {
            return (String) token;
        }
        String fresh = generateCsrfToken();
        session.setAttribute("csrfToken", fresh);
        return fresh;
    }

    /**
     * Stores user into session after successful login with session fixation protection
     * and generates a cryptographically random CSRF token bound to the new session.
     */
    protected void setAuthenticatedUser(HttpServletRequest request, User user) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("user", user);
        newSession.setAttribute("userId", user.getUserId());
        newSession.setAttribute("role", user.getRole().name());
        newSession.setAttribute("csrfToken", generateCsrfToken());
    }

    protected void clearAuthenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    protected boolean ensureAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in.\"}");
            return false;
        }
        if (user.getRole() != UserRole.ADMIN) {
            sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"success\":false,\"error\":\"Access denied: Administrator privileges required.\"}");
            return false;
        }
        return true;
    }

    protected boolean ensureOwnerOrAdmin(HttpServletRequest request, HttpServletResponse response, String targetUserId)
            throws IOException {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required. Please log in.\"}");
            return false;
        }
        if (user.getRole() != UserRole.ADMIN && !user.getUserId().equalsIgnoreCase(targetUserId)) {
            sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"success\":false,\"error\":\"Access denied: Cannot access another user's account or portfolio.\"}");
            return false;
        }
        return true;
    }
}
