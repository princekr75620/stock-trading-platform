package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import filter.AuthenticationFilter;
import servlet.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded Servlet Container Bridge hosting javax.servlet.http.HttpServlet controllers
 * and AuthenticationFilter with cryptographically random session IDs, true inactivity
 * timeout enforcement, HttpOnly/SameSite/Secure cookie attributes, and safe error handling.
 */
public class ServletBridge {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<String, ServletBridgeSession> sessionStore = new ConcurrentHashMap<>();

    private final Map<String, HttpServlet> servletMap = new HashMap<>();
    private final AuthenticationFilter authFilter = new AuthenticationFilter();

    public ServletBridge() {
        try {
            authFilter.init(null);
        } catch (ServletException ignored) {}
    }

    public static int resolveSessionTimeoutSeconds() {
        String prop = System.getProperty("session.timeout.seconds");
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv("SESSION_TIMEOUT_SECONDS");
        if (env != null && !env.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 1800; // 30 minutes default
    }

    private static String generateSessionId() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void registerServlet(String path, HttpServlet servlet) {
        try {
            servlet.init();
        } catch (ServletException e) {
            System.err.println("[ServletBridge] Servlet init failed: " + e.getMessage());
        }
        servletMap.put(path, servlet);
    }

    public HttpServlet getServlet(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String normalized = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;

        HttpServlet exact = servletMap.get(normalized);
        if (exact != null) {
            return exact;
        }

        String bestMatch = null;
        for (String key : servletMap.keySet()) {
            if (normalized.startsWith(key + "/")) {
                if (bestMatch == null || key.length() > bestMatch.length()) {
                    bestMatch = key;
                }
            }
        }
        return bestMatch != null ? servletMap.get(bestMatch) : null;
    }

    public static int resolveMaxBodyBytes() {
        String prop = System.getProperty("max.request.body.bytes");
        if (prop != null && !prop.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv("MAX_REQUEST_BODY_BYTES");
        if (env != null && !env.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 1_048_576; // 1 MB default
    }

    public void dispatch(HttpExchange exchange, HttpServlet servlet) throws IOException {
        dispatch(exchange, servlet, null);
    }

    public void dispatch(HttpExchange exchange, HttpServlet servlet, String bodyText) throws IOException {
        // Opportunistically purge expired sessions from store to prevent memory accumulation
        sessionStore.entrySet().removeIf(entry -> !entry.getValue().isValid() || entry.getValue().isExpired());

        ByteArrayOutputStream responseBodyBuffer = new ByteArrayOutputStream();
        int[] responseStatus = new int[]{200};
        Map<String, String> responseHeaders = new HashMap<>();

        byte[] requestBodyBytes;
        int maxBytes = resolveMaxBodyBytes();
        if (bodyText != null) {
            requestBodyBytes = bodyText.getBytes(StandardCharsets.UTF_8);
            if (requestBodyBytes.length > maxBytes) {
                sendPayloadTooLarge(exchange);
                return;
            }
        } else {
            try (InputStream is = exchange.getRequestBody()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = is.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) {
                        sendPayloadTooLarge(exchange);
                        return;
                    }
                    baos.write(buf, 0, n);
                }
                requestBodyBytes = baos.toByteArray();
            } catch (IOException e) {
                requestBodyBytes = new byte[0];
            }
        }

        // Purge expired or invalid session cookies deterministically
        String incomingSessionId = extractSessionId(exchange);
        ServletBridgeSession session = null;
        boolean clearCookieOnResponse = false;

        if (incomingSessionId != null) {
            ServletBridgeSession candidate = sessionStore.get(incomingSessionId);
            if (candidate != null) {
                if (!candidate.isValid() || candidate.isExpired()) {
                    candidate.invalidate();
                    clearCookieOnResponse = true;
                } else {
                    candidate.touch();
                    session = candidate;
                }
            } else {
                clearCookieOnResponse = true;
            }
        }

        final ServletBridgeSession[] sessionHolder = new ServletBridgeSession[]{session};
        final boolean[] clearedFlag = new boolean[]{clearCookieOnResponse};

        HttpServletRequest requestProxy = createRequestProxy(exchange, requestBodyBytes, sessionHolder, clearedFlag);
        HttpServletResponse responseProxy = createResponseProxy(responseStatus, responseHeaders, responseBodyBuffer);

        try {
            authFilter.doFilter(requestProxy, responseProxy, (req, resp) -> {
                try {
                    servlet.service((HttpServletRequest) req, (HttpServletResponse) resp);
                } catch (ServletException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("[ServletBridge] Internal error dispatching servlet: " + cause.getMessage());
            responseStatus[0] = 500;
            responseHeaders.put("Content-Type", "application/json;charset=UTF-8");
            responseBodyBuffer.reset();
            responseBodyBuffer.write("{\"success\":false,\"error\":\"Internal server error processing request.\"}".getBytes(StandardCharsets.UTF_8));
        }

        Headers headers = exchange.getResponseHeaders();
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            headers.set(entry.getKey(), entry.getValue());
        }
        if (!headers.containsKey("Content-Type")) {
            headers.set("Content-Type", "application/json;charset=UTF-8");
        }
        if (!headers.containsKey("X-Content-Type-Options")) {
            headers.set("X-Content-Type-Options", "nosniff");
        }
        if (!headers.containsKey("X-Frame-Options")) {
            headers.set("X-Frame-Options", "SAMEORIGIN");
        }

        String cookieAttrs = buildCookieAttributes(exchange);
        if (sessionHolder[0] != null && sessionHolder[0].isValid() && !sessionHolder[0].isExpired()) {
            sessionStore.put(sessionHolder[0].getId(), sessionHolder[0]);
            headers.set("Set-Cookie", "JSESSIONID=" + sessionHolder[0].getId() + cookieAttrs);
        } else if (clearedFlag[0] || (sessionHolder[0] != null && !sessionHolder[0].isValid())) {
            headers.set("Set-Cookie", "JSESSIONID=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT" + cookieAttrs);
        }

        byte[] outBytes = responseBodyBuffer.toByteArray();
        exchange.sendResponseHeaders(responseStatus[0], outBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(outBytes);
            os.flush();
        }
    }

    private void sendPayloadTooLarge(HttpExchange exchange) throws IOException {
        byte[] err = "{\"success\":false,\"error\":\"Request payload exceeds maximum allowed size.\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(413, err.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(err);
        }
    }

    private String buildCookieAttributes(HttpExchange exchange) {
        String sameSite = System.getenv("COOKIE_SAMESITE");
        if (sameSite == null || (!"Strict".equalsIgnoreCase(sameSite) && !"Lax".equalsIgnoreCase(sameSite) && !"None".equalsIgnoreCase(sameSite))) {
            sameSite = "Lax";
        }
        boolean secure = "true".equalsIgnoreCase(System.getenv("COOKIE_SECURE"))
                || "https".equalsIgnoreCase(getFirstHeader(exchange, "X-Forwarded-Proto"))
                || "None".equalsIgnoreCase(sameSite);
        return "; Path=/; HttpOnly; SameSite=" + sameSite + (secure ? "; Secure" : "");
    }

    private String getFirstHeader(HttpExchange exchange, String headerName) {
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String extractSessionId(HttpExchange exchange) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String ch : cookieHeaders) {
                String[] parts = ch.split(";");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("JSESSIONID=")) {
                        String val = part.substring("JSESSIONID=".length()).trim();
                        return val.isEmpty() ? null : val;
                    }
                }
            }
        }
        return null;
    }

    private HttpServletRequest createRequestProxy(
            HttpExchange exchange,
            byte[] bodyBytes,
            ServletBridgeSession[] sessionHolder,
            boolean[] clearedFlag) {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String queryString = exchange.getRequestURI().getQuery();

        Map<String, String[]> paramMap = new HashMap<>();
        if (queryString != null) {
            for (String pair : queryString.split("&")) {
                String[] kv = pair.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                paramMap.put(k, new String[]{v});
            }
        }

        Map<String, Object> attributes = new HashMap<>();

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, invokedMethod, args) -> {
                    String name = invokedMethod.getName();
                    switch (name) {
                        case "getMethod": return method;
                        case "getRequestURI": return path;
                        case "getQueryString": return queryString;
                        case "getScheme": {
                            String proto = getFirstHeader(exchange, "X-Forwarded-Proto");
                            return (proto != null && !proto.isEmpty()) ? proto : "http";
                        }
                        case "getServerName": {
                            String host = getFirstHeader(exchange, "X-Forwarded-Host");
                            if (host == null || host.isEmpty()) host = getFirstHeader(exchange, "Host");
                            if (host != null && host.contains(":")) return host.substring(0, host.indexOf(':'));
                            return (host != null && !host.isEmpty()) ? host : "127.0.0.1";
                        }
                        case "getParameter": {
                            String[] vals = paramMap.get(args[0]);
                            return (vals != null && vals.length > 0) ? vals[0] : null;
                        }
                        case "getParameterMap": return paramMap;
                        case "getHeader": return getFirstHeader(exchange, (String) args[0]);
                        case "getContentType": return getFirstHeader(exchange, "Content-Type");
                        case "getSession": {
                            boolean create = (args != null && args.length > 0) ? (Boolean) args[0] : true;
                            if (sessionHolder[0] != null) {
                                if (!sessionHolder[0].isValid() || sessionHolder[0].isExpired()) {
                                    sessionHolder[0].invalidate();
                                    sessionHolder[0] = null;
                                    clearedFlag[0] = true;
                                } else {
                                    sessionHolder[0].setNewSession(false);
                                    sessionHolder[0].touch();
                                }
                            }
                            if (sessionHolder[0] == null && create) {
                                sessionHolder[0] = new ServletBridgeSession(generateSessionId(), resolveSessionTimeoutSeconds());
                                sessionHolder[0].setNewSession(true);
                                clearedFlag[0] = false;
                            }
                            return sessionHolder[0];
                        }
                        case "setAttribute":
                            attributes.put((String) args[0], args[1]);
                            return null;
                        case "getAttribute":
                            return attributes.get(args[0]);
                        case "getReader":
                            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bodyBytes), StandardCharsets.UTF_8));
                        case "getInputStream":
                            return new DelegatingServletInputStream(new ByteArrayInputStream(bodyBytes));
                        default:
                            return defaultValue(invokedMethod.getReturnType());
                    }
                }
        );
    }

    private HttpServletResponse createResponseProxy(
            int[] status,
            Map<String, String> headers,
            ByteArrayOutputStream buffer) {

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);

        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, invokedMethod, args) -> {
                    String name = invokedMethod.getName();
                    switch (name) {
                        case "setStatus":
                            status[0] = (Integer) args[0];
                            return null;
                        case "getStatus":
                            return status[0];
                        case "setContentType":
                            headers.put("Content-Type", (String) args[0]);
                            return null;
                        case "setHeader":
                            headers.put((String) args[0], (String) args[1]);
                            return null;
                        case "getWriter":
                            return writer;
                        case "getOutputStream":
                            return new DelegatingServletOutputStream(buffer);
                        default:
                            return defaultValue(invokedMethod.getReturnType());
                    }
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0.0;
        return null;
    }

    public static class ServletBridgeSession implements HttpSession {
        private final String id;
        private final long creationTime;
        private volatile long lastAccessedTime;
        private volatile int maxInactiveIntervalSeconds;
        private volatile boolean newSession = true;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private volatile boolean valid = true;

        public ServletBridgeSession(String id) {
            this(id, resolveSessionTimeoutSeconds());
        }

        public ServletBridgeSession(String id, int maxInactiveIntervalSeconds) {
            this.id = id;
            long now = System.currentTimeMillis();
            this.creationTime = now;
            this.lastAccessedTime = now;
            this.maxInactiveIntervalSeconds = maxInactiveIntervalSeconds;
        }

        public void touch() {
            this.lastAccessedTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            if (maxInactiveIntervalSeconds <= 0) return false;
            return (System.currentTimeMillis() - lastAccessedTime) > (maxInactiveIntervalSeconds * 1000L);
        }

        @Override public String getId() { return id; }
        @Override public long getCreationTime() { return creationTime; }
        @Override public long getLastAccessedTime() { return lastAccessedTime; }
        @Override public ServletContext getServletContext() { return null; }
        @Override public void setMaxInactiveInterval(int interval) { this.maxInactiveIntervalSeconds = interval; }
        @Override public int getMaxInactiveInterval() { return maxInactiveIntervalSeconds; }
        @Override public HttpSessionContext getSessionContext() { return null; }
        @Override public Object getAttribute(String name) { return valid ? attributes.get(name) : null; }
        @Override public Object getValue(String name) { return getAttribute(name); }
        @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
        @Override public String[] getValueNames() { return attributes.keySet().toArray(new String[0]); }
        @Override public void setAttribute(String name, Object value) {
            if (!valid) return;
            if (value == null) attributes.remove(name);
            else attributes.put(name, value);
        }
        @Override public void putValue(String name, Object value) { setAttribute(name, value); }
        @Override public void removeAttribute(String name) { attributes.remove(name); }
        @Override public void removeValue(String name) { removeAttribute(name); }
        @Override public void invalidate() {
            valid = false;
            attributes.clear();
            sessionStore.remove(id);
        }
        public boolean isValid() { return valid; }
        public void setNewSession(boolean newSession) { this.newSession = newSession; }
        @Override public boolean isNew() { return newSession; }
    }

    private static class DelegatingServletInputStream extends ServletInputStream {
        private final InputStream source;
        public DelegatingServletInputStream(InputStream source) { this.source = source; }
        @Override public boolean isFinished() { try { return source.available() == 0; } catch (IOException e) { return true; } }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener readListener) {}
        @Override public int read() throws IOException { return source.read(); }
    }

    private static class DelegatingServletOutputStream extends ServletOutputStream {
        private final OutputStream target;
        public DelegatingServletOutputStream(OutputStream target) { this.target = target; }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener writeListener) {}
        @Override public void write(int b) throws IOException { target.write(b); }
    }
}
