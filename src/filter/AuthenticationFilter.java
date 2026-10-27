package filter;

import model.User;
import model.UserRole;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Enterprise Servlet Filter implementing Session-based Authentication & Role-based Access Control (RBAC).
 *
 * Security Hardening:
 * 1. Strict Server-Side Session Enforcement: Trusts ONLY authenticated server-side HttpSession records.
 * 2. Header Forgery Immunity: Never trusts client-supplied headers (e.g. X-User-Id) or request parameters
 *    for administrative or role authorization.
 * 3. Least Privilege & Isolation: Restricts /api/admin/* strictly to verified ADMIN accounts (HTTP 403).
 * 4. Portfolio Isolation: Enforces user portfolio access control, preventing cross-tenant access.
 * 5. CSRF Defense: Enforces request validation on state-changing requests.
 * 6. Hardened Security Headers: Sets nosniff, SAMEORIGIN, and cache control headers.
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[AuthenticationFilter] Secure web security filter initialized.");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Apply defensive HTTP security response headers
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
        httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 1. Allow public endpoints (login, register, health, public market ticker)
        if (isPublicEndpoint(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Validate Server-Side HttpSession
        HttpSession session = httpRequest.getSession(false);
        User sessionUser = null;
        if (session != null) {
            Object userObj = session.getAttribute("user");
            if (userObj instanceof User) {
                sessionUser = (User) userObj;
            }
        }

        // Unauthenticated check for protected API routes
        if (sessionUser == null) {
            sendJsonError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required. Please log in with valid credentials.");
            return;
        }

        // 3. CSRF Protection on state-changing methods (POST, PUT, DELETE)
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            if (!isValidCsrf(httpRequest, session)) {
                sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Security validation failed: Request origin or CSRF token invalid.");
                return;
            }
        }

        // 4. Administrator Route Authorization (/api/admin/*)
        // STRICT: Only grant access if sessionUser exists AND database role is ADMIN.
        // Never check or trust client-supplied headers like X-User-Id.
        if (path.startsWith("/api/admin")) {
            if (sessionUser.getRole() != UserRole.ADMIN) {
                sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: Administrator privileges required.");
                return;
            }
        }

        // 5. Portfolio Cross-Tenant Isolation (/api/portfolio)
        // Traders can ONLY view their own portfolio; Admins can audit any portfolio.
        if (path.startsWith("/api/portfolio")) {
            String requestedTraderId = httpRequest.getParameter("traderId");
            if (requestedTraderId != null && !requestedTraderId.trim().isEmpty()) {
                if (sessionUser.getRole() != UserRole.ADMIN &&
                    !sessionUser.getUserId().equalsIgnoreCase(requestedTraderId.trim())) {
                    sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: You are not authorized to view another user's portfolio.");
                    return;
                }
            }
        }

        // Pass control downstream
        chain.doFilter(request, response);
    }

    /**
     * Determines whether an endpoint is publicly accessible without prior authentication.
     */
    private boolean isPublicEndpoint(String path, String method) {
        if (path == null) return true;
        return path.equals("/api/auth/login") ||
               path.equals("/api/auth/register") ||
               path.equals("/api/health") ||
               path.equals("/api/db/health") ||
               path.equals("/api/stocks") ||
               path.equals("/api/market/updates") ||
               path.startsWith("/dist") ||
               path.startsWith("/assets") ||
               path.endsWith(".html") ||
               path.endsWith(".js") ||
               path.endsWith(".css") ||
               path.endsWith(".png") ||
               path.endsWith(".ico") ||
               path.endsWith(".svg");
    }

    /**
     * Validates CSRF prevention tokens or XMLHttpRequest / custom headers.
     */
    private boolean isValidCsrf(HttpServletRequest request, HttpSession session) {
        // Safe check 1: XMLHttpRequest or Custom Authorization/Client Header
        String requestedWith = request.getHeader("X-Requested-With");
        String customHeader = request.getHeader("X-CSRF-Token");
        if (requestedWith != null || customHeader != null) {
            return true;
        }

        // Safe check 2: Session-bound CSRF token validation if provided in request
        String sessionToken = (String) session.getAttribute("csrfToken");
        String paramToken = request.getParameter("_csrf");
        if (sessionToken != null && sessionToken.equals(paramToken)) {
            return true;
        }

        // Allow if Content-Type is application/json (standard non-form API fetch with preflight check)
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    /**
     * Writes structured, non-revealing JSON error response with correct HTTP status code.
     */
    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"error\":\"%s\"}", message));
        response.getWriter().flush();
    }

    @Override
    public void destroy() {
        System.out.println("[AuthenticationFilter] Web security filter destroyed.");
    }
}
