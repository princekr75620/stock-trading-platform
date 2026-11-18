package servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet handling User Logout.
 * Strictly requires POST (rejects GET with 405 Method Not Allowed) and invalidates HttpSession.
 */
public class LogoutServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        clearAuthenticatedUser(request);
        sendJsonResponse(response, HttpServletResponse.SC_OK, "{\"success\":true,\"message\":\"Successfully logged out.\"}");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Allow", "POST");
        sendJsonResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "{\"success\":false,\"error\":\"Method Not Allowed. Use POST /api/auth/logout.\"}");
    }
}
