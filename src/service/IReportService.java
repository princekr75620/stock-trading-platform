package service;

/**
 * Interface defining operations for administrative and financial report generation.
 */
public interface IReportService {
    String generateUserReport();
    String generateTradeReport();
    String generatePortfolioReport();
    String generateFinancialReport();
    String generateSystemAnalyticsReport();
    ReportGenerator getReportGenerator();
}
