import React, { useState } from 'react';
import { Trade } from '../../types';

interface TradeHistoryViewProps {
  trades: Trade[];
}

export const TradeHistoryView: React.FC<TradeHistoryViewProps> = ({ trades }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'BUY' | 'SELL'>('ALL');

  const filteredTrades = trades.filter((t) => {
    const matchesSearch =
      t.stockSymbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.tradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      t.stockName?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesType = typeFilter === 'ALL' || t.tradeType === typeFilter;
    return matchesSearch && matchesType;
  });

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Trade History</h1>
          <p className="text-sm text-gray-600">
            Audit log of all completed BUY and SELL transactions executed through your account.
          </p>
        </div>

        {/* Filter & Search */}
        <div className="flex items-center space-x-2">
          <input
            type="text"
            placeholder="Search by Stock or ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded-md text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500 w-48"
          />
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as any)}
            className="px-3 py-1.5 border border-gray-300 rounded-md text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="ALL">All Types</option>
            <option value="BUY">BUY Only</option>
            <option value="SELL">SELL Only</option>
          </select>
        </div>
      </div>

      {/* Clean Trade History Table */}
      <div className="bg-white border border-gray-200 rounded-md shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
              <tr>
                <th className="py-3 px-4 font-semibold">Trade ID</th>
                <th className="py-3 px-4 font-semibold">Stock</th>
                <th className="py-3 px-3 font-semibold">Type</th>
                <th className="py-3 px-3 font-semibold">Quantity</th>
                <th className="py-3 px-3 font-semibold">Price</th>
                <th className="py-3 px-4 font-semibold">Total Amount</th>
                <th className="py-3 px-3 font-semibold">Date</th>
                <th className="py-3 px-3 font-semibold">Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredTrades.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-gray-500 text-sm">
                    No trade records found matching your filters.
                  </td>
                </tr>
              ) : (
                filteredTrades.map((t, idx) => {
                  const isBuy = t.tradeType === 'BUY';
                  // Parse date and time from t.dateTime (format e.g. "2026-09-15 14:32:10")
                  const parts = t.dateTime.split(' ');
                  const dateStr = parts[0] || t.dateTime;
                  const timeStr = parts[1] || '';

                  return (
                    <tr key={`${t.tradeId || 'trade'}-${t.dateTime}-${idx}`} className="hover:bg-gray-50/75">
                      <td className="py-3 px-4 font-mono text-xs text-gray-600">
                        {t.tradeId}
                      </td>
                      <td className="py-3 px-4 font-bold text-gray-900">
                        {t.stockSymbol}
                      </td>
                      <td className="py-3 px-3">
                        <span
                          className={`inline-block px-2.5 py-0.5 rounded text-xs font-semibold ${
                            isBuy ? 'bg-blue-50 text-blue-700' : 'bg-red-50 text-red-700'
                          }`}
                        >
                          {t.tradeType}
                        </span>
                      </td>
                      <td className="py-3 px-3 text-gray-800 font-medium">
                        {t.quantity}
                      </td>
                      <td className="py-3 px-3 text-gray-800">
                        ₹{(t.price ?? 0).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 font-semibold text-gray-900">
                        ₹{(t.totalAmount ?? 0).toFixed(2)}
                      </td>
                      <td className="py-3 px-3 text-gray-600 text-xs whitespace-nowrap">
                        {dateStr}
                      </td>
                      <td className="py-3 px-3 text-gray-500 text-xs whitespace-nowrap font-mono">
                        {timeStr}
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
