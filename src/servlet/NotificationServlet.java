package servlet;

import model.Notification;
import model.User;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling authenticated user notifications and read-state updates.
 * Enforces session authentication and cross-tenant isolation via ensureOwnerOrAdmin.
 */
public class NotificationServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User sessionUser = getAuthenticatedUser(request);
        if (sessionUser == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required to view notifications.\"}");
            return;
        }

        String requestedUserId = request.getParameter("userId");
        if (requestedUserId == null || requestedUserId.trim().isEmpty()) {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/notifications/") && path.length() > "/api/notifications/".length()) {
                requestedUserId = path.substring("/api/notifications/".length());
            }
        }

        String targetUserId = (requestedUserId != null && !requestedUserId.trim().isEmpty())
                ? requestedUserId.trim()
                : sessionUser.getUserId();

        if (!ensureOwnerOrAdmin(request, response, targetUserId)) {
            return;
        }

        List<Notification> notifications = notificationService.getNotificationsForUser(targetUserId);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < notifications.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonHelper.notificationToJson(notifications.get(i)));
        }
        sb.append("]");
        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User sessionUser = getAuthenticatedUser(request);
        if (sessionUser == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"error\":\"Authentication required.\"}");
            return;
        }

        Map<String, String> body = parseJsonBody(request);
        String requestedUserId = body.get("userId");
        if (requestedUserId == null || requestedUserId.trim().isEmpty()) {
            requestedUserId = request.getParameter("userId");
        }

        String targetUserId = (requestedUserId != null && !requestedUserId.trim().isEmpty())
                ? requestedUserId.trim()
                : sessionUser.getUserId();

        if (!ensureOwnerOrAdmin(request, response, targetUserId)) {
            return;
        }

        notificationService.markAllAsRead(targetUserId);
        sendJsonResponse(response, HttpServletResponse.SC_OK, "{\"success\":true}");
    }
}
