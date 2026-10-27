import React, { useState, useEffect } from 'react';
import { Stock, User } from '../../types';
import { api } from '../../services/api';

interface StocksViewProps {
  stocks: Stock[];
  currentUser?: User | null;
  onOpenTrade: (stock: Stock, type: 'BUY' | 'SELL') => void;
}

export const StocksView: React.FC<StocksViewProps> = ({ stocks, currentUser, onOpenTrade }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [exchangeFilter, setExchangeFilter] = useState<'ALL' | 'NSE' | 'BSE'>('ALL');
  const [viewFilter, setViewFilter] = useState<'ALL' | 'WATCHLIST'>('ALL');
  const [watchlistSymbols, setWatchlistSymbols] = useState<Set<string>>(new Set());
  const [isTogglingSymbol, setIsTogglingSymbol] = useState<string | null>(null);

  // Fetch watchlist for current user on mount
  useEffect(() => {
    if (currentUser?.userId) {
      api.getWatchlist(currentUser.userId)
        .then((data) => {
          if (data && Array.isArray(data.watchlist)) {
            setWatchlistSymbols(new Set(data.watchlist));
          }
        })
        .catch((err) => {
          console.warn('Watchlist fetch warning:', err);
        });
    }
  }, [currentUser?.userId]);

  const handleToggleWatchlist = async (symbol: string) => {
    if (!currentUser?.userId || isTogglingSymbol) return;
    setIsTogglingSymbol(symbol);

    // Optimistic UI update
    const nextSet = new Set(watchlistSymbols);
    const wasInWatchlist = nextSet.has(symbol);
    if (wasInWatchlist) {
      nextSet.delete(symbol);
    } else {
      nextSet.add(symbol);
    }
    setWatchlistSymbols(nextSet);

    try {
      const res = await api.toggleWatchlist(symbol, currentUser.userId, 'toggle');
      if (res && Array.isArray(res.watchlist)) {
        setWatchlistSymbols(new Set(res.watchlist));
      }
    } catch (err) {
      console.error('Failed to toggle watchlist:', err);
      // Revert on error
      setWatchlistSymbols(watchlistSymbols);
    } finally {
      setIsTogglingSymbol(null);
    }
  };

  const filteredStocks = stocks.filter((s) => {
    const matchesSearch =
      s.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.name.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesExchange = exchangeFilter === 'ALL' || (s.exchange || 'NSE') === exchangeFilter;
    const matchesWatchlist = viewFilter === 'ALL' || watchlistSymbols.has(s.symbol);
    return matchesSearch && matchesExchange && matchesWatchlist;
  });

  return (
    <div className="space-y-4">
      {/* Header and Search */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
        <div>
          <div className="flex items-center space-x-2">
            <h1 className="text-2xl font-bold text-gray-900 flex items-center space-x-2">
              <span>1. Stock Trading</span>
              <span className="text-xs bg-purple-50 text-purple-700 px-2.5 py-0.5 rounded border border-purple-200 font-mono">
                Java: StockServlet &bull; TradingService
              </span>
            </h1>
          </div>
          <p className="text-sm text-gray-600 mt-1">
            Interface for executing buy and sell trades with current market data across NSE &amp; BSE.
          </p>
        </div>

        <div className="flex items-center space-x-2 w-full sm:w-auto flex-wrap gap-y-2">
          {/* View Filter Tabs: All vs Watchlist */}
          <div className="inline-flex rounded-lg border border-gray-300 p-0.5 bg-gray-50 text-xs font-semibold">
            <button
              onClick={() => setViewFilter('ALL')}
              className={`px-3 py-1 rounded-md transition cursor-pointer ${
                viewFilter === 'ALL'
                  ? 'bg-white text-gray-900 shadow-xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              All Equities ({stocks.length})
            </button>
            <button
              onClick={() => setViewFilter('WATCHLIST')}
              className={`px-3 py-1 rounded-md transition flex items-center space-x-1 cursor-pointer ${
                viewFilter === 'WATCHLIST'
                  ? 'bg-amber-500 text-white shadow-xs'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              <span>⭐ Watchlist</span>
              <span className={`ml-1 px-1.5 py-0.2 rounded-full text-[10px] ${
                viewFilter === 'WATCHLIST' ? 'bg-amber-600 text-white' : 'bg-gray-200 text-gray-700'
              }`}>
                {watchlistSymbols.size}
              </span>
            </button>
          </div>

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
            placeholder="Search symbol or name..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full sm:w-60 px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
      </div>

      {/* Stock Table */}
      <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
              <tr>
                <th className="py-3 px-3 font-semibold text-center w-12">Watch</th>
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
                  <td colSpan={8} className="py-8 text-center text-gray-500 text-sm">
                    {viewFilter === 'WATCHLIST'
                      ? 'No stocks in your watchlist yet. Click the star icon (☆) on any stock to add it.'
                      : `No Indian stocks matching "${searchTerm}" found.`}
                  </td>
                </tr>
              ) : (
                filteredStocks.map((stock) => {
                  const isUp = (stock.priceChange ?? 0) >= 0;
                  const isWatched = watchlistSymbols.has(stock.symbol);

                  return (
                    <tr key={stock.symbol} className="hover:bg-gray-50/75 transition">
                      <td className="py-3 px-3 text-center">
                        <button
                          onClick={() => handleToggleWatchlist(stock.symbol)}
                          disabled={!currentUser?.userId || isTogglingSymbol === stock.symbol}
                          title={isWatched ? 'Remove from Watchlist' : 'Add to Watchlist'}
                          className={`p-1 text-base rounded hover:scale-110 transition cursor-pointer disabled:opacity-50 ${
                            isWatched ? 'text-amber-500' : 'text-slate-300 hover:text-amber-400'
                          }`}
                        >
                          {isWatched ? '⭐' : '☆'}
                        </button>
                      </td>
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
                            className="px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-semibold transition cursor-pointer shadow-xs"
                          >
                            BUY
                          </button>
                          <button
                            onClick={() => onOpenTrade(stock, 'SELL')}
                            className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-semibold transition cursor-pointer shadow-xs"
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
