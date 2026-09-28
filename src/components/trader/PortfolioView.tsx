import React from 'react';
import { Portfolio, Stock } from '../../types';

interface PortfolioViewProps {
  portfolio: Portfolio | null;
  stocks: Stock[];
  onOpenTrade: (stock: Stock, type: 'BUY' | 'SELL') => void;
}

export const PortfolioView: React.FC<PortfolioViewProps> = ({
  portfolio,
  stocks,
  onOpenTrade,
}) => {
  const currentVal = portfolio?.currentPortfolioValue ?? 0;
  const investmentVal = portfolio?.totalInvestmentValue ?? 0;
  const pnl = portfolio?.unrealizedProfitLoss ?? 0;
  const pnlPercent = portfolio?.unrealizedProfitLossPercent ?? 0;
  const cashBalance = portfolio?.cashBalance ?? 0;
  const totalNetWorth = currentVal + cashBalance;

  const holdings = portfolio?.holdings ?? [];
  const isProfit = pnl >= 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="pb-4 border-b border-gray-200">
        <h1 className="text-2xl font-bold text-gray-900">Portfolio</h1>
        <p className="text-sm text-gray-600">
          Your current asset holdings, investment costs, and realized/unrealized profit & loss.
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Total Investment */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Total Investment
          </span>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            ₹{investmentVal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Total cost basis of current holdings
          </div>
        </div>

        {/* Current Value */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Current Value
          </span>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            ₹{currentVal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Cash: ₹{cashBalance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} &bull; Total: ₹{totalNetWorth.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>

        {/* Profit / Loss */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Profit/Loss
          </span>
          <div className={`text-2xl font-bold mt-1 ${isProfit ? 'text-green-600' : 'text-red-600'}`}>
            {isProfit ? '+' : ''}₹{pnl.toFixed(2)}
          </div>
          <div className={`text-xs mt-1 font-medium ${isProfit ? 'text-green-600' : 'text-red-600'}`}>
            {isProfit ? '+' : ''}{pnlPercent.toFixed(2)}% overall return
          </div>
        </div>
      </div>

      {/* Holdings Table */}
      <div className="bg-white border border-gray-200 rounded-md shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-200 flex justify-between items-center">
          <h2 className="text-base font-semibold text-gray-900">
            Portfolio Holdings ({holdings.length})
          </h2>
          <span className="text-xs text-gray-500">
            Real-time market valuation
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
              <tr>
                <th className="py-3 px-4 font-semibold">Stock</th>
                <th className="py-3 px-3 font-semibold">Quantity</th>
                <th className="py-3 px-3 font-semibold">Average Price</th>
                <th className="py-3 px-3 font-semibold">Current Price</th>
                <th className="py-3 px-3 font-semibold">Investment Value</th>
                <th className="py-3 px-3 font-semibold">Current Value</th>
                <th className="py-3 px-4 font-semibold">Profit/Loss</th>
                <th className="py-3 px-4 font-semibold text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {holdings.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-gray-500 text-sm">
                    You do not own any stocks yet. Go to the Stocks page to place a BUY order.
                  </td>
                </tr>
              ) : (
                holdings.map((h, idx) => {
                  const stockObj = stocks.find((s) => s.symbol === h.symbol) || {
                    symbol: h.symbol,
                    name: h.name || h.symbol,
                    currentPrice: h.currentPrice ?? 0,
                    previousPrice: h.currentPrice ?? 0,
                    priceChange: 0,
                    priceChangePercent: 0,
                    availableQuantity: 10000,
                  };
                  const itemProfit = (h.profitLoss ?? 0) >= 0;
                  const avgPrice = h.avgPurchasePrice ?? (h as any).averageBuyPrice ?? 0;
                  const curPrice = h.currentPrice ?? stockObj.currentPrice ?? 0;
                  const investVal = h.investmentValue ?? (h.quantity * avgPrice);
                  const curVal = h.currentValue ?? (h.quantity * curPrice);
                  const pnlVal = h.profitLoss ?? (curVal - investVal);
                  const pnlPct = h.profitLossPercent ?? (investVal > 0 ? (pnlVal / investVal) * 100 : 0);

                  return (
                    <tr key={`${h.symbol}-${idx}`} className="hover:bg-gray-50/75">
                      <td className="py-3 px-4 font-bold text-gray-900">
                        <div>{h.symbol}</div>
                        <div className="text-xs text-gray-500 font-normal">{h.name || stockObj.name}</div>
                      </td>
                      <td className="py-3 px-3 text-gray-800 font-medium">
                        {h.quantity ?? 0}
                      </td>
                      <td className="py-3 px-3 text-gray-800">
                        ₹{avgPrice.toFixed(2)}
                      </td>
                      <td className="py-3 px-3 text-gray-900 font-medium">
                        ₹{curPrice.toFixed(2)}
                      </td>
                      <td className="py-3 px-3 text-gray-800">
                        ₹{investVal.toFixed(2)}
                      </td>
                      <td className="py-3 px-3 font-semibold text-gray-900">
                        ₹{curVal.toFixed(2)}
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                            itemProfit ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                          }`}
                        >
                          {itemProfit ? '+' : ''}₹{pnlVal.toFixed(2)} ({itemProfit ? '+' : ''}
                          {pnlPct.toFixed(2)}%)
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <div className="inline-flex space-x-1.5">
                          <button
                            onClick={() => onOpenTrade(stockObj, 'BUY')}
                            className="px-2.5 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium transition"
                          >
                            BUY
                          </button>
                          <button
                            onClick={() => onOpenTrade(stockObj, 'SELL')}
                            className="px-2.5 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-medium transition"
                          >
                            SELL
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Simple Asset Allocation Bar */}
      {holdings.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <h2 className="text-sm font-semibold text-gray-900 mb-3">
            Asset Allocation by Holding Value
          </h2>
          <div className="w-full h-4 bg-gray-100 rounded-full overflow-hidden flex">
            {holdings.map((h, i) => {
              const colors = ['bg-blue-600', 'bg-emerald-500', 'bg-amber-500', 'bg-indigo-500', 'bg-cyan-500', 'bg-purple-500'];
              const holdingVal = h.currentValue ?? ((h.quantity ?? 0) * (h.currentPrice ?? 0));
              const pct = currentVal > 0 ? (holdingVal / currentVal) * 100 : 0;
              return (
                <div
                  key={`${h.symbol}-${i}`}
                  style={{ width: `${pct}%` }}
                  className={`${colors[i % colors.length]} h-full transition-all`}
                  title={`${h.symbol}: ${(pct || 0).toFixed(1)}%`}
                />
              );
            })}
          </div>
          <div className="flex flex-wrap gap-4 mt-3 text-xs text-gray-600">
            {holdings.map((h, i) => {
              const colors = ['bg-blue-600', 'bg-emerald-500', 'bg-amber-500', 'bg-indigo-500', 'bg-cyan-500', 'bg-purple-500'];
              const holdingVal = h.currentValue ?? ((h.quantity ?? 0) * (h.currentPrice ?? 0));
              const pct = currentVal > 0 ? (holdingVal / currentVal) * 100 : 0;
              return (
                <div key={`${h.symbol}-${i}`} className="flex items-center space-x-1.5">
                  <span className={`w-2.5 h-2.5 rounded-full ${colors[i % colors.length]}`} />
                  <span className="font-medium text-gray-800">{h.symbol}</span>
                  <span className="text-gray-500">({(pct || 0).toFixed(1)}%)</span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
