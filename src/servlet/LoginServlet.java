package servlet;

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
 */
public class LoginServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

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

        try {
            User user = authService.login(usernameOrEmail.trim(), password);
            if (user != null) {
                setAuthenticatedUser(request, user);
                String csrfToken = getCsrfToken(request);

                String json = String.format("{\"success\":true,\"user\":%s,\"csrfToken\":\"%s\"}",
                        JsonHelper.userToJson(user),
                        JsonHelper.escape(csrfToken));
                sendJsonResponse(response, HttpServletResponse.SC_OK, json);
            } else {
                sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"success\":false,\"error\":\"Invalid username/email or password.\"}");
            }
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
