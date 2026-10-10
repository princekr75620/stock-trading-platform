package test;

import model.Portfolio;

/**
 * Unit Test verifying weighted average cost basis math and holding removals in Portfolio.
 */
public class PortfolioCalculationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println(">>> Running PortfolioCalculationTest Suite...");
        testInitialBuyCreatesHolding();
        testAdditionalBuyCalculatesWeightedAverage();
        testPartialSellDecrementsHolding();
        testFullSellRemovesHolding();

        System.out.printf("PortfolioCalculationTest Results: %d/%d Passed.\n\n", testsPassed, testsRun);
    }

    private static void assertTrue(boolean condition, String testName) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  ✓ PASS: " + testName);
        } else {
            System.err.println("  ✗ FAIL: " + testName);
        }
    }

    public static void testInitialBuyCreatesHolding() {
        Portfolio p = new Portfolio("trader1");
        p.recordBuy("RELIANCE", 10, 2500.00);

        int qty = p.getHoldings().getOrDefault("RELIANCE", 0);
        double avg = p.getAveragePrice("RELIANCE");

        assertTrue(qty == 10 && Math.abs(avg - 2500.00) < 0.01, "testInitialBuyCreatesHolding - 10 shares @ ₹2500.00");
    }

    public static void testAdditionalBuyCalculatesWeightedAverage() {
        // Buy 10 @ ₹2500 (= ₹25,000), Buy 10 @ ₹3000 (= ₹30,000). Total 20 shares, Total ₹55,000 => Avg ₹2750
        Portfolio p = new Portfolio("trader1");
        p.recordBuy("RELIANCE", 10, 2500.00);
        p.recordBuy("RELIANCE", 10, 3000.00);

        int qty = p.getHoldings().getOrDefault("RELIANCE", 0);
        double avg = p.getAveragePrice("RELIANCE");

        assertTrue(qty == 20 && Math.abs(avg - 2750.00) < 0.01, "testAdditionalBuyCalculatesWeightedAverage - Weighted avg ₹2750.00");
    }

    public static void testPartialSellDecrementsHolding() {
        Portfolio p = new Portfolio("trader1");
        p.recordBuy("TCS", 15, 4000.00);
        p.recordSell("TCS", 5);

        int qty = p.getHoldings().getOrDefault("TCS", 0);
        double avg = p.getAveragePrice("TCS");

        assertTrue(qty == 10 && Math.abs(avg - 4000.00) < 0.01, "testPartialSellDecrementsHolding - Remaining 10 shares @ ₹4000.00");
    }

    public static void testFullSellRemovesHolding() {
        Portfolio p = new Portfolio("trader1");
        p.recordBuy("INFY", 5, 1800.00);
        p.recordSell("INFY", 5);

        boolean hasKey = p.getHoldings().containsKey("INFY");
        assertTrue(!hasKey, "testFullSellRemovesHolding - Fully sold holding purged from Map");
    }
}
