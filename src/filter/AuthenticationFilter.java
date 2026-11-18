package filter;

import model.User;
import model.UserRole;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Enterprise Servlet Filter implementing Session-based Authentication,
 * Role-Based Access Control (RBAC), Cross-Tenant Isolation, and Strict CSRF Enforcement.
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

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        if ("https".equalsIgnoreCase(httpRequest.getScheme())) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Logout enforces POST-only and CSRF validation when a session is active
        if ("/api/auth/logout".equals(path)) {
            if (!"POST".equalsIgnoreCase(method)) {
                sendJsonError(httpResponse, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                        "Method Not Allowed. Use POST /api/auth/logout.");
                return;
            }
            HttpSession logoutSession = httpRequest.getSession(false);
            if (logoutSession != null && logoutSession.getAttribute("user") instanceof User) {
                if (!isValidCsrf(httpRequest, logoutSession)) {
                    sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Security validation failed: Request origin or CSRF token invalid.");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        // 1. Allow public read/auth endpoints
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

        if (sessionUser == null) {
            sendJsonError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required. Please log in with valid credentials.");
            return;
        }

        // 3. Strict CSRF Protection on ALL state-changing methods (POST, PUT, PATCH, DELETE)
        if (isStateChangingMethod(method)) {
            if (!isValidCsrf(httpRequest, session)) {
                sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Security validation failed: Request origin or CSRF token invalid.");
                return;
            }
        }

        // 4. Administrator Route Authorization (/api/admin/*)
        if (path != null && path.startsWith("/api/admin")) {
            if (sessionUser.getRole() != UserRole.ADMIN) {
                sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: Administrator privileges required.");
                return;
            }
        }

        // 5. Cross-Tenant Resource Isolation (query params AND path suffixes)
        String requestedTargetId = extractTargetUserId(path, httpRequest);
        if (requestedTargetId != null && !requestedTargetId.isEmpty()) {
            if (sessionUser.getRole() != UserRole.ADMIN &&
                !sessionUser.getUserId().equalsIgnoreCase(requestedTargetId)) {
                sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: You are not authorized to view another user's portfolio or account data.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isStateChangingMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private String extractTargetUserId(String path, HttpServletRequest request) {
        String traderIdParam = request.getParameter("traderId");
        if (traderIdParam != null && !traderIdParam.trim().isEmpty()) {
            return traderIdParam.trim();
        }
        String userIdParam = request.getParameter("userId");
        if (userIdParam != null && !userIdParam.trim().isEmpty()) {
            return userIdParam.trim();
        }

        String[] prefixes = {
                "/api/portfolio/",
                "/api/trades/",
                "/api/transactions/",
                "/api/dashboard/",
                "/api/notifications/",
                "/api/watchlist/"
        };
        if (path == null) return null;
        for (String prefix : prefixes) {
            if (path.startsWith(prefix) && path.length() > prefix.length()) {
                String suffix = path.substring(prefix.length());
                if ("/api/trades/".equals(prefix) && ("buy".equalsIgnoreCase(suffix) || "sell".equalsIgnoreCase(suffix))) {
                    return null;
                }
                if ("/api/notifications/".equals(prefix) && "read".equalsIgnoreCase(suffix)) {
                    return null;
                }
                int slashIdx = suffix.indexOf('/');
                String candidate = (slashIdx >= 0 ? suffix.substring(0, slashIdx) : suffix).trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path == null) return true;
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isPost = "POST".equalsIgnoreCase(method);
        return (isPost && path.equals("/api/auth/login")) ||
               (isPost && path.equals("/api/auth/register")) ||
               (isGet && path.equals("/api/auth/session")) ||
               (isGet && path.equals("/api/health")) ||
               (isGet && path.equals("/api/db/health")) ||
               (isGet && path.equals("/api/stocks")) ||
               (isGet && path.equals("/api/market/updates")) ||
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
     * Enforces Origin/Referer trust validation AND constant-time verification
     * of a session-bound CSRF token (`X-CSRF-Token` header or `_csrf` param).
     */
    private boolean isValidCsrf(HttpServletRequest request, HttpSession session) {
        if (session == null) return false;

        // 1. Validate Origin / Referer against configured trusted origins or exact request Host
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.trim().isEmpty()) {
            if (!isAllowedOrigin(origin.trim(), request)) {
                return false;
            }
        } else {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.trim().isEmpty()) {
                if (!isAllowedOrigin(referer.trim(), request)) {
                    return false;
                }
            }
        }

        // 2. Validate cryptographically strong session-bound CSRF token (constant-time comparison)
        Object tokenObj = session.getAttribute("csrfToken");
        String sessionToken = (tokenObj instanceof String) ? (String) tokenObj : null;
        if (sessionToken == null || sessionToken.isEmpty()) {
            return false;
        }

        String providedToken = request.getHeader("X-CSRF-Token");
        if (providedToken == null || providedToken.trim().isEmpty()) {
            providedToken = request.getParameter("_csrf");
        }
        if (providedToken == null || providedToken.trim().isEmpty()) {
            return false;
        }

        return MessageDigest.isEqual(
                sessionToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isAllowedOrigin(String originOrReferer, HttpServletRequest request) {
        try {
            URI uri = URI.create(originOrReferer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return false;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }

            Set<String> allowedOrigins = getConfiguredAllowedOrigins();
            String normalizedOrigin = (scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "")).toLowerCase();
            if (allowedOrigins.contains(normalizedOrigin)) {
                return true;
            }

            // Allow exact scheme + host[:port] match against reverse-proxy forwarded host or request Host header
            String expectedScheme = request.getScheme() != null ? request.getScheme().toLowerCase() : "http";
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            if (forwardedHost != null && !forwardedHost.trim().isEmpty()) {
                String fh = forwardedHost.split(",")[0].trim().toLowerCase();
                if (normalizedOrigin.equals(expectedScheme + "://" + fh)) {
                    return true;
                }
            }
            String hostHeader = request.getHeader("Host");
            if (hostHeader != null && !hostHeader.trim().isEmpty()) {
                String hh = hostHeader.trim().toLowerCase();
                if (normalizedOrigin.equals(expectedScheme + "://" + hh)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static Set<String> getConfiguredAllowedOrigins() {
        Set<String> set = new HashSet<>();
        String envOrigins = System.getenv("ALLOWED_ORIGINS");
        if (envOrigins != null && !envOrigins.trim().isEmpty()) {
            for (String item : envOrigins.split(",")) {
                String trimmed = item.trim().toLowerCase();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
        }
        set.add("http://localhost:3000");
        set.add("http://127.0.0.1:3000");
        return set;
    }

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
