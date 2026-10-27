package servlet;

import server.JsonHelper;
import util.DBConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Diagnostic Servlet providing live Database Health, JDBC Metadata, and Connection Status.
 * Useful for college Review 2 faculty evaluation to verify live database connectivity
 * without disclosing sensitive credentials.
 */
public class DatabaseHealthServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map<String, Object> status = DBConnection.getHealthStatus();
        boolean connected = Boolean.TRUE.equals(status.get("connected"));

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"success\":").append(connected).append(",");
        sb.append("\"status\":\"").append(connected ? "CONNECTED" : "DISCONNECTED").append("\",");
        sb.append("\"databaseProduct\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("databaseProduct", "Unknown")))).append("\",");
        sb.append("\"databaseVersion\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("databaseVersion", "Unknown")))).append("\",");
        sb.append("\"driverName\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("driverName", "Unknown")))).append("\",");
        sb.append("\"driverVersion\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("driverVersion", "Unknown")))).append("\",");
        sb.append("\"engineType\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("engineType", "SQLite (Embedded Relational)")))).append("\",");
        sb.append("\"jdbcUrl\":\"").append(JsonHelper.escape(String.valueOf(status.getOrDefault("jdbcUrl", "jdbc:sqlite:data/stock_trading.db")))).append("\",");

        Object tableCountsObj = status.get("tableCounts");
        if (tableCountsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> counts = (Map<String, Integer>) tableCountsObj;
            sb.append("\"tableCounts\":{");
            int idx = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (idx++ > 0) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            }
            sb.append("},");
        }

        sb.append("\"timestamp\":\"").append(java.time.LocalDateTime.now()).append("\"");
        sb.append("}");

        sendJsonResponse(response, connected ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE, sb.toString());
    }
}
