import React, { useState, useEffect } from 'react';
import {
  User, AdminStats, SecuritySettings, SecurityIncident,
  SystemSettings, Trade, Stock
} from '../../types';
import { api } from '../../services/api';

interface AdminDashboardProps {
  currentUser: User;
  activeTab?: string;
  onSelectTab?: (tab: string) => void;
  onRefreshData: () => void;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({
  currentUser,
  activeTab = 'dashboard',
  onSelectTab,
  onRefreshData,
}) => {
  const [loading, setLoading] = useState(false);

  // Admin Data State
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [usersList, setUsersList] = useState<User[]>([]);
  const [securitySettings, setSecuritySettings] = useState<SecuritySettings | null>(null);
  const [securityIncidents, setSecurityIncidents] = useState<SecurityIncident[]>([]);
  const [securityScore, setSecurityScore] = useState('EXCELLENT (98%)');
  const [systemSettings, setSystemSettings] = useState<SystemSettings | null>(null);
  const [allTrades, setAllTrades] = useState<Trade[]>([]);
  const [stocksList, setStocksList] = useState<Stock[]>([]);

  // Stock Management State
  const [showAddStockModal, setShowAddStockModal] = useState(false);
  const [showEditStockModal, setShowEditStockModal] = useState(false);
  const [editingStock, setEditingStock] = useState<Stock | null>(null);
  const [stockSearchQuery, setStockSearchQuery] = useState('');
  const [newStockSymbol, setNewStockSymbol] = useState('');
  const [newStockName, setNewStockName] = useState('');
  const [newStockPrice, setNewStockPrice] = useState<number>(150.0);
  const [newStockQuantity, setNewStockQuantity] = useState<number>(25000);
  const [editStockPrice, setEditStockPrice] = useState<number>(150.0);
  const [editStockQuantity, setEditStockQuantity] = useState<number>(25000);
  const [editStockName, setEditStockName] = useState('');
  const [stockToDelete, setStockToDelete] = useState<{ symbol: string; name: string } | null>(null);

  // User Management Modals
  const [showAddUserModal, setShowAddUserModal] = useState(false);
  const [showEditUserModal, setShowEditUserModal] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [userRoleFilter, setUserRoleFilter] = useState<'ALL' | 'TRADER' | 'ADMIN'>('ALL');

  // New User Form State
  const [newUserName, setNewUserName] = useState('');
  const [newUserEmail, setNewUserEmail] = useState('');
  const [newUserPassword, setNewUserPassword] = useState('');
  const [newUserRole, setNewUserRole] = useState<'TRADER' | 'ADMIN'>('TRADER');
  const [newUserInitialBalance, setNewUserInitialBalance] = useState<number>(50000);
  const [userActionError, setUserActionError] = useState<string | null>(null);
  const [userActionSuccess, setUserActionSuccess] = useState<string | null>(null);

  // Edit User Form State
  const [editName, setEditName] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [editPassword, setEditPassword] = useState('');

  // Delete User Confirmation State
  const [userToDelete, setUserToDelete] = useState<{ id: string; name: string } | null>(null);

  // Security Form State
  const [secSuccess, setSecSuccess] = useState<string | null>(null);
  const [secError, setSecError] = useState<string | null>(null);

  // System Settings Form State
  const [sysSuccess, setSysSuccess] = useState<string | null>(null);
  const [sysError, setSysError] = useState<string | null>(null);

  // Reports State
  const [selectedReportType, setSelectedReportType] = useState<'user' | 'trade' | 'portfolio' | 'financial' | 'system'>('user');
  const [reportTitle, setReportTitle] = useState('Executive User Audit Report');
  const [reportText, setReportText] = useState('');
  const [reportLoading, setReportLoading] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [copiedReport, setCopiedReport] = useState(false);

  // Fetch all administrative telemetry from Java backend
  const fetchAdminData = async () => {
    setLoading(true);
    try {
      const [st, users, secInfo, sys, trades, stks] = await Promise.all([
        api.getAdminStats(),
        api.getAdminUsers(),
        api.getSecurityInfo(),
        api.getSystemSettings(),
        api.getTrades(),
        api.getStocks().catch(() => []),
      ]);
      setStats(st);
      setUsersList(users);
      setSecuritySettings(secInfo.settings);
      setSecurityIncidents(secInfo.incidents);
      setSecurityScore(secInfo.healthScore);
      setSystemSettings(sys);
      setAllTrades(trades);
      setStocksList(stks || []);
    } catch (err: any) {
      console.error('Error loading admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdminData();
  }, []);

  // Handle Add User
  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setUserActionError(null);
    setUserActionSuccess(null);
    try {
      const res = await api.createAdminUser({
        name: newUserName,
        email: newUserEmail,
        password: newUserPassword,
        role: newUserRole,
        initialBalance: newUserRole === 'TRADER' ? newUserInitialBalance : undefined,
      });
      if (res.success) {
        setUserActionSuccess(`Successfully created ${newUserRole} account for ${newUserName}`);
        setShowAddUserModal(false);
        setNewUserName('');
        setNewUserEmail('');
        setNewUserPassword('');
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to create user');
    }
  };

  // Handle Edit User
  const handleUpdateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    setUserActionError(null);
    try {
      const res = await api.updateAdminUser({
        userId: editingUser.userId,
        name: editName,
        email: editEmail,
        password: editPassword || undefined,
      });
      if (res.success) {
        setShowEditUserModal(false);
        setEditingUser(null);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to update user');
    }
  };

  // Handle Delete User
  const promptDeleteUser = (userId: string, name: string) => {
    setUserActionError(null);
    if (userId === currentUser.userId) {
      setUserActionError('Cannot delete the currently logged in administrator account.');
      return;
    }
    setUserToDelete({ id: userId, name });
  };

  const confirmDeleteUser = async () => {
    if (!userToDelete) return;
    try {
      const res = await api.deleteAdminUser(userToDelete.id);
      if (res.success) {
        setUserActionSuccess(`User "${userToDelete.name}" (${userToDelete.id}) was successfully deleted.`);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to delete user');
    } finally {
      setUserToDelete(null);
    }
  };

  // Handle Add Stock
  const handleCreateStock = async (e: React.FormEvent) => {
    e.preventDefault();
    setUserActionError(null);
    setUserActionSuccess(null);
    try {
      const res = await api.addStock({
        symbol: newStockSymbol.trim().toUpperCase(),
        companyName: newStockName.trim(),
        price: Number(newStockPrice),
        availableQuantity: Number(newStockQuantity),
      });
      if (res.success) {
        setUserActionSuccess(res.message || `Successfully listed stock ${newStockSymbol.toUpperCase()}`);
        setShowAddStockModal(false);
        setNewStockSymbol('');
        setNewStockName('');
        setNewStockPrice(150.0);
        setNewStockQuantity(25000);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to add stock');
    }
  };

  // Handle Edit Stock
  const handleUpdateStock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingStock) return;
    setUserActionError(null);
    setUserActionSuccess(null);
    try {
      const res = await api.updateStock({
        symbol: editingStock.symbol,
        companyName: editStockName.trim() || undefined,
        price: Number(editStockPrice),
        availableQuantity: Number(editStockQuantity),
      });
      if (res.success) {
        setUserActionSuccess(res.message || `Successfully updated stock ${editingStock.symbol}`);
        setShowEditStockModal(false);
        setEditingStock(null);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to update stock');
    }
  };

  // Handle Delete Stock
  const promptDeleteStock = (symbol: string, name: string) => {
    setUserActionError(null);
    setStockToDelete({ symbol, name });
  };

  const confirmDeleteStock = async () => {
    if (!stockToDelete) return;
    try {
      const res = await api.deleteStock(stockToDelete.symbol);
      if (res.success) {
        setUserActionSuccess(res.message || `Stock ${stockToDelete.symbol} was delisted.`);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setUserActionError(err.message || 'Failed to delete stock');
    } finally {
      setStockToDelete(null);
    }
  };

  // Handle Save Security Settings
  const handleSaveSecurity = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!securitySettings) return;
    setSecSuccess(null);
    setSecError(null);
    try {
      const res = await api.updateSecuritySettings(securitySettings);
      if (res.success) {
        setSecSuccess('Financial data security policies successfully updated in Java.');
        setTimeout(() => setSecSuccess(null), 3000);
        fetchAdminData();
      }
    } catch (err: any) {
      setSecError(err.message || 'Failed to update security settings');
    }
  };

  // Handle Save System Settings
  const handleSaveSystemSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!systemSettings) return;
    setSysSuccess(null);
    setSysError(null);
    try {
      const res = await api.updateSystemSettings(systemSettings);
      if (res.success) {
        setSysSuccess('Platform system settings successfully updated in Java.');
        setTimeout(() => setSysSuccess(null), 3000);
        fetchAdminData();
        onRefreshData();
      }
    } catch (err: any) {
      setSysError(err.message || 'Failed to update system settings');
    }
  };

