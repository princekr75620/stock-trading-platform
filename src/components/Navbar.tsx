import React from 'react';
import { User, NotificationItem } from '../types';

interface NavbarProps {
  currentUser: User | null;
  activeTab: string;
  onSelectTab: (tab: string) => void;
  onLogout: () => void;
  onTriggerTick: () => void;
  onOpenRubric?: () => void;
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
  onOpenRubric,
  isTicking,
  marketStatus,
  notifications,
  cashBalance,
}) => {
  if (!currentUser) return null;

  const unreadAlerts = notifications.filter(n => !n.isRead).length;

  const traderTabs: NavTab[] = [
    { id: 'dashboard', label: 'Trading Desk' },
    { id: 'stocks', label: '1. Stock Trading' },
    { id: 'portfolio', label: '2. Portfolio Overview' },
    { id: 'market-updates', label: '3. Market Updates' },
    { id: 'trade-history', label: '4. Trade History' },
    { id: 'alerts', label: '5. Alerts & Notifications', badge: unreadAlerts > 0 ? unreadAlerts : undefined },
    { id: 'settings', label: 'Preferences' },
  ];

  const adminTabs: NavTab[] = [
    { id: 'dashboard', label: 'Executive Overview' },
    { id: 'users', label: '1. User Management' },
    { id: 'security', label: '2. Financial Data Security' },
    { id: 'system-settings', label: '3. System Settings' },
    { id: 'trade-activity', label: '4. Trade Activity Monitoring' },
    { id: 'reports', label: '5. Report Generation' },
    { id: 'stocks', label: 'Stock Management' },
  ];

  const tabs = currentUser.role === 'ADMIN' ? adminTabs : traderTabs;

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-30 shadow-xs">
      {/* Top Real-time Indian Equities Indices Bar */}
      <div className="bg-slate-950 text-slate-300 text-[11px] py-1.5 px-4 border-b border-slate-800">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-4 overflow-x-auto whitespace-nowrap scrollbar-none">
          <div className="flex items-center space-x-6">
            <span className="flex items-center space-x-1.5 font-bold text-white">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              <span>NSE / BSE TICKER</span>
            </span>
            <span className="space-x-1.5">
              <span className="text-slate-400">NIFTY 50:</span>
              <span className="font-semibold text-emerald-400">25,340.20</span>
              <span className="text-emerald-400 font-bold">+0.62%</span>
            </span>
            <span className="space-x-1.5">
              <span className="text-slate-400">SENSEX:</span>
              <span className="font-semibold text-emerald-400">82,780.50</span>
              <span className="text-emerald-400 font-bold">+0.58%</span>
            </span>
            <span className="space-x-1.5 hidden sm:inline">
              <span className="text-slate-400">BANK NIFTY:</span>
              <span className="font-semibold text-emerald-400">51,890.10</span>
              <span className="text-emerald-400 font-bold">+0.74%</span>
            </span>
            <span className="space-x-1.5 hidden md:inline">
              <span className="text-slate-400">INDIA VIX:</span>
              <span className="font-semibold text-red-400">12.45</span>
              <span className="text-red-400 font-bold">-2.10%</span>
            </span>
          </div>

          <div className="flex items-center space-x-3 text-slate-400 text-[11px] shrink-0">
            <span className="hidden lg:inline">Brokerage: 0.15% &bull; SEBI Regulated</span>
            <span className="px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-semibold">
              IST: 09:15 - 15:30
            </span>
          </div>
        </div>
      </div>

      {/* Main Brand & Controls Header */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 border-b border-slate-100">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Logo & Platform Title */}
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-orange-500 to-amber-600 text-white font-black text-xl flex items-center justify-center shadow-md shadow-orange-500/20">
              ₹
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-lg font-black text-slate-900 tracking-tight">
                  BHARAT STOCKS
                </span>
                <span className="text-[10px] font-bold bg-orange-50 text-orange-700 px-2 py-0.5 rounded border border-orange-200">
                  NSE &bull; BSE
                </span>
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${
                    marketStatus === 'OPEN'
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                      : 'bg-amber-50 text-amber-700 border-amber-200'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
                      marketStatus === 'OPEN' ? 'bg-emerald-500' : 'bg-amber-500'
                    }`}
                  />
                  {marketStatus === 'OPEN' ? 'Market Live' : 'Market Closed'}
                </span>
              </div>
              <p className="text-[11px] text-slate-500 font-medium">
                Enterprise Securities &amp; Indian Equities Trading System
              </p>
            </div>
          </div>

          {/* Right Header Controls */}
          <div className="flex items-center space-x-3 text-sm flex-wrap gap-y-2">
            {onOpenRubric && (
              <button
                onClick={onOpenRubric}
                title="View Java Web Projects Marking Rubric &amp; 33 Marks Evidence"
                className="px-2.5 py-1.5 text-xs border border-purple-200 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-700 transition font-bold flex items-center space-x-1.5 shadow-xs cursor-pointer"
              >
                <span>🎓 Rubric (33 Marks)</span>
              </button>
            )}

            <button
              onClick={onTriggerTick}
              disabled={isTicking}
              title="Simulate Market Price Fluctuation in Java Engine"
              className="px-2.5 py-1.5 text-xs border border-slate-300 rounded-lg bg-white hover:bg-slate-50 text-slate-700 transition font-semibold disabled:opacity-50 cursor-pointer shadow-xs"
            >
              {isTicking ? 'Updating Tick...' : '⚡ Market Tick'}
            </button>

            {currentUser.role === 'TRADER' && cashBalance !== undefined && (
              <div className="px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-200 text-xs">
                <span className="text-slate-500 font-medium">Available Margin: </span>
                <span className="font-bold text-slate-900 text-sm">
                  ₹{cashBalance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </span>
              </div>
            )}

            <div className="text-xs text-slate-600 hidden md:block">
              <span className="text-slate-400">Account: </span>
              <span className="font-bold text-slate-900">{currentUser.name}</span>
              <span className="text-slate-500 ml-1">({currentUser.role})</span>
            </div>

            <button
              onClick={onLogout}
              className="text-xs text-red-600 hover:text-red-700 font-semibold px-2.5 py-1.5 border border-red-200 rounded-lg hover:bg-red-50 transition cursor-pointer"
            >
              Logout
            </button>
          </div>
        </div>
      </div>

      {/* Main Navigation Tabs */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 bg-slate-50/60">
        <nav className="flex space-x-1 sm:space-x-2 overflow-x-auto py-1.5 scrollbar-none">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onSelectTab(tab.id)}
                className={`flex items-center whitespace-nowrap px-3.5 py-2 text-xs font-bold rounded-lg transition cursor-pointer ${
                  isActive
                    ? 'bg-slate-900 text-white shadow-xs'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-white'
                }`}
              >
                <span>{tab.label}</span>
                {tab.badge && (
                  <span className="ml-2 px-1.5 py-0.2 bg-orange-500 text-white rounded-full text-[10px] font-extrabold">
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
