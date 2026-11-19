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

        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Full name must be between 2 and 100 characters.\"}");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalizedEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Please provide a valid email address.\"}");
            return;
        }

        String strengthError = util.PasswordHasher.validatePasswordStrength(password);
        if (strengthError != null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(strengthError)));
            return;
        }

        String normalizedUid = (userId != null) ? userId.trim() : "";
        if (normalizedUid.isEmpty()) {
            normalizedUid = normalizedEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase(java.util.Locale.ROOT);
            if (normalizedUid.length() < 3) {
                normalizedUid = "trader_" + (System.currentTimeMillis() % 100000);
            }
        }
        if (!normalizedUid.matches("^[a-zA-Z0-9_-]{3,40}$")) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"userId must be 3-40 alphanumeric, underscore, or hyphen characters.\"}");
            return;
        }

        // Public registration always creates a TRADER account with default initial balance.
        double defaultInitialBalance = 50000.0;
        User newUser = new Trader(normalizedUid, trimmedName, normalizedEmail, password, defaultInitialBalance);

        try {
            userService.createUser(newUser);
            setAuthenticatedUser(request, newUser);
            String csrfToken = getCsrfToken(request);
            String json = String.format("{\"success\":true,\"user\":%s,\"csrfToken\":\"%s\"}",
                    JsonHelper.userToJson(newUser),
                    JsonHelper.escape(csrfToken));
            sendJsonResponse(response, HttpServletResponse.SC_CREATED, json);
        } catch (exception.DuplicateUserException dup) {
            sendJsonResponse(response, HttpServletResponse.SC_CONFLICT,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(dup.getMessage())));
        } catch (exception.InvalidInputException inv) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(inv.getMessage())));
        } catch (Exception e) {
            System.err.println("[RegisterServlet] Unexpected error: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to complete registration right now.\"}");
        }
    }
}
