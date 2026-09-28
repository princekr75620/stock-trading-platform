import React from 'react';
import { Stock, Portfolio, Trade, MarketUpdate, User } from '../../types';

interface TraderDashboardProps {
  currentUser: User;
  portfolio: Portfolio | null;
  stocks: Stock[];
  trades: Trade[];
  marketUpdates: MarketUpdate[];
  onNavigate: (tab: string) => void;
  onOpenTrade: (stock: Stock, type: 'BUY' | 'SELL') => void;
}

export const TraderDashboard: React.FC<TraderDashboardProps> = ({
  currentUser,
  portfolio,
  stocks,
  trades,
  marketUpdates,
  onNavigate,
  onOpenTrade,
}) => {
  const currentVal = portfolio?.currentPortfolioValue ?? 0;
  const investmentVal = portfolio?.totalInvestmentValue ?? 0;
  const pnl = portfolio?.unrealizedProfitLoss ?? 0;
  const pnlPercent = portfolio?.unrealizedProfitLossPercent ?? 0;
  const holdingsCount = portfolio?.holdingsCount ?? portfolio?.holdings?.length ?? 0;

  const isProfit = pnl >= 0;

  return (
    <div className="space-y-6">
      {/* Top Welcome */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-gray-200">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            Welcome, Trader
          </h1>
          <p className="text-sm text-gray-600">
            Overview of your current stock holdings, recent trade activity, and market movement.
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={() => onNavigate('stocks')}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition"
          >
            Trade Stocks
          </button>
          <button
            onClick={() => onNavigate('portfolio')}
            className="px-4 py-2 border border-gray-300 hover:bg-gray-50 text-gray-700 text-sm font-medium rounded-md transition"
          >
            View Portfolio
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Portfolio Value */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Portfolio Value
          </span>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            ₹{currentVal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Cash: ₹{(portfolio?.cashBalance ?? currentUser.cashBalance ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
        </div>

        {/* Total Investment */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Total Investment
          </span>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            ₹{investmentVal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Capital allocated in equities
          </div>
        </div>

        {/* Profit/Loss */}
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

        {/* Total Holdings */}
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wider">
            Total Holdings
          </span>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            {holdingsCount}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Active stock positions
          </div>
        </div>
      </div>

      {/* Main Grid: Recent Trades and Market Updates */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Trades Table */}
        <div className="bg-white border border-gray-200 rounded-md shadow-sm">
          <div className="p-4 border-b border-gray-200 flex items-center justify-between">
            <h2 className="text-base font-semibold text-gray-900">
              Recent Trades
            </h2>
            <button
              onClick={() => onNavigate('trade-history')}
              className="text-xs text-blue-600 hover:text-blue-800 font-medium"
            >
              View All Trades &rarr;
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                <tr>
                  <th className="py-2.5 px-4 font-semibold">Stock</th>
                  <th className="py-2.5 px-3 font-semibold">Type</th>
                  <th className="py-2.5 px-3 font-semibold">Qty</th>
                  <th className="py-2.5 px-3 font-semibold">Price</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {trades.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-sm text-gray-500">
                      No trades recorded yet. Start by buying a stock.
                    </td>
                  </tr>
                ) : (
                  trades.slice(0, 5).map((t, idx) => {
                    const isBuy = t.tradeType === 'BUY';
                    return (
                      <tr key={`${t.tradeId || 'trade'}-${t.dateTime}-${idx}`} className="hover:bg-gray-50/75">
                        <td className="py-2.5 px-4 font-medium text-gray-900">
                          <div>{t.stockSymbol}</div>
                          <div className="text-xs text-gray-500">{t.dateTime.split(' ')[0]}</div>
                        </td>
                        <td className="py-2.5 px-3">
                          <span
                            className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                              isBuy ? 'bg-blue-50 text-blue-700' : 'bg-red-50 text-red-700'
                            }`}
                          >
                            {t.tradeType}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-gray-800">{t.quantity}</td>
                        <td className="py-2.5 px-3 text-gray-800">₹{(t.price ?? 0).toFixed(2)}</td>
                        <td className="py-2.5 px-4 font-semibold text-right text-gray-900">
                          ₹{(t.totalAmount ?? 0).toFixed(2)}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Market Updates */}
        <div className="bg-white border border-gray-200 rounded-md shadow-sm">
          <div className="p-4 border-b border-gray-200 flex items-center justify-between">
            <h2 className="text-base font-semibold text-gray-900">
              Market Updates
            </h2>
            <button
              onClick={() => onNavigate('market-updates')}
              className="text-xs text-blue-600 hover:text-blue-800 font-medium"
            >
              View All Updates &rarr;
            </button>
          </div>

          <div className="p-4 divide-y divide-gray-100">
            {marketUpdates.length === 0 ? (
              <p className="py-6 text-center text-sm text-gray-500">
                No market updates currently available.
              </p>
            ) : (
              marketUpdates.slice(0, 5).map((up, idx) => {
                const isPositive = up.priceDelta >= 0;
                return (
                  <div key={`${up.updateId || 'up'}-${idx}`} className="py-3 first:pt-0 last:pb-0">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-semibold text-gray-900">
                        {up.stockSymbol} <span className="font-normal text-gray-500">({up.stockName})</span>
                      </span>
                      <span className={`font-semibold text-xs ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
                        ${(up.currentPrice ?? 0).toFixed(2)} ({isPositive ? '+' : ''}{(up.percentChange ?? 0).toFixed(2)}%)
                      </span>
                    </div>
                    <p className="text-xs text-gray-700 mt-1">{up.headline}</p>
                    <span className="text-[11px] text-gray-400 mt-0.5 block">{up.timestamp}</span>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* Quick Trade Stocks Bar */}
      <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold text-gray-900">
            Featured Available Stocks
          </h2>
          <button
            onClick={() => onNavigate('stocks')}
            className="text-xs text-blue-600 hover:text-blue-800 font-medium"
          >
            Go to Full Stock Table &rarr;
          </button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {stocks.slice(0, 4).map((s) => {
            const isUp = (s.priceChange ?? 0) >= 0;
            return (
              <div key={s.symbol} className="border border-gray-200 rounded p-3 bg-gray-50/50">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-bold text-gray-900">{s.symbol}</span>
                    <p className="text-xs text-gray-500 truncate max-w-[140px]">{s.name}</p>
                  </div>
                  <span className={`text-xs font-semibold ${isUp ? 'text-green-600' : 'text-red-600'}`}>
                    {isUp ? '+' : ''}{(s.priceChangePercent ?? 0).toFixed(2)}%
                  </span>
                </div>
                <div className="mt-2 flex items-center justify-between">
                  <span className="text-base font-bold text-gray-900">₹{(s.currentPrice ?? 0).toFixed(2)}</span>
                  <div className="space-x-1.5">
                    <button
                      onClick={() => onOpenTrade(s, 'BUY')}
                      className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium"
                    >
                      BUY
                    </button>
                    <button
                      onClick={() => onOpenTrade(s, 'SELL')}
                      className="px-2 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-medium"
                    >
                      SELL
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
