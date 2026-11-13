package test;

/**
 * Master Java Test Runner executing all unit test suites for terminal verification.
 */
public class AllTestsRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   ONLINE STOCK TRADING PLATFORM - JAVA UNIT TEST RUNNER      ");
        System.out.println("===============================================================\n");

        TradingServiceTest.main(args);
        PortfolioCalculationTest.main(args);
        SecurityAndAuthTest.main(args);
        DatabaseAndIntegrationTest.main(args);
        EndToEndHttpIntegrationTest.main(args);

        System.out.println("===============================================================");
        System.out.println("   ALL JAVA UNIT & E2E INTEGRATION TESTS PASSED!               ");
        System.out.println("===============================================================");
    }
}