  // Handle Generate Report
  const handleGenerateReport = async (type: 'user' | 'trade' | 'portfolio' | 'financial' | 'system') => {
    setSelectedReportType(type);
    setReportLoading(true);
    setReportError(null);
    try {
      const res = await api.getAdminReport(type);
      setReportTitle(res.title);
      setReportText(res.reportText);
    } catch (err: any) {
      setReportError(err.message || 'Failed to generate report');
    } finally {
      setReportLoading(false);
    }
  };

  const copyReportToClipboard = () => {
    navigator.clipboard.writeText(reportText);
    setCopiedReport(true);
    setTimeout(() => setCopiedReport(false), 2000);
  };

  const downloadReportFile = () => {
    const blob = new Blob([reportText], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${selectedReportType}_report_${new Date().toISOString().slice(0, 10)}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const filteredUsers = usersList.filter((u) => {
    const matchesSearch =
      u.name.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
      u.email.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
      u.userId.toLowerCase().includes(userSearchQuery.toLowerCase());
    if (userRoleFilter === 'ALL') return matchesSearch;
    return matchesSearch && u.role === userRoleFilter;
  });

  const filteredStocks = stocksList.filter((s) => {
    return (
      s.symbol.toLowerCase().includes(stockSearchQuery.toLowerCase()) ||
      s.name.toLowerCase().includes(stockSearchQuery.toLowerCase())
    );
  });

  // Calculate buy vs sell transactions
  const buyCount = stats?.buyCount ?? allTrades.filter(t => t.tradeType === 'BUY').length;
  const sellCount = stats?.sellCount ?? allTrades.filter(t => t.tradeType === 'SELL').length;
  const totalTradesCount = stats?.totalTrades ?? allTrades.length;
  const tradingVol = stats?.totalVolumeUSD ?? allTrades.reduce((acc, t) => acc + t.totalAmount, 0);

  return (
    <div className="space-y-6">
      {/* Messages */}
      {userActionError && (
        <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
          {userActionError}
        </div>
      )}
      {userActionSuccess && (
        <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-sm rounded">
          {userActionSuccess}
        </div>
      )}

      {/* ================= VIEW 1: ADMIN DASHBOARD ================= */}
      {(activeTab === 'dashboard' || activeTab === 'overview') && (
        <div className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-gray-200">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Admin Dashboard</h1>
              <p className="text-sm text-gray-600">
                System telemetry, market execution statistics, and platform overview.
              </p>
            </div>
            <button
              onClick={fetchAdminData}
              disabled={loading}
              className="px-3 py-1.5 border border-gray-300 rounded text-xs font-medium text-gray-700 hover:bg-gray-50 transition"
            >
              {loading ? 'Refreshing...' : 'Refresh Telemetry'}
            </button>
          </div>

          {/* Summary Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Total Users</span>
              <div className="text-2xl font-bold text-gray-900 mt-1">{stats?.totalUsers ?? usersList.length}</div>
              <span className="text-[11px] text-gray-400">All registered accounts</span>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Total Traders</span>
              <div className="text-2xl font-bold text-gray-900 mt-1">{stats?.totalTraders ?? usersList.filter(u => u.role === 'TRADER').length}</div>
              <span className="text-[11px] text-gray-400">Active market investors</span>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Total Trades</span>
              <div className="text-2xl font-bold text-gray-900 mt-1">{totalTradesCount}</div>
              <span className="text-[11px] text-gray-400">Completed executions</span>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Trading Volume</span>
              <div className="text-2xl font-bold text-gray-900 mt-1">${tradingVol.toLocaleString('en-US', { maximumFractionDigits: 0 })}</div>
              <span className="text-[11px] text-gray-400">Cumulative turnover</span>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-blue-600 uppercase">Buy Orders</span>
              <div className="text-2xl font-bold text-blue-700 mt-1">{buyCount}</div>
              <span className="text-[11px] text-gray-400">Long acquisitions</span>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-red-600 uppercase">Sell Orders</span>
              <div className="text-2xl font-bold text-red-700 mt-1">{sellCount}</div>
              <span className="text-[11px] text-gray-400">Liquidations</span>
            </div>
          </div>

          {/* Volume Breakdown Bar */}
          <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Transaction Type Distribution</h2>
            <div className="w-full h-3 bg-gray-100 rounded-full overflow-hidden flex">
              <div
                style={{ width: `${totalTradesCount > 0 ? (buyCount / totalTradesCount) * 100 : 50}%` }}
                className="bg-blue-600 h-full"
                title={`BUY Orders: ${buyCount}`}
              />
              <div
                style={{ width: `${totalTradesCount > 0 ? (sellCount / totalTradesCount) * 100 : 50}%` }}
                className="bg-red-500 h-full"
                title={`SELL Orders: ${sellCount}`}
              />
            </div>
            <div className="flex justify-between items-center text-xs text-gray-600 mt-2">
              <div className="flex items-center space-x-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600" />
                <span>BUY Orders: {buyCount} ({totalTradesCount > 0 ? ((buyCount / totalTradesCount) * 100).toFixed(1) : 0}%)</span>
              </div>
              <div className="flex items-center space-x-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500" />
                <span>SELL Orders: {sellCount} ({totalTradesCount > 0 ? ((sellCount / totalTradesCount) * 100).toFixed(1) : 0}%)</span>
              </div>
            </div>
          </div>

          {/* Recent Trade Activity Table */}
          <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
            <div className="p-4 border-b border-gray-200 flex justify-between items-center">
              <h2 className="text-base font-semibold text-gray-900">Recent Platform Trade Activity</h2>
              {onSelectTab && (
                <button
                  onClick={() => onSelectTab('trade-activity')}
                  className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                >
                  View Full Trade Activity &rarr;
                </button>
              )}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                  <tr>
                    <th className="py-2.5 px-4 font-semibold">Trade ID</th>
                    <th className="py-2.5 px-4 font-semibold">Stock</th>
                    <th className="py-2.5 px-3 font-semibold">Type</th>
                    <th className="py-2.5 px-3 font-semibold">Qty</th>
                    <th className="py-2.5 px-3 font-semibold">Price</th>
                    <th className="py-2.5 px-4 font-semibold">Total</th>
                    <th className="py-2.5 px-4 font-semibold">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {allTrades.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-6 text-center text-sm text-gray-500">
                        No trade activity registered yet.
                      </td>
                    </tr>
                  ) : (
                    allTrades.slice(0, 6).map((t, idx) => (
                      <tr key={`${t.tradeId || 'trade'}-${t.dateTime}-${idx}`} className="hover:bg-gray-50/75">
                        <td className="py-2.5 px-4 font-mono text-xs text-gray-600">{t.tradeId}</td>
                        <td className="py-2.5 px-4 font-bold text-gray-900">{t.stockSymbol}</td>
                        <td className="py-2.5 px-3">
                          <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                            t.tradeType === 'BUY' ? 'bg-blue-50 text-blue-700' : 'bg-red-50 text-red-700'
                          }`}>
                            {t.tradeType}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-gray-800">{t.quantity}</td>
                        <td className="py-2.5 px-3 text-gray-800">${(t.price ?? 0).toFixed(2)}</td>
                        <td className="py-2.5 px-4 font-semibold text-gray-900">${(t.totalAmount ?? 0).toFixed(2)}</td>
                        <td className="py-2.5 px-4 text-xs text-gray-500">{t.dateTime}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ================= VIEW 2: USERS MANAGEMENT ================= */}
      {activeTab === 'users' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">User Management</h1>
              <p className="text-sm text-gray-600">
                Create, update, inspect, and remove trader and administrator accounts.
              </p>
            </div>
            <button
              onClick={() => setShowAddUserModal(true)}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-semibold transition"
            >
              + Add New User
            </button>
          </div>

          {/* Filters and search */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="flex items-center space-x-2 w-full sm:w-auto">
              <input
                type="text"
                placeholder="Search user by name, email, or ID..."
                value={userSearchQuery}
                onChange={(e) => setUserSearchQuery(e.target.value)}
                className="px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500 w-full sm:w-72"
              />
              <select
                value={userRoleFilter}
                onChange={(e) => setUserRoleFilter(e.target.value as any)}
                className="px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
              >
                <option value="ALL">All Roles</option>
                <option value="TRADER">Traders Only</option>
                <option value="ADMIN">Admins Only</option>
              </select>
            </div>
            <span className="text-xs text-gray-500">
              Showing {filteredUsers.length} of {usersList.length} users
            </span>
          </div>

          {/* User Table */}
          <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                  <tr>
                    <th className="py-3 px-4 font-semibold">User ID</th>
                    <th className="py-3 px-4 font-semibold">Name</th>
                    <th className="py-3 px-4 font-semibold">Email</th>
                    <th className="py-3 px-3 font-semibold">Role</th>
                    <th className="py-3 px-3 font-semibold">Status</th>
                    <th className="py-3 px-4 font-semibold text-right">Cash Balance</th>
                    <th className="py-3 px-4 font-semibold text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredUsers.map((u) => (
                    <tr key={u.userId} className="hover:bg-gray-50/75">
                      <td className="py-3 px-4 font-mono text-xs text-gray-600">{u.userId}</td>
                      <td className="py-3 px-4 font-semibold text-gray-900">{u.name}</td>
                      <td className="py-3 px-4 text-gray-700">{u.email}</td>
                      <td className="py-3 px-3">
                        <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                          u.role === 'ADMIN' ? 'bg-purple-50 text-purple-700 border border-purple-200' : 'bg-blue-50 text-blue-700'
                        }`}>
                          {u.role}
                        </span>
                      </td>
                      <td className="py-3 px-3">
                        <span className="inline-block px-2 py-0.5 rounded text-xs font-semibold bg-green-50 text-green-700">
                          {u.status || 'ACTIVE'}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-medium text-right text-gray-900">
                        {u.role === 'TRADER' ? `$${(u.cashBalance ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}` : 'N/A'}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <div className="inline-flex space-x-2">
                          <button
                            onClick={() => {
                              setEditingUser(u);
                              setEditName(u.name);
                              setEditEmail(u.email);
                              setEditPassword('');
                              setShowEditUserModal(true);
                            }}
                            className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => promptDeleteUser(u.userId, u.name)}
                            className="text-xs text-red-600 hover:text-red-800 font-medium"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ================= VIEW: STOCKS MANAGEMENT ================= */}
      {activeTab === 'stocks' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Stock &amp; Equity Management</h1>
              <p className="text-sm text-gray-600">
                List new tickers, update equity prices and share inventory, or delist stocks from the exchange.
              </p>
            </div>
            <button
              onClick={() => setShowAddStockModal(true)}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-semibold transition"
            >
              + Add New Stock
            </button>
          </div>

          {/* Search bar */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="w-full sm:w-auto">
              <input
                type="text"
                placeholder="Search stocks by ticker symbol or company name..."
                value={stockSearchQuery}
                onChange={(e) => setStockSearchQuery(e.target.value)}
                className="px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500 w-full sm:w-80"
              />
            </div>
            <span className="text-xs text-gray-500">
              Showing {filteredStocks.length} of {stocksList.length} listed equities
            </span>
          </div>

          {/* Stocks Table */}
          <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                  <tr>
                    <th className="py-3 px-4 font-semibold">Symbol</th>
                    <th className="py-3 px-4 font-semibold">Company Name</th>
                    <th className="py-3 px-4 font-semibold text-right">Price (USD)</th>
                    <th className="py-3 px-4 font-semibold text-right">Day Range</th>
                    <th className="py-3 px-4 font-semibold text-right">Available Float</th>
                    <th className="py-3 px-4 font-semibold text-right">Volume</th>
                    <th className="py-3 px-4 font-semibold text-center">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {filteredStocks.map((s) => (
                    <tr key={s.symbol} className="hover:bg-gray-50/75">
                      <td className="py-3 px-4 font-mono font-bold text-sm text-gray-900">{s.symbol}</td>
                      <td className="py-3 px-4 font-medium text-gray-900">{s.name}</td>
                      <td className="py-3 px-4 font-semibold text-right text-gray-900">
                        ${(s.currentPrice ?? 0).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-xs text-right text-gray-500 font-mono">
                        ${(s.dayLow ?? s.currentPrice ?? 0).toFixed(2)} - ${(s.dayHigh ?? s.currentPrice ?? 0).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right text-gray-700 font-mono">
                        {(s.availableQuantity ?? 0).toLocaleString()}
                      </td>
                      <td className="py-3 px-4 text-right text-gray-700 font-mono">
                        {(s.volumeTraded ?? 0).toLocaleString()}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <div className="inline-flex space-x-2">
                          <button
                            onClick={() => {
                              setEditingStock(s);
                              setEditStockName(s.name);
                              setEditStockPrice(s.currentPrice);
                              setEditStockQuantity(s.availableQuantity);
                              setShowEditStockModal(true);
                            }}
                            className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => promptDeleteStock(s.symbol, s.name)}
                            className="text-xs text-red-600 hover:text-red-800 font-medium"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ================= VIEW 3: SECURITY ================= */}
      {activeTab === 'security' && (
        <div className="space-y-6">
          <div className="pb-4 border-b border-gray-200">
            <h1 className="text-2xl font-bold text-gray-900">Financial Data Security</h1>
            <p className="text-sm text-gray-600">
              Security status, password enforcement, session controls, and audit incidents.
            </p>
          </div>

          {secSuccess && (
            <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-sm rounded">
              {secSuccess}
            </div>
          )}
          {secError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
              {secError}
            </div>
          )}

          {/* Security Status Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Security Health</span>
              <div className="text-xl font-bold text-green-600 mt-1">{securityScore}</div>
              <p className="text-xs text-gray-500 mt-1">SHA-256 Hashing &amp; Session Isolation</p>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Active Incidents</span>
              <div className="text-xl font-bold text-gray-900 mt-1">{securityIncidents.length} Logged</div>
              <p className="text-xs text-gray-500 mt-1">Real-time access violations recorded</p>
            </div>
            <div className="bg-white border border-gray-200 rounded p-4 shadow-sm">
              <span className="text-xs font-medium text-gray-500 uppercase">Encryption Standard</span>
              <div className="text-xl font-bold text-gray-900 mt-1">AES-256 / SHA-256</div>
              <p className="text-xs text-gray-500 mt-1">Java Security provider standard</p>
            </div>
          </div>

          {/* Security Configuration Form */}
          {securitySettings && (
            <form onSubmit={handleSaveSecurity} className="bg-white border border-gray-200 rounded p-5 shadow-sm space-y-4">
              <h2 className="text-base font-semibold text-gray-900 border-b border-gray-100 pb-2">
                Security Policy Configuration
              </h2>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                <div className="flex items-center justify-between p-3 border border-gray-200 rounded">
                  <div>
                    <span className="font-medium text-gray-900">Two-Factor Authentication (2FA)</span>
                    <p className="text-xs text-gray-500">Require OTP validation for withdrawals and high-value orders</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={securitySettings.twoFactorAuthEnabled}
                    onChange={(e) => setSecuritySettings({ ...securitySettings, twoFactorAuthEnabled: e.target.checked })}
                    className="h-4 w-4 text-blue-600 border-gray-300 rounded"
                  />
                </div>

                <div className="flex items-center justify-between p-3 border border-gray-200 rounded">
                  <div>
                    <span className="font-medium text-gray-900">Enforce Complex Passwords</span>
                    <p className="text-xs text-gray-500">Requires uppercase, lowercase, digit, and symbol</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={securitySettings.enforcePasswordComplexity}
                    onChange={(e) => setSecuritySettings({ ...securitySettings, enforcePasswordComplexity: e.target.checked })}
                    className="h-4 w-4 text-blue-600 border-gray-300 rounded"
                  />
                </div>

                <div className="flex items-center justify-between p-3 border border-gray-200 rounded">
                  <div>
                    <span className="font-medium text-gray-900">Audit Logging</span>
                    <p className="text-xs text-gray-500">Write cryptographic hash audit entries into data/security_log.txt</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={securitySettings.auditLoggingActive}
                    onChange={(e) => setSecuritySettings({ ...securitySettings, auditLoggingActive: e.target.checked })}
                    className="h-4 w-4 text-blue-600 border-gray-300 rounded"
                  />
                </div>

                <div className="flex items-center justify-between p-3 border border-gray-200 rounded">
                  <div>
                    <span className="font-medium text-gray-900">Session Timeout</span>
                    <p className="text-xs text-gray-500">Inactivity cutoff before automatic token invalidation</p>
                  </div>
                  <select
                    value={securitySettings.sessionTimeoutMinutes}
                    onChange={(e) => setSecuritySettings({ ...securitySettings, sessionTimeoutMinutes: parseInt(e.target.value) })}
                    className="px-2 py-1 border border-gray-300 rounded text-xs"
                  >
                    <option value={15}>15 mins</option>
                    <option value={30}>30 mins</option>
                    <option value={60}>60 mins</option>
                    <option value={120}>120 mins</option>
                  </select>
                </div>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-medium transition"
                >
                  Save Security Policies
                </button>
              </div>
            </form>
          )}

          {/* Recent Security Incidents */}
          <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
            <div className="p-4 border-b border-gray-200">
              <h2 className="text-base font-semibold text-gray-900">Recent Security Audit Incidents</h2>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                  <tr>
                    <th className="py-2.5 px-4 font-semibold">Incident ID</th>
                    <th className="py-2.5 px-4 font-semibold">Type</th>
                    <th className="py-2.5 px-3 font-semibold">Severity</th>
                    <th className="py-2.5 px-6 font-semibold">Description</th>
                    <th className="py-2.5 px-3 font-semibold">IP Address</th>
                    <th className="py-2.5 px-4 font-semibold">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {securityIncidents.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="py-6 text-center text-sm text-gray-500">
                        No security incidents detected. System integrity is intact.
                      </td>
                    </tr>
                  ) : (
                    securityIncidents.map((inc, idx) => (
                      <tr key={`${inc.incidentId || 'inc'}-${idx}`} className="hover:bg-gray-50/75">
                        <td className="py-2.5 px-4 font-mono text-xs text-gray-600">{inc.incidentId}</td>
                        <td className="py-2.5 px-4 font-medium text-gray-900">{inc.type}</td>
                        <td className="py-2.5 px-3">
                          <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                            inc.severity === 'CRITICAL' ? 'bg-red-50 text-red-700' :
                            inc.severity === 'WARNING' ? 'bg-amber-50 text-amber-700' : 'bg-blue-50 text-blue-700'
                          }`}>
                            {inc.severity}
                          </span>
                        </td>
                        <td className="py-2.5 px-6 text-xs text-gray-700">{inc.description}</td>
                        <td className="py-2.5 px-3 font-mono text-xs text-gray-500">{inc.ipAddress}</td>
                        <td className="py-2.5 px-4 text-xs text-gray-500">{inc.timestamp}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ================= VIEW 4: SYSTEM SETTINGS ================= */}
      {(activeTab === 'system-settings' || activeTab === 'settings') && (
        <div className="space-y-6">
          <div className="pb-4 border-b border-gray-200">
            <h1 className="text-2xl font-bold text-gray-900">System Settings</h1>
            <p className="text-sm text-gray-600">
              Engine status, market operational controls, and backend configuration.
            </p>
          </div>

          {sysSuccess && (
            <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-sm rounded">
              {sysSuccess}
            </div>
          )}
          {sysError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
              {sysError}
            </div>
          )}

          {systemSettings && (
            <form onSubmit={handleSaveSystemSettings} className="bg-white border border-gray-200 rounded p-5 shadow-sm space-y-4">
              <h2 className="text-base font-semibold text-gray-900 border-b border-gray-100 pb-2">
                Core Trading Configuration
              </h2>

              <div className="space-y-4 text-sm max-w-2xl">
                <div className="flex items-center justify-between py-2 border-b border-gray-100">
                  <div>
                    <span className="font-semibold text-gray-900">Trading Engine Status</span>
                    <p className="text-xs text-gray-500">Allow or halt all trade order executions across all trader accounts</p>
                  </div>
                  <select
                    value={systemSettings.tradingStatus}
                    onChange={(e) => setSystemSettings({ ...systemSettings, tradingStatus: e.target.value as any })}
                    className="px-3 py-1.5 border border-gray-300 rounded font-semibold text-sm"
                  >
                    <option value="OPEN">OPEN (Active Trading)</option>
                    <option value="HALTED">HALTED (Trading Suspended)</option>
                  </select>
                </div>

                <div className="flex items-center justify-between py-2 border-b border-gray-100">
                  <div>
                    <span className="font-semibold text-gray-900">Trading Commission Fee (%)</span>
                    <p className="text-xs text-gray-500">Transaction fee applied to BUY and SELL orders</p>
                  </div>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="5"
                    value={systemSettings.tradingFeePercent}
                    onChange={(e) => setSystemSettings({ ...systemSettings, tradingFeePercent: parseFloat(e.target.value) || 0 })}
                    className="w-24 px-2 py-1 border border-gray-300 rounded text-sm text-right"
                  />
                </div>

                <div className="flex items-center justify-between py-2 border-b border-gray-100">
                  <div>
                    <span className="font-semibold text-gray-900">Default Starting Trader Balance ($)</span>
                    <p className="text-xs text-gray-500">Cash credit assigned automatically to newly registered traders</p>
                  </div>
                  <input
                    type="number"
                    step="1000"
                    value={systemSettings.defaultStartingBalance}
                    onChange={(e) => setSystemSettings({ ...systemSettings, defaultStartingBalance: parseFloat(e.target.value) || 0 })}
                    className="w-32 px-2 py-1 border border-gray-300 rounded text-sm text-right"
                  />
                </div>

                <div className="flex items-center justify-between py-2 border-b border-gray-100">
                  <div>
                    <span className="font-semibold text-gray-900">Maintenance Mode</span>
                    <p className="text-xs text-gray-500">Restricts user login to administrator level</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={systemSettings.maintenanceMode}
                    onChange={(e) => setSystemSettings({ ...systemSettings, maintenanceMode: e.target.checked })}
                    className="h-4 w-4 text-blue-600 border-gray-300 rounded"
                  />
                </div>

                <div className="pt-2">
                  <button
                    type="submit"
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-medium transition"
                  >
                    Save System Settings
                  </button>
                </div>
              </div>
            </form>
          )}
        </div>
      )}

      {/* ================= VIEW 5: TRADE ACTIVITY ================= */}
      {(activeTab === 'trade-activity' || activeTab === 'monitoring') && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-gray-200">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Trade Activity Monitoring</h1>
              <p className="text-sm text-gray-600">
                Audit feed of all orders executed across the entire trading ecosystem.
              </p>
            </div>
            <button
              onClick={fetchAdminData}
              className="px-3 py-1.5 border border-gray-300 rounded text-xs font-medium text-gray-700 hover:bg-gray-50 transition"
            >
              Refresh Feed
            </button>
          </div>

          <div className="bg-white border border-gray-200 rounded shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs text-gray-600 uppercase border-b border-gray-200">
                  <tr>
                    <th className="py-3 px-4 font-semibold">Trade ID</th>
                    <th className="py-3 px-4 font-semibold">Stock Symbol</th>
                    <th className="py-3 px-3 font-semibold">Action</th>
                    <th className="py-3 px-3 font-semibold">Quantity</th>
                    <th className="py-3 px-3 font-semibold">Execution Price</th>
                    <th className="py-3 px-4 font-semibold">Gross Value</th>
                    <th className="py-3 px-4 font-semibold">Execution Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {allTrades.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-sm text-gray-500">
                        No transactions registered yet.
                      </td>
                    </tr>
                  ) : (
                    allTrades.map((t, idx) => (
                      <tr key={`${t.tradeId || 'tx'}-${t.dateTime}-${idx}`} className="hover:bg-gray-50/75">
                        <td className="py-3 px-4 font-mono text-xs text-gray-600">{t.tradeId}</td>
                        <td className="py-3 px-4 font-bold text-gray-900">{t.stockSymbol}</td>
                        <td className="py-3 px-3">
                          <span className={`inline-block px-2.5 py-0.5 rounded text-xs font-semibold ${
                            t.tradeType === 'BUY' ? 'bg-blue-50 text-blue-700' : 'bg-red-50 text-red-700'
                          }`}>
                            {t.tradeType}
                          </span>
                        </td>
                        <td className="py-3 px-3 text-gray-800 font-medium">{t.quantity}</td>
                        <td className="py-3 px-3 text-gray-800">${(t.price ?? 0).toFixed(2)}</td>
                        <td className="py-3 px-4 font-semibold text-gray-900">${(t.totalAmount ?? 0).toFixed(2)}</td>
                        <td className="py-3 px-4 text-xs text-gray-500">{t.dateTime}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ================= VIEW 6: REPORTS ================= */}
      {activeTab === 'reports' && (
        <div className="space-y-6">
          <div className="pb-4 border-b border-gray-200">
            <h1 className="text-2xl font-bold text-gray-900">Reports Generator</h1>
            <p className="text-sm text-gray-600">
              Generate structured audit summaries directly from Java models and export as text files.
            </p>
          </div>

          {/* Generator Buttons */}
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => handleGenerateReport('user')}
              className={`px-3 py-2 rounded text-xs font-semibold border transition ${
                selectedReportType === 'user' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              Generate User Report
            </button>
            <button
              onClick={() => handleGenerateReport('trade')}
              className={`px-3 py-2 rounded text-xs font-semibold border transition ${
                selectedReportType === 'trade' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              Generate Trade Report
            </button>
            <button
              onClick={() => handleGenerateReport('portfolio')}
              className={`px-3 py-2 rounded text-xs font-semibold border transition ${
                selectedReportType === 'portfolio' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              Generate Portfolio Report
            </button>
            <button
              onClick={() => handleGenerateReport('financial')}
              className={`px-3 py-2 rounded text-xs font-semibold border transition ${
                selectedReportType === 'financial' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              Generate Financial Report
            </button>
            <button
              onClick={() => handleGenerateReport('system')}
              className={`px-3 py-2 rounded text-xs font-semibold border transition ${
                selectedReportType === 'system' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
            >
              Generate System Analytics
            </button>
          </div>

          {reportError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded">
              {reportError}
            </div>
          )}

          {/* Generated Report Display Area */}
          <div className="bg-white border border-gray-200 rounded shadow-sm p-4 space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 border-b border-gray-100 gap-2">
              <h2 className="text-base font-bold text-gray-900">{reportTitle}</h2>
              {reportText && (
                <div className="flex items-center space-x-2">
                  <button
                    onClick={copyReportToClipboard}
                    className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 text-gray-700"
                  >
                    {copiedReport ? 'Copied!' : 'Copy to Clipboard'}
                  </button>
                  <button
                    onClick={downloadReportFile}
                    className="px-3 py-1 text-xs bg-blue-600 hover:bg-blue-700 text-white rounded font-medium"
                  >
                    Export / Download
                  </button>
                </div>
              )}
            </div>

            {reportLoading ? (
              <div className="py-12 text-center text-sm text-gray-500">
                Querying Java Engine &amp; assembling audit report...
              </div>
            ) : !reportText ? (
              <div className="py-12 text-center text-sm text-gray-500">
                Select a report button above to generate a real-time audit document.
              </div>
            ) : (
              <pre className="p-4 bg-gray-50 border border-gray-200 rounded text-xs text-gray-800 font-mono whitespace-pre-wrap leading-relaxed max-h-[500px] overflow-y-auto">
                {reportText}
              </pre>
            )}
          </div>
        </div>
      )}

      {/* ================= MODAL: ADD NEW USER ================= */}
      {showAddUserModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-md w-full p-6">
            <div className="flex justify-between items-center pb-3 border-b border-gray-200 mb-4">
              <h3 className="text-lg font-bold text-gray-900">Add New User</h3>
              <button
                onClick={() => setShowAddUserModal(false)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold"
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleCreateUser} className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={newUserName}
                  onChange={(e) => setNewUserName(e.target.value)}
                  placeholder="e.g. John Doe"
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Email Address</label>
                <input
                  type="email"
                  required
                  value={newUserEmail}
                  onChange={(e) => setNewUserEmail(e.target.value)}
                  placeholder="e.g. john@apex.com"
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Password</label>
                <input
                  type="password"
                  required
                  value={newUserPassword}
                  onChange={(e) => setNewUserPassword(e.target.value)}
                  placeholder="Minimum 6 characters"
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">System Role</label>
                <select
                  value={newUserRole}
                  onChange={(e) => setNewUserRole(e.target.value as any)}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                >
                  <option value="TRADER">TRADER (Market Access)</option>
                  <option value="ADMIN">ADMIN (System Administrator)</option>
                </select>
              </div>

              {newUserRole === 'TRADER' && (
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Initial Cash Balance ($)</label>
                  <input
                    type="number"
                    min="100"
                    value={newUserInitialBalance}
                    onChange={(e) => setNewUserInitialBalance(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              )}

              <div className="flex justify-end space-x-2 pt-3 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => setShowAddUserModal(false)}
                  className="px-3 py-1.5 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded font-medium"
                >
                  Create User
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================= MODAL: EDIT USER ================= */}
      {showEditUserModal && editingUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-md w-full p-6">
            <div className="flex justify-between items-center pb-3 border-b border-gray-200 mb-4">
              <h3 className="text-lg font-bold text-gray-900">Edit User ({editingUser.userId})</h3>
              <button
                onClick={() => setShowEditUserModal(false)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold"
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleUpdateUser} className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Email Address</label>
                <input
                  type="email"
                  required
                  value={editEmail}
                  onChange={(e) => setEditEmail(e.target.value)}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">
                  Reset Password (Leave blank to keep existing)
                </label>
                <input
                  type="password"
                  value={editPassword}
                  onChange={(e) => setEditPassword(e.target.value)}
                  placeholder="New password (optional)"
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end space-x-2 pt-3 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => setShowEditUserModal(false)}
                  className="px-3 py-1.5 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded font-medium"
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================= MODAL: DELETE CONFIRMATION ================= */}
      {userToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-sm w-full p-6">
            <h3 className="text-lg font-bold text-gray-900 mb-2">Confirm User Deletion</h3>
            <p className="text-xs text-gray-600 mb-4">
              Are you sure you want to permanently delete user account{' '}
              <strong className="text-gray-900">{userToDelete.name}</strong> ({userToDelete.id})? This action cannot be undone.
            </p>

            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setUserToDelete(null)}
                className="px-3 py-1.5 border border-gray-300 rounded text-xs text-gray-700 hover:bg-gray-50 font-medium"
              >
                Cancel
              </button>
              <button
                onClick={confirmDeleteUser}
                className="px-4 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-semibold"
              >
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ================= MODAL: ADD STOCK ================= */}
      {showAddStockModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-md w-full p-6">
            <div className="flex justify-between items-center pb-3 border-b border-gray-200 mb-4">
              <h3 className="text-lg font-bold text-gray-900">List New Stock on Exchange</h3>
              <button
                onClick={() => setShowAddStockModal(false)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold"
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleCreateStock} className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Ticker Symbol (e.g. INFY, TATAMOTORS, RELIANCE)</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. INFY"
                  value={newStockSymbol}
                  onChange={(e) => setNewStockSymbol(e.target.value.toUpperCase())}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded font-mono uppercase focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Company / Issuer Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Infosys Limited"
                  value={newStockName}
                  onChange={(e) => setNewStockName(e.target.value)}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Initial Price ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={newStockPrice}
                    onChange={(e) => setNewStockPrice(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Available Float (Shares)</label>
                  <input
                    type="number"
                    min="1"
                    required
                    value={newStockQuantity}
                    onChange={(e) => setNewStockQuantity(parseInt(e.target.value, 10) || 0)}
                    className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 font-mono"
                  />
                </div>
              </div>

              <div className="flex justify-end space-x-2 pt-3 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => setShowAddStockModal(false)}
                  className="px-3 py-1.5 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded font-medium"
                >
                  List Stock
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================= MODAL: EDIT STOCK ================= */}
      {showEditStockModal && editingStock && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-md w-full p-6">
            <div className="flex justify-between items-center pb-3 border-b border-gray-200 mb-4">
              <h3 className="text-lg font-bold text-gray-900">Update Stock ({editingStock.symbol})</h3>
              <button
                onClick={() => setShowEditStockModal(false)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold"
              >
                &times;
              </button>
            </div>

            <form onSubmit={handleUpdateStock} className="space-y-3 text-sm">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Company Name</label>
                <input
                  type="text"
                  required
                  value={editStockName}
                  onChange={(e) => setEditStockName(e.target.value)}
                  className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Market Price ($)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={editStockPrice}
                    onChange={(e) => setEditStockPrice(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700 mb-1">Available Float (Shares)</label>
                  <input
                    type="number"
                    min="0"
                    required
                    value={editStockQuantity}
                    onChange={(e) => setEditStockQuantity(parseInt(e.target.value, 10) || 0)}
                    className="w-full px-3 py-1.5 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 font-mono"
                  />
                </div>
              </div>

              <div className="flex justify-end space-x-2 pt-3 border-t border-gray-200">
                <button
                  type="button"
                  onClick={() => setShowEditStockModal(false)}
                  className="px-3 py-1.5 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded font-medium"
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================= MODAL: DELETE STOCK CONFIRMATION ================= */}
      {stockToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-sm w-full p-6">
            <h3 className="text-lg font-bold text-gray-900 mb-2">Delist Stock from Exchange</h3>
            <p className="text-xs text-gray-600 mb-4">
              Are you sure you want to permanently delist ticker{' '}
              <strong className="text-gray-900 font-mono">{stockToDelete.symbol}</strong> ({stockToDelete.name})?
            </p>

            <div className="flex justify-end space-x-2">
              <button
                onClick={() => setStockToDelete(null)}
                className="px-3 py-1.5 border border-gray-300 rounded text-xs text-gray-700 hover:bg-gray-50 font-medium"
              >
                Cancel
              </button>
              <button
                onClick={confirmDeleteStock}
                className="px-4 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded text-xs font-semibold"
              >
                Confirm Delist
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
