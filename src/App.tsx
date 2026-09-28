import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from './components/Navbar';
import { LoginView } from './components/LoginView';
import { TraderDashboard } from './components/trader/TraderDashboard';
import { StocksView } from './components/trader/StocksView';
import { PortfolioView } from './components/trader/PortfolioView';
import { MarketUpdatesView } from './components/trader/MarketUpdatesView';
import { TradeHistoryView } from './components/trader/TradeHistoryView';
import { AlertsView } from './components/trader/AlertsView';
import { TraderSettingsView } from './components/trader/TraderSettingsView';
import { TradeModal } from './components/trader/TradeModal';
import { AdminDashboard } from './components/admin/AdminDashboard';
import { JavaInspectorModal } from './components/JavaInspectorModal';
import { User, Stock, Trade, Portfolio, MarketUpdate, NotificationItem } from './types';
import { api } from './services/api';

export default function App() {
  // Authentication state - starts as trader1 by default for rapid review & testing
  const [currentUser, setCurrentUser] = useState<User | null>({
    userId: 'trader1',
    name: 'Alex Morgan',
    email: 'alex.morgan@apex.com',
    role: 'TRADER',
    cashBalance: 50000.0,
    createdAt: '2026-09-10 09:30:00',
    emailAlertsEnabled: true,
    alertThresholdPercent: 2.0,
  });

  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [showLoginModal, setShowLoginModal] = useState<boolean>(false);
  const [showJavaInspector, setShowJavaInspector] = useState<boolean>(false);

  // Trade Modal State
  const [tradeModalStock, setTradeModalStock] = useState<Stock | null>(null);
  const [tradeModalType, setTradeModalType] = useState<'BUY' | 'SELL'>('BUY');
  const [isTradeModalOpen, setIsTradeModalOpen] = useState<boolean>(false);

  // Live Market & Trading State
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [marketUpdates, setMarketUpdates] = useState<MarketUpdate[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [marketStatus, setMarketStatus] = useState<string>('OPEN');
  const [isTicking, setIsTicking] = useState<boolean>(false);

  // Load live platform data
  const loadPlatformData = useCallback(async () => {
    try {
      const [stocksRes, sysRes, updatesRes] = await Promise.all([
        api.getStocks(),
        api.getSystemSettings().catch(() => null),
        api.getMarketUpdates().catch(() => []),
      ]);

      if (stocksRes && Array.isArray(stocksRes)) {
        setStocks(stocksRes);
      }
      if (sysRes) {
        setMarketStatus(sysRes.tradingStatus || 'OPEN');
      }
      if (updatesRes) {
        setMarketUpdates(updatesRes);
      }
    } catch (err) {
      console.warn('Backend sync warning:', err);
    }
  }, []);

  // Load User-specific data
  const loadUserData = useCallback(async (user: User) => {
    try {
      if (user.role === 'TRADER') {
        const [portRes, tradesRes, notifsRes] = await Promise.all([
          api.getPortfolio(user.userId).catch(() => null),
          api.getTrades(user.userId).catch(() => []),
          api.getNotifications(user.userId).catch(() => []),
        ]);

        if (portRes) setPortfolio(portRes);
        if (tradesRes) setTrades(tradesRes);
        if (notifsRes) setNotifications(notifsRes);
      } else {
        const notifsRes = await api.getNotifications(user.userId).catch(() => []);
        setNotifications(notifsRes || []);
      }
    } catch (err) {
      console.warn('User data fetch warning:', err);
    }
  }, []);

  // Initial load
  useEffect(() => {
    loadPlatformData();
    if (currentUser) {
      loadUserData(currentUser);
    }
  }, [loadPlatformData, loadUserData]);

  // Periodic polling to keep prices and balances fresh
  useEffect(() => {
    const timer = setInterval(() => {
      loadPlatformData();
      if (currentUser && currentUser.role === 'TRADER') {
        loadUserData(currentUser);
      }
    }, 12000);

    return () => clearInterval(timer);
  }, [currentUser, loadPlatformData, loadUserData]);

  // Trigger manual market tick
  const handleTriggerTick = async () => {
    setIsTicking(true);
    try {
      await api.triggerMarketTick();
      await loadPlatformData();
      if (currentUser) {
        await loadUserData(currentUser);
      }
    } catch (err) {
      console.error('Market tick error:', err);
    } finally {
      setIsTicking(false);
    }
  };

  const handleMarkNotificationsRead = async () => {
    if (!currentUser) return;
    try {
      await api.markNotificationsRead(currentUser.userId);
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
    } catch (err) {
      console.error('Failed to mark notifications read:', err);
    }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setShowLoginModal(true);
    setActiveTab('dashboard');
  };

  const handleLoginSuccess = (user: User) => {
    setCurrentUser(user);
    setShowLoginModal(false);
    setActiveTab('dashboard');
    loadUserData(user);
    loadPlatformData();
  };

  const handleOpenTrade = (stock: Stock, type: 'BUY' | 'SELL') => {
    setTradeModalStock(stock);
    setTradeModalType(type);
    setIsTradeModalOpen(true);
  };

  return (
    <div className="min-h-screen bg-white text-gray-900 flex flex-col font-sans">
      {/* Top Navbar */}
      <Navbar
        currentUser={currentUser}
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        onLogout={handleLogout}
        onTriggerTick={handleTriggerTick}
        isTicking={isTicking}
        marketStatus={marketStatus}
        notifications={notifications}
        cashBalance={portfolio?.cashBalance ?? currentUser?.cashBalance}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {showLoginModal || !currentUser ? (
          <LoginView onLoginSuccess={handleLoginSuccess} />
        ) : currentUser.role === 'ADMIN' ? (
          <AdminDashboard
            currentUser={currentUser}
            activeTab={activeTab}
            onSelectTab={setActiveTab}
            onRefreshData={() => {
              loadPlatformData();
              loadUserData(currentUser);
            }}
          />
        ) : (
          /* TRADER ROUTING */
          <>
            {activeTab === 'dashboard' && (
              <TraderDashboard
                currentUser={currentUser}
                portfolio={portfolio}
                stocks={stocks}
                trades={trades}
                marketUpdates={marketUpdates}
                onNavigate={setActiveTab}
                onOpenTrade={handleOpenTrade}
              />
            )}

            {activeTab === 'stocks' && (
              <StocksView
                stocks={stocks}
                onOpenTrade={handleOpenTrade}
              />
            )}

            {activeTab === 'portfolio' && (
              <PortfolioView
                portfolio={portfolio}
                stocks={stocks}
                onOpenTrade={handleOpenTrade}
              />
            )}

            {activeTab === 'market-updates' && (
              <MarketUpdatesView
                marketUpdates={marketUpdates}
                stocks={stocks}
                onOpenTrade={handleOpenTrade}
                onTriggerTick={handleTriggerTick}
                isTicking={isTicking}
              />
            )}

            {activeTab === 'trade-history' && (
              <TradeHistoryView
                trades={trades}
              />
            )}

            {activeTab === 'alerts' && (
              <AlertsView
                notifications={notifications}
                onMarkRead={handleMarkNotificationsRead}
              />
            )}

            {activeTab === 'settings' && (
              <TraderSettingsView
                currentUser={currentUser}
              />
            )}
          </>
        )}
      </main>

      {/* Trade Modal */}
      {currentUser && currentUser.role === 'TRADER' && (
        <TradeModal
          isOpen={isTradeModalOpen}
          onClose={() => setIsTradeModalOpen(false)}
          stock={tradeModalStock}
          initialType={tradeModalType}
          currentUser={currentUser}
          portfolio={portfolio}
          onTradeCompleted={() => {
            loadPlatformData();
            loadUserData(currentUser);
          }}
        />
      )}

      {/* Simple Footer */}
      <footer className="border-t border-gray-200 bg-gray-50 py-4 px-4 sm:px-6 lg:px-8 mt-auto">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-gray-600">
          <div className="flex items-center space-x-2">
            <span className="font-semibold text-gray-800">Online Stock Trading Platform</span>
            <span>&bull;</span>
            <span>Java SE 17 REST Engine (Port 8765)</span>
            <span>&bull;</span>
            <span>File Persistence (data/*.txt)</span>
          </div>

          <div className="flex items-center space-x-3">
            <button
              onClick={() => setShowJavaInspector(true)}
              className="text-blue-600 hover:text-blue-800 font-medium underline"
            >
              Inspect Java Source Code &amp; OOP Models
            </button>
            <span className="text-gray-300">|</span>
            <span className="text-gray-500">College Final Project</span>
          </div>
        </div>
      </footer>

      {/* Java Architecture Inspector Modal */}
      <JavaInspectorModal
        isOpen={showJavaInspector}
        onClose={() => setShowJavaInspector(false)}
      />
    </div>
  );
}
