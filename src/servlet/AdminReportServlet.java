package servlet;

import server.JsonHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Servlet handling Audit and Compliance Report generation for Administrators.
 * Strictly enforces server-side session and ADMIN role verification via ensureAdmin.
 */
public class AdminReportServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdmin(request, response)) {
            return;
        }

        String type = request.getParameter("type");
        if (type == null || type.trim().isEmpty()) {
            type = "user";
        }

        String textReport;
        String reportTitle;

        switch (type.toLowerCase()) {
            case "trade":
                textReport = reportService.generateTradeReport();
                reportTitle = "Executive Trade & Transaction Audit";
                break;
            case "portfolio":
                textReport = reportService.generatePortfolioReport();
                reportTitle = "Portfolio Valuation & Holdings Report";
                break;
            case "financial":
                textReport = reportService.generateFinancialReport();
                reportTitle = "Platform Financial & Revenue Report";
                break;
            case "system":
            case "analytics":
                textReport = reportService.generateSystemAnalyticsReport();
                reportTitle = "System Health & Analytics Report";
                break;
            case "user":
            default:
                textReport = reportService.generateUserReport();
                reportTitle = "Trader Accounts & Verification Report";
                break;
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"").append(JsonHelper.escape(type)).append("\",");
        sb.append("\"title\":\"").append(JsonHelper.escape(reportTitle)).append("\",");
        sb.append("\"generatedAt\":\"").append(LocalDateTime.now()).append("\",");
        sb.append("\"reportText\":\"").append(JsonHelper.escape(textReport)).append("\"");
        sb.append("}");

        sendJsonResponse(response, HttpServletResponse.SC_OK, sb.toString());
    }
}
