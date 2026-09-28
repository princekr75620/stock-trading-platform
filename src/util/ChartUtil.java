package util;

/**
 * Utility class providing graphical and statistical visual representations
 * using Java console ASCII characters (bar charts, distribution bars, progress meters).
 */
public class ChartUtil {

    /**
     * Renders a horizontal bar chart comparing BUY vs SELL transaction counts.
     */
    public static String renderBuySellChart(int buyCount, int sellCount) {
        StringBuilder sb = new StringBuilder();
        int total = buyCount + sellCount;
        int maxBars = 35;

        int buyBars = total > 0 ? (int) Math.round((double) buyCount / total * maxBars) : 0;
        int sellBars = total > 0 ? (int) Math.round((double) sellCount / total * maxBars) : 0;

        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("                  TRADE ACTIVITY DISTRIBUTION (BUY vs SELL)                     \n");
        sb.append("--------------------------------------------------------------------------------\n");

        sb.append(String.format("BUY  [%4d] : ", buyCount));
        for (int i = 0; i < buyBars; i++) sb.append("█");
        sb.append(String.format(" %5.1f%%\n", total > 0 ? (buyCount * 100.0 / total) : 0.0));

        sb.append(String.format("SELL [%4d] : ", sellCount));
        for (int i = 0; i < sellBars; i++) sb.append("▓");
        sb.append(String.format(" %5.1f%%\n", total > 0 ? (sellCount * 100.0 / total) : 0.0));

        sb.append("--------------------------------------------------------------------------------\n");
        return sb.toString();
    }

    /**
     * Renders a horizontal percentage bar for metrics (e.g. Memory, Health, Quota).
     */
    public static String renderProgressBar(String label, double percentage, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-22s [", label));
        int filled = (int) Math.round(percentage / 100.0 * width);
        for (int i = 0; i < width; i++) {
            if (i < filled) sb.append("■");
            else sb.append(" ");
        }
        sb.append(String.format("] %5.1f%%", percentage));
        return sb.toString();
    }

    /**
     * Renders stock price sparkline or bar representation.
     */
    public static String renderStockPriceBar(String symbol, double price, double dayLow, double dayHigh) {
        StringBuilder sb = new StringBuilder();
        int barLength = 20;
        double range = dayHigh - dayLow;
        int pos = range > 0 ? (int) Math.round(((price - dayLow) / range) * (barLength - 1)) : barLength / 2;

        sb.append(String.format("%-6s $%-8.2f Low[", symbol, price));
        for (int i = 0; i < barLength; i++) {
            if (i == pos) sb.append("◆");
            else sb.append("─");
        }
        sb.append(String.format("]High ($%.2f - $%.2f)", dayLow, dayHigh));
        return sb.toString();
    }
}
