package model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Trader's investment portfolio.
 * Tracks stocks owned, quantities, cost basis (purchase value), current value, and profit/loss.
 * Demonstrates Collections (HashMap), Encapsulation, and Business Logic separation.
 */
public class Portfolio implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traderId;
    // Map of stock symbol -> quantity owned
    private Map<String, Integer> holdings;
    // Map of stock symbol -> total cost basis paid for currently held shares
    private Map<String, Double> costBasis;
    // Cumulative realized profit/loss from sold stocks
    private double realizedProfitLoss;

    public Portfolio(String traderId) {
        this.traderId = traderId;
        this.holdings = new HashMap<>();
        this.costBasis = new HashMap<>();
        this.realizedProfitLoss = 0.0;
    }

    public Portfolio(String traderId, Map<String, Integer> holdings, Map<String, Double> costBasis, double realizedProfitLoss) {
        this.traderId = traderId;
        this.holdings = (holdings != null) ? new HashMap<>(holdings) : new HashMap<>();
        this.costBasis = (costBasis != null) ? new HashMap<>(costBasis) : new HashMap<>();
        this.realizedProfitLoss = realizedProfitLoss;
    }

    /**
     * Updates portfolio upon a BUY transaction.
     */
    public synchronized void recordBuy(String symbol, int quantity, double price) {
        symbol = symbol.toUpperCase().trim();
        int currentQty = holdings.getOrDefault(symbol, 0);
        double currentCost = costBasis.getOrDefault(symbol, 0.0);

        holdings.put(symbol, currentQty + quantity);
        costBasis.put(symbol, currentCost + (quantity * price));
    }

    /**
     * Updates portfolio upon a SELL transaction.
     * Calculates realized gain/loss using weighted average cost basis.
     * Returns the realized profit/loss on this sale.
     */
    public synchronized double recordSell(String symbol, int quantity, double sellPrice) {
        symbol = symbol.toUpperCase().trim();
        int currentQty = holdings.getOrDefault(symbol, 0);
        if (currentQty < quantity) {
            throw new IllegalArgumentException("Cannot sell more shares than currently owned!");
        }

        double currentCost = costBasis.getOrDefault(symbol, 0.0);
        double avgCostPerShare = currentQty > 0 ? (currentCost / currentQty) : 0.0;
        double costOfSoldShares = avgCostPerShare * quantity;
        double saleRevenue = quantity * sellPrice;
        double saleProfit = saleRevenue - costOfSoldShares;

        this.realizedProfitLoss += saleProfit;

        int remainingQty = currentQty - quantity;
        if (remainingQty == 0) {
            holdings.remove(symbol);
            costBasis.remove(symbol);
        } else {
            holdings.put(symbol, remainingQty);
            costBasis.put(symbol, currentCost - costOfSoldShares);
        }

        return saleProfit;
    }

    public int getQuantity(String symbol) {
        return holdings.getOrDefault(symbol.toUpperCase().trim(), 0);
    }

    public double getCostBasis(String symbol) {
        return costBasis.getOrDefault(symbol.toUpperCase().trim(), 0.0);
    }

    public double getAverageBuyPrice(String symbol) {
        int qty = getQuantity(symbol);
        if (qty == 0) return 0.0;
        return costBasis.getOrDefault(symbol.toUpperCase().trim(), 0.0) / qty;
    }

    public double getTotalInvestmentValue() {
        double total = 0.0;
        for (double cost : costBasis.values()) {
            total += cost;
        }
        return Math.round(total * 100.0) / 100.0;
    }

    public double getCurrentPortfolioValue(Map<String, Stock> marketStocks) {
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
            String sym = entry.getKey();
            int qty = entry.getValue();
            Stock stock = marketStocks.get(sym);
            double price = stock != null ? stock.getCurrentPrice() : 0.0;
            total += (qty * price);
        }
        return Math.round(total * 100.0) / 100.0;
    }

    public double getUnrealizedProfitLoss(Map<String, Stock> marketStocks) {
        double curVal = getCurrentPortfolioValue(marketStocks);
        double investVal = getTotalInvestmentValue();
        return Math.round((curVal - investVal) * 100.0) / 100.0;
    }

    public double getUnrealizedProfitLossPercent(Map<String, Stock> marketStocks) {
        double investVal = getTotalInvestmentValue();
        if (investVal == 0.0) return 0.0;
        double pnl = getUnrealizedProfitLoss(marketStocks);
        return Math.round((pnl / investVal * 100.0) * 100.0) / 100.0;
    }

    public String getTraderId() {
        return traderId;
    }

    public String getUserId() {
        return traderId;
    }

    public double getAveragePrice(String symbol) {
        if (symbol == null) return 0.0;
        symbol = symbol.toUpperCase().trim();
        int qty = holdings.getOrDefault(symbol, 0);
        if (qty <= 0) return 0.0;
        double cost = costBasis.getOrDefault(symbol, 0.0);
        return Math.round((cost / qty) * 100.0) / 100.0;
    }

    public Map<String, Integer> getHoldings() {
        return Collections.unmodifiableMap(holdings);
    }

    public Map<String, Double> getCostBasisMap() {
        return Collections.unmodifiableMap(costBasis);
    }

    public double getRealizedProfitLoss() {
        return Math.round(realizedProfitLoss * 100.0) / 100.0;
    }
}
