package filter;

import model.User;
import model.UserRole;
import server.JsonHelper;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Servlet Filter implementing Session-based Authentication,
 * Role-Based Access Control (RBAC), Cross-Tenant Isolation, Host/Proxy Trust Validation,
 * and Strict CSRF Enforcement.
 */
public class AuthenticationFilter implements Filter {

    private static final Pattern FORWARDED_HOST_PATTERN =
            Pattern.compile("(?i)(?:^|[;,\\s])host=(\"[^\"]+\"|[^;,\\s]+)");

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

        // 0. Validate Host and Forwarded proxy headers unconditionally on all requests
        String hostValidationError = validateHostAndForwardedHeaders(httpRequest);
        if (hostValidationError != null) {
            sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN, hostValidationError);
            return;
        }

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Enforce Origin/Referer validation on all state-changing requests (including auth endpoints) when present
        if (isStateChangingMethod(method)) {
            String origin = httpRequest.getHeader("Origin");
            if (origin != null && !origin.trim().isEmpty()) {
                if (!isAllowedOrigin(origin.trim(), httpRequest)) {
                    sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Security validation failed: Untrusted request Origin.");
                    return;
                }
            } else {
                String referer = httpRequest.getHeader("Referer");
                if (referer != null && !referer.trim().isEmpty()) {
                    if (!isAllowedOrigin(referer.trim(), httpRequest)) {
                        sendJsonError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                                "Security validation failed: Untrusted request Referer.");
                        return;
                    }
                }
            }
        }

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

    /**
     * Validates incoming Host, X-Forwarded-Host, and Forwarded headers.
     * Untrusted forwarded host headers or untrusted Host headers are rejected cleanly.
     */
    public static String validateHostAndForwardedHeaders(HttpServletRequest request) {
        String hostHeader = request.getHeader("Host");
        String xForwardedHost = request.getHeader("X-Forwarded-Host");
        String forwardedHeader = request.getHeader("Forwarded");
        String remoteAddr = request.getRemoteAddr();
        String gatewaySecret = request.getHeader("X-Gateway-Proxy-Secret");
        return validateHostAndForwardedHeaders(hostHeader, xForwardedHost, forwardedHeader, remoteAddr, gatewaySecret);
    }

    public static String validateHostAndForwardedHeaders(
            String hostHeader,
            String xForwardedHost,
            String forwardedHeader,
            String remoteAddr,
            String gatewaySecret) {

        if (hostHeader != null && !hostHeader.trim().isEmpty()) {
            if (!isAllowedHost(hostHeader.trim())) {
                return "Security validation failed: Untrusted Host header.";
            }
        }

        boolean trustedProxy = isTrustedProxyPeer(remoteAddr, gatewaySecret);

        if (xForwardedHost != null && !xForwardedHost.trim().isEmpty()) {
            if (!trustedProxy || !isAllowedHost(xForwardedHost.trim())) {
                return "Security validation failed: Untrusted forwarded host header.";
            }
        }

        if (forwardedHeader != null && !forwardedHeader.trim().isEmpty()) {
            Matcher m = FORWARDED_HOST_PATTERN.matcher(forwardedHeader);
            while (m.find()) {
                String candidate = m.group(1).replace("\"", "").trim();
                if (!candidate.isEmpty() && (!trustedProxy || !isAllowedHost(candidate))) {
                    return "Security validation failed: Untrusted Forwarded header host.";
                }
            }
        }
        return null;
    }

    public static boolean isTrustedProxyRequest(HttpServletRequest request) {
        if (request == null) return false;
        return isTrustedProxyPeer(request.getRemoteAddr(), request.getHeader("X-Gateway-Proxy-Secret"));
    }

    public static boolean isTrustedProxyPeer(String remoteAddr, String gatewaySecretHeader) {
        String normalizedRemote = normalizeIp(remoteAddr);

        // 1. Authenticated local Express gateway hop over loopback
        String configuredSecret = util.EnvConfig.get("GATEWAY_PROXY_SECRET");
        if (configuredSecret != null && !configuredSecret.isEmpty()
                && gatewaySecretHeader != null && !gatewaySecretHeader.isEmpty()
                && isLoopbackIp(normalizedRemote)) {
            if (MessageDigest.isEqual(
                    configuredSecret.getBytes(StandardCharsets.UTF_8),
                    gatewaySecretHeader.trim().getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }

        // 2. Explicit TRUST_PROXY / TRUSTED_PROXIES configuration
        String trustProxyFlag = util.EnvConfig.get("TRUST_PROXY");
        String trustedProxiesRaw = util.EnvConfig.first("TRUSTED_PROXIES", "TRUSTED_PROXY_IPS", null);

        if (trustedProxiesRaw != null && !trustedProxiesRaw.isEmpty()) {
            if (normalizedRemote == null) return false;
            for (String entry : trustedProxiesRaw.split(",")) {
                String allowed = normalizeIp(entry);
                if (allowed != null && allowed.equals(normalizedRemote)) {
                    return true;
                }
            }
            return false;
        }

        return "true".equalsIgnoreCase(trustProxyFlag);
    }

    /**
     * Resolves client IP safely. Forwarded client IP headers are only honored
     * when the immediate peer is explicitly verified as a trusted proxy.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String directRemote = normalizeIp(request.getRemoteAddr());
        if (isTrustedProxyRequest(request)) {
            String realIp = sanitizeIpHeader(request.getHeader("X-Real-IP"));
            if (realIp != null) return realIp;

            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.trim().isEmpty()) {
                String first = sanitizeIpHeader(xff.split(",")[0]);
                if (first != null) return first;
            }
        }
        return directRemote != null ? directRemote : "unknown";
    }

    private static String sanitizeIpHeader(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) return null;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '[' || c == ']';
            if (!ok) return null;
        }
        return normalizeIp(trimmed);
    }

    private static String normalizeIp(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("/")) s = s.substring(1);
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
        }
        int zoneIdx = s.indexOf('%');
        if (zoneIdx > 0) {
            s = s.substring(0, zoneIdx);
        }
        if ("0:0:0:0:0:0:0:1".equals(s) || "::ffff:127.0.0.1".equalsIgnoreCase(s)) {
            return "127.0.0.1";
        }
        return s.toLowerCase();
    }

    private static boolean isLoopbackIp(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "localhost".equals(ip);
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

        // 1. Validate Origin / Referer against explicitly configured trusted origins
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
            if (uri.getRawUserInfo() != null) {
                return false;
            }

            String normalizedHost = host.toLowerCase();
            String originKey = (scheme + "://" + normalizedHost + (uri.getPort() > 0 ? ":" + uri.getPort() : "")).toLowerCase();
            if (getConfiguredAllowedOrigins().contains(originKey)) {
                return true;
            }

            // Never trust X-Forwarded-Host dynamically here.
            // In non-production mode, permit same-host loopback calls with validated Host header.
            if (!util.EnvConfig.isProductionMode()
                    && ("localhost".equals(normalizedHost) || "127.0.0.1".equals(normalizedHost) || "::1".equals(normalizedHost))) {
                String expectedScheme = request.getScheme() != null ? request.getScheme().toLowerCase() : "http";
                String hostHeader = request.getHeader("Host");
                String originHostWithPort = (normalizedHost + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
                if (hostHeader != null && isAllowedHost(hostHeader.trim())) {
                    return expectedScheme.equalsIgnoreCase(scheme)
                            && originHostWithPort.equalsIgnoreCase(hostHeader.trim());
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAllowedHost(String rawHostHeader) {
        if (rawHostHeader == null) return false;
        String trimmed = rawHostHeader.trim();
        if (trimmed.isEmpty() || trimmed.length() > 255) return false;
        // Disallow multiple comma-separated hosts or control/userinfo/path characters
        if (trimmed.contains(",") || trimmed.contains(" ") || trimmed.contains("\t")
                || trimmed.contains("\r") || trimmed.contains("\n") || trimmed.contains("@")
                || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("?")) {
            return false;
        }

        String candidateHost;
        int port = -1;
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("[")) {
            int close = lower.indexOf(']');
            if (close < 2) return false;
            candidateHost = lower.substring(1, close);
            if (close + 1 < lower.length()) {
                if (lower.charAt(close + 1) != ':') return false;
                port = parsePort(lower.substring(close + 2));
                if (port <= 0) return false;
            }
        } else {
            int colon = lower.indexOf(':');
            if (colon >= 0) {
                if (lower.indexOf(':', colon + 1) >= 0) return false; // multiple colons outside []
                candidateHost = lower.substring(0, colon);
                port = parsePort(lower.substring(colon + 1));
                if (port <= 0) return false;
            } else {
                candidateHost = lower;
            }
        }

        if (candidateHost.isEmpty()) return false;
        String normalizedHostPort = port > 0 ? (candidateHost + ":" + port) : candidateHost;

        Set<String> allowedHosts = getConfiguredAllowedHosts();
        if (allowedHosts.contains(normalizedHostPort) || allowedHosts.contains(candidateHost)) {
            return true;
        }

        String javaHost = util.EnvConfig.get("JAVA_HOST", "127.0.0.1").toLowerCase();
        if (candidateHost.equals(javaHost) || "127.0.0.1".equals(candidateHost) || "localhost".equals(candidateHost)) {
            int javaPort = util.EnvConfig.getPositiveInt("JAVA_PORT", "JAVA_PORT", 8765);
            if (port == javaPort) {
                return true;
            }
        }
        return false;
    }

    private static int parsePort(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            int p = Integer.parseInt(s);
            return (p >= 1 && p <= 65535) ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Set<String> getConfiguredAllowedHosts() {
        Set<String> hosts = new HashSet<>();
        String envHosts = util.EnvConfig.get("ALLOWED_HOSTS");
        if (envHosts != null && !envHosts.isEmpty()) {
            for (String item : envHosts.split(",")) {
                String trimmed = item.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    hosts.add(trimmed);
                }
            }
        }

        for (String origin : getConfiguredAllowedOrigins()) {
            try {
                URI uri = URI.create(origin);
                if (uri.getHost() != null) {
                    String h = uri.getHost().toLowerCase();
                    hosts.add(h);
                    if (uri.getPort() > 0) {
                        hosts.add(h + ":" + uri.getPort());
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!util.EnvConfig.isProductionMode()) {
            hosts.add("localhost");
            hosts.add("127.0.0.1");
            hosts.add("::1");
        }
        return hosts;
    }

    public static Set<String> getConfiguredAllowedOrigins() {
        Set<String> set = new HashSet<>();
        addOriginList(set, util.EnvConfig.get("ALLOWED_ORIGINS"));
        addOriginList(set, util.EnvConfig.get("APP_URL"));
        set.add("http://localhost:3000");
        set.add("http://127.0.0.1:3000");

        if (!util.EnvConfig.isProductionMode()) {
            String portStr = util.EnvConfig.get("PORT");
            if (portStr != null) {
                int p = parsePort(portStr);
                if (p > 0) {
                    set.add("http://localhost:" + p);
                    set.add("http://127.0.0.1:" + p);
                }
            }
        }
        return set;
    }

    private static void addOriginList(Set<String> target, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        for (String item : raw.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            try {
                URI uri = URI.create(trimmed);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    String normalized = (uri.getScheme() + "://" + uri.getHost()
                            + (uri.getPort() > 0 ? ":" + uri.getPort() : "")).toLowerCase();
                    target.add(normalized);
                }
            } catch (Exception ignored) {}
        }
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"error\":\"%s\"}", JsonHelper.escape(message)));
        response.getWriter().flush();
    }

    @Override
    public void destroy() {
        System.out.println("[AuthenticationFilter] Web security filter destroyed.");
    }
}
