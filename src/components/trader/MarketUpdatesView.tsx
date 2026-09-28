import React, { useState } from 'react';
import { MarketUpdate, Stock } from '../../types';

interface MarketUpdatesViewProps {
  marketUpdates: MarketUpdate[];
  stocks: Stock[];
  onOpenTrade: (stock: Stock, type: 'BUY' | 'SELL') => void;
  onTriggerTick: () => void;
  isTicking: boolean;
}

export const MarketUpdatesView: React.FC<MarketUpdatesViewProps> = ({
  marketUpdates,
  stocks,
  onOpenTrade,
  onTriggerTick,
  isTicking,
}) => {
  const [filterSymbol, setFilterSymbol] = useState<string>('ALL');

  const filteredUpdates = filterSymbol === 'ALL'
    ? marketUpdates
    : marketUpdates.filter((u) => u.stockSymbol === filterSymbol);

  const availableSymbols = Array.from(new Set(marketUpdates.map((u) => u.stockSymbol)));

  return (
    <div className="space-y-6">
      {/* Header and Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Market Updates</h1>
          <p className="text-sm text-gray-600">
            Real-time corporate announcements, volatility alerts, and price change feeds.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={onTriggerTick}
            disabled={isTicking}
            className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-md text-xs font-medium transition disabled:opacity-50"
          >
            {isTicking ? 'Simulating...' : 'Simulate Market Movement'}
          </button>
          <select
            value={filterSymbol}
            onChange={(e) => setFilterSymbol(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded-md text-xs text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="ALL">All Stocks</option>
            {availableSymbols.map((sym) => (
              <option key={sym} value={sym}>{sym}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Clean Table of Market Updates */}
      <div className="bg-white border border-gray-200 rounded-md shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
              <tr>
                <th className="py-3 px-4 font-semibold">Stock</th>
                <th className="py-3 px-4 font-semibold">Current Price</th>
                <th className="py-3 px-4 font-semibold">Price Change</th>
                <th className="py-3 px-6 font-semibold">Market Update</th>
                <th className="py-3 px-4 font-semibold">Date/Time</th>
                <th className="py-3 px-4 font-semibold text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredUpdates.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-gray-500 text-sm">
                    No market updates recorded yet. Click "Simulate Market Movement" to generate updates.
                  </td>
                </tr>
              ) : (
                filteredUpdates.map((update, idx) => {
                  const isUp = (update.priceDelta ?? 0) >= 0;
                  const stockObj = stocks.find((s) => s.symbol === update.stockSymbol);
                  return (
                    <tr key={`${update.updateId || 'upd'}-${idx}`} className="hover:bg-gray-50/75">
                      <td className="py-3 px-4 font-bold text-gray-900">
                        <div>{update.stockSymbol}</div>
                        <div className="text-xs text-gray-500 font-normal">{update.stockName}</div>
                      </td>
                      <td className="py-3 px-4 font-semibold text-gray-900">
                        ${(update.currentPrice ?? 0).toFixed(2)}
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                            isUp ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                          }`}
                        >
                          {isUp ? '+' : ''}${(update.priceDelta ?? 0).toFixed(2)} ({isUp ? '+' : ''}
                          {(update.percentChange ?? 0).toFixed(2)}%)
                        </span>
                      </td>
                      <td className="py-3 px-6 text-gray-800 text-xs leading-relaxed max-w-md">
                        {update.headline}
                      </td>
                      <td className="py-3 px-4 text-xs text-gray-500 whitespace-nowrap">
                        {update.timestamp}
                      </td>
                      <td className="py-3 px-4 text-center">
                        {stockObj && (
                          <div className="inline-flex space-x-1.5">
                            <button
                              onClick={() => onOpenTrade(stockObj, 'BUY')}
                              className="px-2.5 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium"
                            >
                              BUY
                            </button>
                            <button
                              onClick={() => onOpenTrade(stockObj, 'SELL')}
                              className="px-2.5 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-medium"
                            >
                              SELL
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
