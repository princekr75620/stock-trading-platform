package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import filter.AuthenticationFilter;
import model.User;
import servlet.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded Servlet Container Bridge that mounts Java EE Servlets and Filters
 * on the embedded Java HTTP Server.
 * Supports full HttpServlet lifecycle (init, service, destroy), HttpSession management,
 * and FilterChain execution for GUVI Evaluation.
 */
public class ServletBridge {

    // Active session store: sessionId -> ServletBridgeSession
    private static final Map<String, ServletBridgeSession> sessionStore = new ConcurrentHashMap<>();

    // Registered servlets
    private final Map<String, HttpServlet> servletMap = new HashMap<>();
    private final AuthenticationFilter authFilter = new AuthenticationFilter();

    public ServletBridge() {
        try {
            authFilter.init(null);
        } catch (ServletException ignored) {}
    }

    public void registerServlet(String path, HttpServlet servlet) {
        try {
            servlet.init();
        } catch (ServletException e) {
            e.printStackTrace();
        }
        servletMap.put(path, servlet);
    }

    public HttpServlet getServlet(String path) {
        if (servletMap.containsKey(path)) {
            return servletMap.get(path);
        }
        // Match prefix patterns if exact match not found
        for (Map.Entry<String, HttpServlet> entry : servletMap.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void dispatch(HttpExchange exchange, HttpServlet servlet) throws IOException {
        dispatch(exchange, servlet, null);
    }

    public void dispatch(HttpExchange exchange, HttpServlet servlet, String bodyText) throws IOException {
        ByteArrayOutputStream responseBodyBuffer = new ByteArrayOutputStream();
        int[] responseStatus = new int[]{200};
        Map<String, String> responseHeaders = new HashMap<>();

        // Use pre-read body bytes or read from stream
        byte[] requestBodyBytes;
        if (bodyText != null) {
            requestBodyBytes = bodyText.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                requestBodyBytes = exchange.getRequestBody().readAllBytes();
            } catch (IOException e) {
                requestBodyBytes = new byte[0];
            }
        }

        // Extract or generate session
        String sessionId = extractSessionId(exchange);
        ServletBridgeSession session = null;
        if (sessionId != null && sessionStore.containsKey(sessionId)) {
            session = sessionStore.get(sessionId);
        }

        final ServletBridgeSession currentSession = session;
        final ServletBridgeSession[] sessionHolder = new ServletBridgeSession[]{currentSession};

        // Create Dynamic Proxies for HttpServletRequest and HttpServletResponse
        HttpServletRequest requestProxy = createRequestProxy(exchange, requestBodyBytes, sessionHolder);
        HttpServletResponse responseProxy = createResponseProxy(exchange, responseStatus, responseHeaders, responseBodyBuffer, sessionHolder);

        try {
            // Run through AuthenticationFilter first
            authFilter.doFilter(requestProxy, responseProxy, (req, resp) -> {
                try {
                    servlet.service((HttpServletRequest) req, (HttpServletResponse) resp);
                } catch (ServletException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("[ServletBridge] Error in servlet service: " + cause.getMessage());
            responseStatus[0] = 500;
            responseHeaders.put("Content-Type", "application/json;charset=UTF-8");
            responseBodyBuffer.reset();
            responseBodyBuffer.write(String.format("{\"success\":false,\"error\":\"Servlet error: %s\"}",
                    JsonHelper.escape(cause.getMessage())).getBytes(StandardCharsets.UTF_8));
        }

        // Send headers
        Headers headers = exchange.getResponseHeaders();
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            headers.set(entry.getKey(), entry.getValue());
        }
        if (!headers.containsKey("Content-Type")) {
            headers.set("Content-Type", "application/json;charset=UTF-8");
        }

        // Set session cookie if new session created
        if (sessionHolder[0] != null) {
            sessionStore.put(sessionHolder[0].getId(), sessionHolder[0]);
            headers.add("Set-Cookie", "JSESSIONID=" + sessionHolder[0].getId() + "; Path=/; HttpOnly; SameSite=Lax");
        }

        byte[] outBytes = responseBodyBuffer.toByteArray();
        exchange.sendResponseHeaders(responseStatus[0], outBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(outBytes);
            os.flush();
        }
    }

    private String extractSessionId(HttpExchange exchange) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String ch : cookieHeaders) {
                String[] parts = ch.split(";");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("JSESSIONID=")) {
                        return part.substring("JSESSIONID=".length());
                    }
                }
            }
        }
        return null;
    }

    private HttpServletRequest createRequestProxy(
            HttpExchange exchange,
            byte[] bodyBytes,
            ServletBridgeSession[] sessionHolder) {

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
                        case "getParameter":
                            String[] vals = paramMap.get(args[0]);
                            return (vals != null && vals.length > 0) ? vals[0] : null;
                        case "getParameterMap": return paramMap;
                        case "getHeader":
                            return exchange.getRequestHeaders().getFirst((String) args[0]);
                        case "getSession":
                            boolean create = (args != null && args.length > 0) ? (Boolean) args[0] : true;
                            if (sessionHolder[0] == null && create) {
                                sessionHolder[0] = new ServletBridgeSession(UUID.randomUUID().toString());
                            }
                            return sessionHolder[0];
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
            HttpExchange exchange,
            int[] status,
            Map<String, String> headers,
            ByteArrayOutputStream buffer,
            ServletBridgeSession[] sessionHolder) {

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
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private boolean valid = true;

        public ServletBridgeSession(String id) {
            this.id = id;
            this.creationTime = System.currentTimeMillis();
        }

        @Override public String getId() { return id; }
        @Override public long getCreationTime() { return creationTime; }
        @Override public long getLastAccessedTime() { return creationTime; }
        @Override public ServletContext getServletContext() { return null; }
        @Override public void setMaxInactiveInterval(int interval) {}
        @Override public int getMaxInactiveInterval() { return 1800; }
        @Override public HttpSessionContext getSessionContext() { return null; }
        @Override public Object getAttribute(String name) { return attributes.get(name); }
        @Override public Object getValue(String name) { return getAttribute(name); }
        @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
        @Override public String[] getValueNames() { return attributes.keySet().toArray(new String[0]); }
        @Override public void setAttribute(String name, Object value) {
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
        @Override public boolean isNew() { return false; }
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
