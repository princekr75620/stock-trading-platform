package service;

/**
 * Service implementation for managing and delivering executive, financial, and analytical reports.
 * Wraps and exposes the ReportGenerator to the application service layer.
 */
public class ReportService implements IReportService {
    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    public ReportService(IUserService userService,
                         ITradingService tradingService,
                         IPortfolioService portfolioService,
                         IMarketUpdateService marketUpdateService,
                         ISecurityService securityService,
                         ISystemSettingsService systemSettingsService) {
        this.reportGenerator = new ReportGenerator(
                userService,
                tradingService,
                portfolioService,
                marketUpdateService,
                securityService,
                systemSettingsService
        );
    }

    @Override
    public String generateUserReport() {
        return reportGenerator.generateUserReport();
    }

    @Override
    public String generateTradeReport() {
        return reportGenerator.generateTradeReport();
    }

    @Override
    public String generatePortfolioReport() {
        return reportGenerator.generatePortfolioReport();
    }

    @Override
    public String generateFinancialReport() {
        return reportGenerator.generateFinancialReport();
    }

    @Override
    public String generateSystemAnalyticsReport() {
        return reportGenerator.generateSystemAnalyticsReport();
    }

    @Override
    public ReportGenerator getReportGenerator() {
        return reportGenerator;
    }
}
