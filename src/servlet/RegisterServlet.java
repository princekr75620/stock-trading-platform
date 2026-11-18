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
 * Servlet handling New User Registration.
 * Public registration ALWAYS enforces TRADER role and standard opening balance.
 */
public class RegisterServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);

        String name = body.get("name");
        if (name == null) name = request.getParameter("name");

        String email = body.get("email");
        if (email == null) email = request.getParameter("email");

        String password = body.get("password");
        if (password == null) password = request.getParameter("password");

        String userId = body.get("userId");
        if (userId == null) userId = request.getParameter("userId");

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Name, email, and password are required.\"}");
            return;
        }

        String trimmedEmail = email.trim();
        if (!trimmedEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Please provide a valid email address.\"}");
            return;
        }

        if (password.length() < 6) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Password must be at least 6 characters long.\"}");
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            userId = trimmedEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
            if (userId.isEmpty()) {
                userId = "trader_" + (System.currentTimeMillis() % 100000);
            }
        }

        // Public registration always creates a TRADER account with default initial balance.
        double defaultInitialBalance = 50000.0;
        User newUser = new Trader(userId.trim(), name.trim(), trimmedEmail, password, defaultInitialBalance);

        try {
            userService.createUser(newUser);
            setAuthenticatedUser(request, newUser);
            String csrfToken = getCsrfToken(request);
            String json = String.format("{\"success\":true,\"user\":%s,\"csrfToken\":\"%s\"}",
                    JsonHelper.userToJson(newUser),
                    JsonHelper.escape(csrfToken));
            sendJsonResponse(response, HttpServletResponse.SC_CREATED, json);
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
