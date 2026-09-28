import React, { useState } from 'react';
import { Stock } from '../../types';

interface StocksViewProps {
  stocks: Stock[];
  onOpenTrade: (stock: Stock, type: 'BUY' | 'SELL') => void;
}

export const StocksView: React.FC<StocksViewProps> = ({ stocks, onOpenTrade }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [exchangeFilter, setExchangeFilter] = useState<'ALL' | 'NSE' | 'BSE'>('ALL');

  const filteredStocks = stocks.filter((s) => {
    const matchesSearch =
      s.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.name.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesExchange = exchangeFilter === 'ALL' || (s.exchange || 'NSE') === exchangeFilter;
    return matchesSearch && matchesExchange;
  });

  return (
    <div className="space-y-4">
      {/* Header and Search */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
        <div>
          <div className="flex items-center space-x-2">
            <h1 className="text-2xl font-bold text-gray-900">Indian Stock Market</h1>
            <span className="px-2 py-0.5 text-xs font-semibold rounded bg-amber-50 text-amber-700 border border-amber-200">
              Demo / Simulated Prices
            </span>
          </div>
          <p className="text-sm text-gray-600 mt-1">
            Listed equities across NSE (National Stock Exchange) and BSE (Bombay Stock Exchange) in Indian Rupees (₹).
          </p>
        </div>

        <div className="flex items-center space-x-2 w-full sm:w-auto">
          <select
            value={exchangeFilter}
            onChange={(e) => setExchangeFilter(e.target.value as any)}
            className="px-2.5 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:ring-1 focus:ring-blue-500 bg-white"
          >
            <option value="ALL">All Exchanges</option>
            <option value="NSE">NSE Only</option>
            <option value="BSE">BSE Only</option>
          </select>
          <input
            type="text"
            placeholder="Search by symbol (e.g. RELIANCE, TCS)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full sm:w-72 px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
      </div>

      {/* Stock Table */}
      <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
              <tr>
                <th className="py-3 px-3 font-semibold">Exch</th>
                <th className="py-3 px-4 font-semibold">Ticker Symbol</th>
                <th className="py-3 px-4 font-semibold">Company Name</th>
                <th className="py-3 px-4 font-semibold text-right">Market Price</th>
                <th className="py-3 px-4 font-semibold text-right">Price Change</th>
                <th className="py-3 px-4 font-semibold text-right">Available Float</th>
                <th className="py-3 px-4 font-semibold text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredStocks.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-gray-500 text-sm">
                    No Indian stocks matching "{searchTerm}" found.
                  </td>
                </tr>
              ) : (
                filteredStocks.map((stock) => {
                  const isUp = (stock.priceChange ?? 0) >= 0;
                  return (
                    <tr key={stock.symbol} className="hover:bg-gray-50/75">
                      <td className="py-3 px-3">
                        <span className="inline-block px-1.5 py-0.5 rounded text-[11px] font-semibold bg-gray-100 text-gray-700 border border-gray-200 font-mono">
                          {stock.exchange || 'NSE'}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-bold text-gray-900 font-mono">
                        {stock.symbol}
                      </td>
                      <td className="py-3 px-4 font-medium text-gray-800">
                        {stock.name}
                      </td>
                      <td className="py-3 px-4 font-semibold text-right text-gray-900 font-mono">
                        ₹{(stock.currentPrice ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <span
                          className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                            isUp ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                          }`}
                        >
                          {isUp ? '+' : ''}₹{(stock.priceChange ?? 0).toFixed(2)} ({isUp ? '+' : ''}
                          {(stock.priceChangePercent ?? 0).toFixed(2)}%)
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right text-gray-600 font-mono">
                        {stock.availableQuantity.toLocaleString('en-IN')} shares
                      </td>
                      <td className="py-3 px-4 text-center">
                        <div className="inline-flex space-x-2">
                          <button
                            onClick={() => onOpenTrade(stock, 'BUY')}
                            className="px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-semibold transition"
                          >
                            BUY
                          </button>
                          <button
                            onClick={() => onOpenTrade(stock, 'SELL')}
                            className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-semibold transition"
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
    </div>
  );
};
