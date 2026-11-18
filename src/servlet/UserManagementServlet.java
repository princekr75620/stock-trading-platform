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

        if (!ensureAdmin(request, response)) {
            return;
        }

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

        if (!ensureAdmin(request, response)) {
            return;
        }

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

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }
        Map<String, String> body = parseJsonBody(request);
        handleUpdateUser(body, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }
        Map<String, String> body = parseJsonBody(request);
        if (!body.containsKey("userId") && request.getParameter("userId") != null) {
            body.put("userId", request.getParameter("userId"));
        }
        handleDeleteUser(body, response);
    }

    private void handleCreateUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String name = body.get("name") != null ? body.get("name").trim() : null;
        String email = body.get("email") != null ? body.get("email").trim() : null;
        String password = body.get("password");
        String roleStr = body.get("role");
        String userId = body.get("userId") != null ? body.get("userId").trim() : null;

        if (name == null || name.isEmpty() || email == null || !email.contains("@") || !email.contains(".")
                || password == null || password.length() < 6) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Valid name, email, and password (min 6 chars) are required.\"}");
            return;
        }

        if (userId == null || userId.isEmpty()) {
            userId = email.split("@")[0].replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
        }
        if (!userId.matches("[a-zA-Z0-9_-]{3,40}")) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"userId must be 3-40 alphanumeric characters.\"}");
            return;
        }

        UserRole role = "ADMIN".equalsIgnoreCase(roleStr) ? UserRole.ADMIN : UserRole.TRADER;
        double balance = 50000.0;
        if (body.containsKey("initialBalance") && body.get("initialBalance") != null && !body.get("initialBalance").trim().isEmpty()) {
            try {
                balance = Double.parseDouble(body.get("initialBalance").trim());
                if (!Double.isFinite(balance) || balance < 0 || balance > 1_000_000_000.0) {
                    sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                            "{\"success\":false,\"error\":\"initialBalance must be between 0 and 1,000,000,000.\"}");
                    return;
                }
            } catch (NumberFormatException e) {
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        "{\"success\":false,\"error\":\"Invalid initialBalance format.\"}");
                return;
            }
        }

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
        } catch (exception.DuplicateUserException | exception.InvalidInputException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (Exception e) {
            System.err.println("[UserManagementServlet] Error creating user: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to create user due to a server error.\"}");
        }
    }

    private void handleUpdateUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String userId = body.get("userId");
        if (userId == null || userId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"success\":false,\"error\":\"userId is required.\"}");
            return;
        }
        String newPassword = body.get("password");
        if (newPassword != null && !newPassword.isEmpty() && newPassword.length() < 6) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"error\":\"Updated password must be at least 6 characters.\"}");
            return;
        }

        try {
            userService.updateUserDetails(userId.trim(), body.get("name"), body.get("email"), newPassword);
            User updated = userService.getUserById(userId.trim());
            String json = String.format("{\"success\":true,\"user\":%s}", JsonHelper.userToJson(updated));
            sendJsonResponse(response, HttpServletResponse.SC_OK, json);
        } catch (exception.InvalidInputException | IllegalArgumentException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (Exception e) {
            System.err.println("[UserManagementServlet] Error updating user: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to update user.\"}");
        }
    }

    private void handleDeleteUser(Map<String, String> body, HttpServletResponse response) throws IOException {
        String userId = body.get("userId");
        if (userId == null || userId.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"success\":false,\"error\":\"userId is required.\"}");
            return;
        }

        try {
            userService.deleteUser(userId.trim());
            sendJsonResponse(response, HttpServletResponse.SC_OK, "{\"success\":true,\"message\":\"User successfully deleted.\"}");
        } catch (IllegalArgumentException e) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(e.getMessage())));
        } catch (Exception e) {
            System.err.println("[UserManagementServlet] Error deleting user: " + e.getMessage());
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"success\":false,\"error\":\"Unable to delete user.\"}");
        }
    }
}
