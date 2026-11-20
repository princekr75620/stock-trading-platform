package servlet;

import exception.AuthenticationThrottledException;
import exception.InvalidLoginException;
import filter.AuthenticationFilter;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Servlet handling User Authentication (Login).
 * Establishes an authenticated HttpSession with session-fixation rotation and CSRF token.
 * Strictly prevents account enumeration and never logs sensitive credentials, session IDs, or tokens.
 */
public class LoginServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private static final String GENERIC_LOGIN_ERROR = "Invalid username/email or password.";
    private static final String GENERIC_THROTTLE_ERROR = "Too many authentication requests. Please try again later.";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);
        String usernameOrEmail = body.get("usernameOrEmail");
        if (usernameOrEmail == null) usernameOrEmail = request.getParameter("usernameOrEmail");

        String password = body.get("password");
        if (password == null) password = request.getParameter("password");

        if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Username/Email and Password are required.\"}");
            return;
        }

        String clientIp = AuthenticationFilter.resolveClientIp(request);

        try {
            User user = authService.login(usernameOrEmail.trim(), password, clientIp);
            if (user != null) {
                setAuthenticatedUser(request, user);
                String csrfToken = getCsrfToken(request);

                String json = String.format("{\"success\":true,\"user\":%s,\"csrfToken\":\"%s\"}",
                        JsonHelper.userToJson(user),
                        JsonHelper.escape(csrfToken));
                sendJsonResponse(response, HttpServletResponse.SC_OK, json);
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        String.format("{\"success\":false,\"error\":\"%s\"}", GENERIC_LOGIN_ERROR));
            }
        } catch (AuthenticationThrottledException throttled) {
            System.err.println("[LoginServlet] Authentication temporarily throttled from peer IP=" + clientIp
                    + " retryAfterSec=" + throttled.getRetryAfterSeconds());
            response.setHeader("Retry-After", String.valueOf(throttled.getRetryAfterSeconds()));
            sendJsonResponse(response, 429,
                    String.format("{\"success\":false,\"error\":\"%s\"}", GENERIC_THROTTLE_ERROR));
        } catch (InvalidLoginException e) {
            // Never return internal exception text or account state to client (anti-enumeration)
            System.err.println("[LoginServlet] Authentication rejected from peer IP=" + clientIp);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    String.format("{\"success\":false,\"error\":\"%s\"}", GENERIC_LOGIN_ERROR));
        } catch (Exception e) {
            System.err.println("[LoginServlet] Unexpected authentication backend error ("
                    + e.getClass().getSimpleName() + ")");
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Authentication service is temporarily unavailable.\"}");
        }
    }
}
