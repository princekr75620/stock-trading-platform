package model;

import util.MoneyUtil;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Trader's investment portfolio.
 * Uses BigDecimal financial arithmetic via MoneyUtil for weighted average cost basis,
 * market valuation, and realized/unrealized P&L without floating-point rounding drift.
 */
public class Portfolio implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traderId;
    private final Map<String, Integer> holdings;
    private final Map<String, BigDecimal> costBasis;
    private BigDecimal realizedProfitLoss;

    public Portfolio(String traderId) {
        this.traderId = traderId;
        this.holdings = new HashMap<>();
        this.costBasis = new HashMap<>();
        this.realizedProfitLoss = MoneyUtil.ZERO;
    }

    public Portfolio(String traderId, Map<String, Integer> holdings, Map<String, Double> costBasis, double realizedProfitLoss) {
        this.traderId = traderId;
        this.holdings = (holdings != null) ? new HashMap<>(holdings) : new HashMap<>();
        this.costBasis = new HashMap<>();
        if (costBasis != null) {
            for (Map.Entry<String, Double> e : costBasis.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    this.costBasis.put(e.getKey().toUpperCase().trim(), MoneyUtil.of(e.getValue()));
                }
            }
        }
        this.realizedProfitLoss = MoneyUtil.of(realizedProfitLoss);
    }

    public synchronized void recordBuy(String symbol, int quantity, double price) {
        if (symbol == null || quantity <= 0) {
            throw new IllegalArgumentException("Symbol and positive quantity are required.");
        }
        String sym = symbol.toUpperCase().trim();
        int currentQty = holdings.getOrDefault(sym, 0);
        BigDecimal currentCost = costBasis.getOrDefault(sym, BigDecimal.ZERO);

        BigDecimal addedCost = MoneyUtil.ofExact(price).multiply(BigDecimal.valueOf(quantity));
        holdings.put(sym, currentQty + quantity);
        costBasis.put(sym, currentCost.add(addedCost));
    }

    public synchronized double recordSell(String symbol, int quantity, double sellPrice) {
        if (symbol == null || quantity <= 0) {
            throw new IllegalArgumentException("Symbol and positive quantity are required.");
        }
        String sym = symbol.toUpperCase().trim();
        int currentQty = holdings.getOrDefault(sym, 0);
        if (currentQty < quantity) {
            throw new IllegalArgumentException("Cannot sell more shares than currently owned!");
        }

        BigDecimal currentCost = costBasis.getOrDefault(sym, BigDecimal.ZERO);
        BigDecimal avgCostPerShare = currentQty > 0
                ? currentCost.divide(BigDecimal.valueOf(currentQty), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal costOfSoldShares = avgCostPerShare.multiply(BigDecimal.valueOf(quantity));
        BigDecimal saleRevenue = MoneyUtil.ofExact(sellPrice).multiply(BigDecimal.valueOf(quantity));
        BigDecimal saleProfit = saleRevenue.subtract(costOfSoldShares);

        this.realizedProfitLoss = this.realizedProfitLoss.add(saleProfit);

        int remainingQty = currentQty - quantity;
        if (remainingQty == 0) {
            holdings.remove(sym);
            costBasis.remove(sym);
        } else {
            holdings.put(sym, remainingQty);
            BigDecimal remainingCost = currentCost.subtract(costOfSoldShares);
            costBasis.put(sym, remainingCost.compareTo(BigDecimal.ZERO) > 0 ? remainingCost : BigDecimal.ZERO);
        }

        return MoneyUtil.round(saleProfit).doubleValue();
    }

    public synchronized double recordSell(String symbol, int quantity) {
        return recordSell(symbol, quantity, getAverageBuyPrice(symbol));
    }

    public int getQuantity(String symbol) {
        if (symbol == null) return 0;
        return holdings.getOrDefault(symbol.toUpperCase().trim(), 0);
    }

    public double getCostBasis(String symbol) {
        if (symbol == null) return 0.0;
        return MoneyUtil.round(costBasis.getOrDefault(symbol.toUpperCase().trim(), BigDecimal.ZERO)).doubleValue();
    }

    public double getAverageBuyPrice(String symbol) {
        if (symbol == null) return 0.0;
        String sym = symbol.toUpperCase().trim();
        int qty = holdings.getOrDefault(sym, 0);
        if (qty <= 0) return 0.0;
        BigDecimal cost = costBasis.getOrDefault(sym, BigDecimal.ZERO);
        return cost.divide(BigDecimal.valueOf(qty), MoneyUtil.CURRENCY_SCALE, MoneyUtil.ROUNDING).doubleValue();
    }

    public double getTotalInvestmentValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal cost : costBasis.values()) {
            if (cost != null) {
                total = total.add(cost);
            }
        }
        return MoneyUtil.round(total).doubleValue();
    }

    public double getCurrentPortfolioValue(Map<String, Stock> marketStocks) {
        BigDecimal total = BigDecimal.ZERO;
        if (marketStocks == null) return 0.0;
        for (Map.Entry<String, Integer> entry : holdings.entrySet()) {
            String sym = entry.getKey();
            int qty = entry.getValue();
            Stock stock = marketStocks.get(sym);
            BigDecimal price = (stock != null && stock.getCurrentPrice() > 0)
                    ? MoneyUtil.ofExact(stock.getCurrentPrice())
                    : BigDecimal.ZERO;
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        return MoneyUtil.round(total).doubleValue();
    }

    public double getUnrealizedProfitLoss(Map<String, Stock> marketStocks) {
        BigDecimal curVal = MoneyUtil.of(getCurrentPortfolioValue(marketStocks));
        BigDecimal investVal = MoneyUtil.of(getTotalInvestmentValue());
        return MoneyUtil.round(curVal.subtract(investVal)).doubleValue();
    }

    public double getUnrealizedProfitLossPercent(Map<String, Stock> marketStocks) {
        BigDecimal investVal = MoneyUtil.of(getTotalInvestmentValue());
        if (investVal.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        BigDecimal pnl = MoneyUtil.of(getUnrealizedProfitLoss(marketStocks));
        return pnl.multiply(MoneyUtil.HUNDRED)
                .divide(investVal, MoneyUtil.CURRENCY_SCALE, MoneyUtil.ROUNDING)
                .doubleValue();
    }

    public String getTraderId() {
        return traderId;
    }

    public String getUserId() {
        return traderId;
    }

    public double getAveragePrice(String symbol) {
        return getAverageBuyPrice(symbol);
    }

    public Map<String, Integer> getHoldings() {
        return Collections.unmodifiableMap(holdings);
    }

    public Map<String, Double> getCostBasisMap() {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : costBasis.entrySet()) {
            result.put(e.getKey(), MoneyUtil.round(e.getValue()).doubleValue());
        }
        return Collections.unmodifiableMap(result);
    }

    public double getRealizedProfitLoss() {
        return MoneyUtil.round(realizedProfitLoss).doubleValue();
    }
}
