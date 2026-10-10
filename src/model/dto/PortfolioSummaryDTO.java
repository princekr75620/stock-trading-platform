package model.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing aggregate portfolio statistics and holdings breakdown.
 */
public class PortfolioSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traderId;
    private double cashBalance;
    private double currentPortfolioValue;
    private double totalInvestmentValue;
    private double unrealizedProfitLoss;
    private double unrealizedProfitLossPercent;
    private double totalNetWorth;
    private int holdingsCount;
    private List<HoldingItemDTO> holdings;

    public PortfolioSummaryDTO() {
        this.holdings = new ArrayList<>();
    }

    public PortfolioSummaryDTO(String traderId, double cashBalance, double currentPortfolioValue, double totalInvestmentValue) {
        this.traderId = traderId;
        this.cashBalance = Math.round(cashBalance * 100.0) / 100.0;
        this.currentPortfolioValue = Math.round(currentPortfolioValue * 100.0) / 100.0;
        this.totalInvestmentValue = Math.round(totalInvestmentValue * 100.0) / 100.0;
        this.unrealizedProfitLoss = Math.round((currentPortfolioValue - totalInvestmentValue) * 100.0) / 100.0;
        this.unrealizedProfitLossPercent = totalInvestmentValue > 0
                ? Math.round((unrealizedProfitLoss / totalInvestmentValue) * 10000.0) / 100.0
                : 0.0;
        this.totalNetWorth = Math.round((cashBalance + currentPortfolioValue) * 100.0) / 100.0;
        this.holdings = new ArrayList<>();
        this.holdingsCount = 0;
    }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }

    public double getCashBalance() { return cashBalance; }
    public void setCashBalance(double cashBalance) { this.cashBalance = cashBalance; }

    public double getCurrentPortfolioValue() { return currentPortfolioValue; }
    public void setCurrentPortfolioValue(double currentPortfolioValue) { this.currentPortfolioValue = currentPortfolioValue; }

    public double getTotalInvestmentValue() { return totalInvestmentValue; }
    public void setTotalInvestmentValue(double totalInvestmentValue) { this.totalInvestmentValue = totalInvestmentValue; }

    public double getUnrealizedProfitLoss() { return unrealizedProfitLoss; }
    public void setUnrealizedProfitLoss(double unrealizedProfitLoss) { this.unrealizedProfitLoss = unrealizedProfitLoss; }

    public double getUnrealizedProfitLossPercent() { return unrealizedProfitLossPercent; }
    public void setUnrealizedProfitLossPercent(double unrealizedProfitLossPercent) { this.unrealizedProfitLossPercent = unrealizedProfitLossPercent; }

    public double getTotalNetWorth() { return totalNetWorth; }
    public void setTotalNetWorth(double totalNetWorth) { this.totalNetWorth = totalNetWorth; }

    public int getHoldingsCount() { return holdingsCount; }
    public void setHoldingsCount(int holdingsCount) { this.holdingsCount = holdingsCount; }

    public List<HoldingItemDTO> getHoldings() { return holdings; }
    public void setHoldings(List<HoldingItemDTO> holdings) {
        this.holdings = holdings;
        this.holdingsCount = holdings != null ? holdings.size() : 0;
    }

    public void addHolding(HoldingItemDTO item) {
        if (this.holdings == null) this.holdings = new ArrayList<>();
        this.holdings.add(item);
        this.holdingsCount = this.holdings.size();
    }

    public static class HoldingItemDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String symbol;
        private String name;
        private int quantity;
        private double avgPurchasePrice;
        private double currentPrice;
        private double investmentValue;
        private double currentValue;
        private double profitLoss;
        private double profitLossPercent;

        public HoldingItemDTO(String symbol, String name, int quantity, double avgPurchasePrice, double currentPrice) {
            this.symbol = symbol;
            this.name = name;
            this.quantity = quantity;
            this.avgPurchasePrice = avgPurchasePrice;
            this.currentPrice = currentPrice;
            this.investmentValue = Math.round(quantity * avgPurchasePrice * 100.0) / 100.0;
            this.currentValue = Math.round(quantity * currentPrice * 100.0) / 100.0;
            this.profitLoss = Math.round((currentValue - investmentValue) * 100.0) / 100.0;
            this.profitLossPercent = investmentValue > 0
                    ? Math.round((profitLoss / investmentValue) * 10000.0) / 100.0
                    : 0.0;
        }

        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public double getAvgPurchasePrice() { return avgPurchasePrice; }
        public double getCurrentPrice() { return currentPrice; }
        public double getInvestmentValue() { return investmentValue; }
        public double getCurrentValue() { return currentValue; }
        public double getProfitLoss() { return profitLoss; }
        public double getProfitLossPercent() { return profitLossPercent; }
    }
}
