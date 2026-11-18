package test;

/**
 * Master Java Test Runner executing all unit, security, database, and E2E HTTP test suites.
 * Exits with non-zero status (exit code 1) if any single test or suite fails.
 */
public class AllTestsRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   ONLINE STOCK TRADING PLATFORM - MASTER JAVA TEST RUNNER     ");
        System.out.println("===============================================================\n");

        boolean tradingOk = TradingServiceTest.runAll();
        boolean portfolioOk = PortfolioCalculationTest.runAll();
        boolean securityOk = SecurityAndAuthTest.runAll();
        boolean databaseOk = DatabaseAndIntegrationTest.runAll();
        boolean e2eOk = EndToEndHttpIntegrationTest.runAll();

        boolean allPassed = tradingOk && portfolioOk && securityOk && databaseOk && e2eOk;

        System.out.println("===============================================================");
        if (allPassed) {
            System.out.println("   ALL 5 JAVA UNIT, SECURITY, DB & E2E SUITES PASSED (100%)    ");
            System.out.println("===============================================================");
        } else {
            System.err.println("   TEST FAILURE DETECTED IN ONE OR MORE SUITES!                ");
            System.err.println("===============================================================");
            System.exit(1);
        }
    }
}
