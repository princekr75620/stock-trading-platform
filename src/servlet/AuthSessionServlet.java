package servlet;

import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet providing the currently authenticated user identity, role, and CSRF token
 * directly from the server-side HttpSession context and authoritative database record.
 */
public class AuthSessionServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User sessionUser = getAuthenticatedUser(request);
        if (sessionUser == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"authenticated\":false,\"user\":null,\"message\":\"No active server session found.\"}");
            return;
        }

        User freshUser = sessionUser;
        if (userService != null) {
            try {
                freshUser = userService.getUserById(sessionUser.getUserId());
                javax.servlet.http.HttpSession session = request.getSession(false);
                if (session != null && freshUser != null) {
                    session.setAttribute("user", freshUser);
                    session.setAttribute("role", freshUser.getRole().name());
                }
            } catch (Exception e) {
                clearAuthenticatedUser(request);
                sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"authenticated\":false,\"user\":null,\"message\":\"Session user no longer active.\"}");
                return;
            }
        }

        String csrfToken = getCsrfToken(request);
        String json = String.format("{\"authenticated\":true,\"user\":%s,\"csrfToken\":\"%s\"}",
                JsonHelper.userToJson(freshUser),
                JsonHelper.escape(csrfToken));
        sendJsonResponse(response, HttpServletResponse.SC_OK, json);
    }
}
