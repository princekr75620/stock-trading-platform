package servlet;

import model.Admin;
import model.Trader;
import model.User;
import model.UserRole;
import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet handling User Management for Administrators.
 * Handles GET (view users), POST (create, update, delete users).
 * Fulfills UserManagementServlet requirement for GUVI Evaluation.
 */
public class UserManagementServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String query = request.getParameter("query");
        List<User> list;
        if (query != null && !query.trim().isEmpty()) {
            list = userService.searchUsers(query.trim());
        } else {
            list = userService.getAllUsers();
        }

        sendJsonResponse(response, HttpServletResponse.SC_OK, JsonHelper.usersToJson(list));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, String> body = parseJsonBody(request);
        String action = body.get("action");
        String uri = request.getRequestURI();

        // Check if action specified in path
        if (uri.endsWith("/update") || "update".equalsIgnoreCase(action)) {
            handleUpdateUser(body, response);
        } else if (uri.endsWith("/delete") || "delete".equalsIgnoreCase(action)) {
            handleDeleteUser(body, response);
        } else {
            handleCreateUser(body, response);
        }
    }

    private void handleCreateUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        String roleStr = body.get("role");
        String userId = body.get("userId");

        if (name == null || email == null || password == null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Name, email, and password are required.\"}");
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            userId = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        }

        UserRole role = "ADMIN".equalsIgnoreCase(roleStr) ? UserRole.ADMIN : UserRole.TRADER;
        double balance = 50000.0;
        try {
            if (body.containsKey("initialBalance")) {
                balance = Double.parseDouble(body.get("initialBalance"));
            }
        } catch (NumberFormatException ignored) {}

        User newUser;
        if (role == UserRole.ADMIN) {
            String dept = body.getOrDefault("department", "Administration");
            newUser = new Admin(userId, name, email, password, dept);
        } else {
            newUser = new Trader(userId, name, email, password, balance);
        }

        try {
            userService.createUser(newUser);
            String json = String.format("{\"success\":true,\"user\":%s}", JsonHelper.userToJson(newUser));
            sendJsonResponse(response, HttpServletResponse.SC_CREATED, json);
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }

    private void handleUpdateUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String userId = body.get("userId");
        if (userId == null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"success\":false,\"error\":\"userId is required.\"}");
            return;
        }

        try {
            userService.updateUserDetails(userId, body.get("name"), body.get("email"), body.get("password"));
            User updated = userService.getUserById(userId);
            String json = String.format("{\"success\":true,\"user\":%s}", JsonHelper.userToJson(updated));
            sendJsonResponse(response, HttpServletResponse.SC_OK, json);
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }

    private void handleDeleteUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String userId = body.get("userId");
        if (userId == null) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"success\":false,\"error\":\"userId is required.\"}");
            return;
        }

        try {
            userService.deleteUser(userId);
            sendJsonResponse(response, HttpServletResponse.SC_OK, "{\"success\":true,\"message\":\"User successfully deleted.\"}");
        } catch (Exception e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        }
    }
}
