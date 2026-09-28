import React from 'react';
import { User, NotificationItem } from '../types';

interface NavbarProps {
  currentUser: User | null;
  activeTab: string;
  onSelectTab: (tab: string) => void;
  onLogout: () => void;
  onTriggerTick: () => void;
  isTicking: boolean;
  marketStatus: string;
  notifications: NotificationItem[];
  cashBalance?: number;
}

interface NavTab {
  id: string;
  label: string;
  badge?: number;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentUser,
  activeTab,
  onSelectTab,
  onLogout,
  onTriggerTick,
  isTicking,
  marketStatus,
  notifications,
  cashBalance,
}) => {
  if (!currentUser) return null;

  const unreadAlerts = notifications.filter(n => !n.isRead).length;

  const traderTabs: NavTab[] = [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'stocks', label: 'Stocks' },
    { id: 'portfolio', label: 'Portfolio' },
    { id: 'market-updates', label: 'Market Updates' },
    { id: 'trade-history', label: 'Trade History' },
    { id: 'alerts', label: 'Alerts', badge: unreadAlerts > 0 ? unreadAlerts : undefined },
    { id: 'settings', label: 'Settings' },
  ];

  const adminTabs: NavTab[] = [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'users', label: 'Users' },
    { id: 'stocks', label: 'Stock Management' },
    { id: 'security', label: 'Security' },
    { id: 'system-settings', label: 'System Settings' },
    { id: 'trade-activity', label: 'Trade Activity' },
    { id: 'reports', label: 'Reports' },
  ];

  const tabs = currentUser.role === 'ADMIN' ? adminTabs : traderTabs;

  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-30">
      {/* Top Utility Header */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 border-b border-gray-100 py-2.5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          {/* Logo & Platform Name */}
          <div className="flex items-center space-x-3">
            <span className="text-lg font-bold text-gray-900 tracking-tight">
              Online Stock Trading Platform
            </span>
            <span className="text-xs bg-gray-100 text-gray-700 px-2 py-0.5 rounded border border-gray-200">
              Java Backend
            </span>
            <span
              className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${
                marketStatus === 'OPEN'
                  ? 'bg-green-50 text-green-700 border-green-200'
                  : 'bg-amber-50 text-amber-700 border-amber-200'
              }`}
            >
              <span
                className={`w-2 h-2 rounded-full mr-1.5 ${
                  marketStatus === 'OPEN' ? 'bg-green-500' : 'bg-amber-500'
                }`}
              />
              Market {marketStatus}
            </span>
          </div>

          {/* Right Header Controls */}
          <div className="flex items-center space-x-4 text-sm">
            <button
              onClick={onTriggerTick}
              disabled={isTicking}
              title="Simulate Market Price Fluctuation in Java Engine"
              className="px-2.5 py-1 text-xs border border-gray-300 rounded bg-white text-gray-700 hover:bg-gray-50 transition font-medium disabled:opacity-50"
            >
              {isTicking ? 'Updating...' : 'Simulate Price Tick'}
            </button>

            {currentUser.role === 'TRADER' && cashBalance !== undefined && (
              <div className="text-xs text-gray-700">
                <span>Cash: </span>
                <span className="font-semibold text-gray-900">
                  ₹{cashBalance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </span>
              </div>
            )}

            <div className="text-xs text-gray-600">
              <span>User: </span>
              <span className="font-medium text-gray-900">{currentUser.name}</span>
              <span className="text-gray-500 ml-1">({currentUser.role})</span>
            </div>

            <button
              onClick={onLogout}
              className="text-xs text-red-600 hover:text-red-700 font-medium px-2 py-1 border border-red-200 rounded hover:bg-red-50 transition"
            >
              Logout
            </button>
          </div>
        </div>
      </div>

      {/* Main Navigation Tabs */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <nav className="flex space-x-1 sm:space-x-4 overflow-x-auto py-1 scrollbar-none">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onSelectTab(tab.id)}
                className={`flex items-center whitespace-nowrap px-3 py-2 text-sm font-medium border-b-2 transition ${
                  isActive
                    ? 'border-blue-600 text-blue-600 bg-blue-50/50'
                    : 'border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300'
                }`}
              >
                <span>{tab.label}</span>
                {tab.badge && (
                  <span className="ml-1.5 px-1.5 py-0.2 bg-blue-600 text-white rounded-full text-xs font-semibold">
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>
    </header>
  );
};
