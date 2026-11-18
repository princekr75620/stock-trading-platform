package test;

import model.Portfolio;
import model.Stock;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit Test verifying BigDecimal weighted average cost basis math, P&L calculation, and holding removals in Portfolio.
 */
public class PortfolioCalculationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        if (!runAll()) {
            System.exit(1);
        }
    }

    public static boolean runAll() {
        testsRun = 0;
        testsPassed = 0;

        System.out.println(">>> Running PortfolioCalculationTest Suite...");
        testInitialBuyCreatesHolding();
        testAdditionalBuyCalculatesWeightedAverage();
        testPartialSellDecrementsHolding();
        testFullSellRemovesHolding();
        testBigDecimalWeightedAverageAndUnrealizedPnL();

        System.out.printf("PortfolioCalculationTest Results: %d/%d Passed.\n\n", testsPassed, testsRun);
        return testsPassed == testsRun;
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

    public static void testBigDecimalWeightedAverageAndUnrealizedPnL() {
        // 3 @ 100.33 (300.99) + 7 @ 200.67 (1404.69) = 1705.68 / 10 = 170.57 (HALF_UP)
        Portfolio p = new Portfolio("trader1");
        p.recordBuy("INFY", 3, 100.33);
        p.recordBuy("INFY", 7, 200.67);

        double avg = p.getAverageBuyPrice("INFY"); // 1705.68 / 10 = 170.57 (HALF_UP)
        double totalInvest = p.getTotalInvestmentValue(); // exact cost basis sum = 1705.68

        Map<String, Stock> market = new HashMap<>();
        market.put("INFY", new Stock("INFY", "Infosys", "NSE", 200.57, 1000));
        double currentVal = p.getCurrentPortfolioValue(market); // 2005.70
        double unrealized = p.getUnrealizedProfitLoss(market); // 2005.70 - 1705.68 = 300.02

        assertTrue(Math.abs(avg - 170.57) < 0.001
                        && Math.abs(totalInvest - 1705.68) < 0.001
                        && Math.abs(currentVal - 2005.70) < 0.001
                        && Math.abs(unrealized - 300.02) < 0.001,
                "testBigDecimalWeightedAverageAndUnrealizedPnL - Exact BigDecimal cost basis & P&L math");
    }
}
