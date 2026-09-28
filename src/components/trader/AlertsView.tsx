import React, { useState } from 'react';
import { NotificationItem } from '../../types';

interface AlertsViewProps {
  notifications: NotificationItem[];
  onMarkRead: (id?: string) => void;
}

export const AlertsView: React.FC<AlertsViewProps> = ({
  notifications,
  onMarkRead,
}) => {
  const [filterType, setFilterType] = useState<'ALL' | 'TRADE' | 'MARKET' | 'SYSTEM'>('ALL');

  const filtered = notifications.filter((n) => {
    if (filterType === 'ALL') return true;
    if (filterType === 'TRADE') return n.type === 'TRADE_EXECUTION';
    if (filterType === 'MARKET') return n.type === 'PRICE_ALERT' || n.type === 'MARKET_VOLATILITY';
    if (filterType === 'SYSTEM') return n.type === 'SYSTEM_ANNOUNCEMENT' || n.type === 'SECURITY_EVENT';
    return true;
  });

  const unreadCount = notifications.filter(n => !n.isRead).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Alerts &amp; Notifications</h1>
          <p className="text-sm text-gray-600">
            System notifications, trade execution notices, and real-time market updates.
          </p>
        </div>

        <div className="flex items-center space-x-3">
          {unreadCount > 0 && (
            <button
              onClick={() => onMarkRead()}
              className="px-3 py-1.5 border border-gray-300 rounded text-xs font-medium text-gray-700 hover:bg-gray-50 transition"
            >
              Mark All as Read ({unreadCount})
            </button>
          )}

          <div className="inline-flex rounded-md shadow-sm border border-gray-300 overflow-hidden text-xs">
            <button
              onClick={() => setFilterType('ALL')}
              className={`px-3 py-1.5 font-medium transition ${
                filterType === 'ALL' ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilterType('TRADE')}
              className={`px-3 py-1.5 font-medium border-l border-gray-300 transition ${
                filterType === 'TRADE' ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              Trade Alerts
            </button>
            <button
              onClick={() => setFilterType('MARKET')}
              className={`px-3 py-1.5 font-medium border-l border-gray-300 transition ${
                filterType === 'MARKET' ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              Market Alerts
            </button>
            <button
              onClick={() => setFilterType('SYSTEM')}
              className={`px-3 py-1.5 font-medium border-l border-gray-300 transition ${
                filterType === 'SYSTEM' ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'
              }`}
            >
              System Alerts
            </button>
          </div>
        </div>
      </div>

      {/* Notification List */}
      <div className="space-y-3">
        {filtered.length === 0 ? (
          <div className="bg-white border border-gray-200 rounded-md p-8 text-center text-sm text-gray-500">
            No notifications found in this category.
          </div>
        ) : (
          filtered.map((item, idx) => {
            const isTrade = item.type === 'TRADE_EXECUTION';
            const isMarket = item.type === 'PRICE_ALERT' || item.type === 'MARKET_VOLATILITY';

            return (
              <div
                key={`${item.id || 'notif'}-${idx}`}
                className={`p-4 rounded-md border transition ${
                  item.isRead
                    ? 'bg-white border-gray-200 text-gray-800'
                    : 'bg-blue-50/40 border-blue-200 text-gray-900 font-medium'
                }`}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center space-x-2">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider ${
                          isTrade
                            ? 'bg-blue-100 text-blue-800'
                            : isMarket
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {isTrade ? 'Trade Alert' : isMarket ? 'Market Alert' : 'System Notice'}
                      </span>
                      <h3 className="text-sm font-bold text-gray-900">{item.title}</h3>
                      {!item.isRead && (
                        <span className="w-2 h-2 rounded-full bg-blue-600" title="Unread" />
                      )}
                    </div>
                    <p className="text-xs text-gray-600 font-normal leading-relaxed">
                      {item.message}
                    </p>
                  </div>

                  <div className="flex flex-col items-end space-y-2 whitespace-nowrap">
                    <span className="text-[11px] text-gray-400">{item.timestamp}</span>
                    {!item.isRead && (
                      <button
                        onClick={() => onMarkRead(item.id)}
                        className="text-xs text-blue-600 hover:text-blue-800 underline font-normal"
                      >
                        Mark as read
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
