import {
  User, Stock, Trade, Portfolio, MarketUpdate,
  NotificationItem, SecuritySettings, SecurityIncident,
  SystemSettings, AdminStats
} from '../types';

const API_BASE = '/api';

async function handleResponse<T>(res: Response): Promise<T> {
  const text = await res.text();
  let data: any;
  try {
    data = JSON.parse(text);
  } catch (e) {
    throw new Error(`Server returned non-JSON response (${res.status}): ${text.substring(0, 100)}`);
  }

  if (!res.ok) {
    throw new Error(data.error || `HTTP error ${res.status}`);
  }
  return data;
}

/**
 * Centralized API Client Service for the Stock Trading Platform.
 * Communicates with the backend REST endpoints for trading, portfolio,
 * authentication, market updates, and administration.
 */
export const api = {
  /**
   * Checks the health and operational status of the backend trading engine.
   */
  async checkHealth(): Promise<{ status: string; backend: string; timestamp: string }> {
    const res = await fetch(`${API_BASE}/health`);
    return handleResponse(res);
  },

  /**
   * Authenticates a user by username/email and password.
   */
  async login(usernameOrEmail: string, password: string): Promise<{ success: boolean; user: User }> {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usernameOrEmail, password }),
    });
    return handleResponse(res);
  },

  /**
   * Logs out the current user and clears server session if any.
   */
  async logout(): Promise<{ success: boolean }> {
    const res = await fetch(`${API_BASE}/auth/logout`, { method: 'POST' });
    return handleResponse(res);
  },

  /**
   * Retrieves the live list of all Indian equities (NSE & BSE) listed on the exchange.
   */
  async getStocks(): Promise<Stock[]> {
    const res = await fetch(`${API_BASE}/stocks`);
    return handleResponse(res);
  },

  /**
   * Executes a BUY order for a given stock symbol and quantity.
   * Validates sufficient cash balance and stock float before recording transaction.
   */
  async executeBuy(traderId: string, symbol: string, quantity: number): Promise<{ success: boolean; trade: Trade; portfolio: Portfolio; message: string }> {
    const res = await fetch(`${API_BASE}/trades/buy`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ traderId, symbol, quantity: String(quantity) }),
    });
    return handleResponse(res);
  },

  /**
   * Executes a SELL order for a given stock symbol and quantity.
   * Validates that the trader owns sufficient shares in their portfolio.
   */
  async executeSell(traderId: string, symbol: string, quantity: number): Promise<{ success: boolean; trade: Trade; portfolio: Portfolio; message: string }> {
    const res = await fetch(`${API_BASE}/trades/sell`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ traderId, symbol, quantity: String(quantity) }),
    });
    return handleResponse(res);
  },

  /**
   * Fetches the trade execution history. If traderId is passed, filters for that trader.
   */
  async getTrades(traderId?: string): Promise<Trade[]> {
    const url = traderId ? `${API_BASE}/trades?traderId=${encodeURIComponent(traderId)}` : `${API_BASE}/trades`;
    const res = await fetch(url);
    return handleResponse(res);
  },

  /**
   * Retrieves the real-time valuation, cost basis, profit/loss, and holdings for a trader.
   */
  async getPortfolio(traderId: string): Promise<Portfolio> {
    const res = await fetch(`${API_BASE}/portfolio?traderId=${encodeURIComponent(traderId)}`);
    return handleResponse(res);
  },

  /**
   * Fetches recent news headlines, market updates, and volatility events.
   */
  async getMarketUpdates(): Promise<MarketUpdate[]> {
    const res = await fetch(`${API_BASE}/market/updates`);
    return handleResponse(res);
  },

  /**
   * Manually triggers a simulated market price fluctuation across equities.
   */
  async triggerMarketTick(): Promise<{ success: boolean; message: string }> {
    const res = await fetch(`${API_BASE}/market/tick`, { method: 'POST' });
    return handleResponse(res);
  },

  /**
   * Retrieves trade execution, price volatility, and security notifications for a user.
   */
  async getNotifications(userId: string): Promise<NotificationItem[]> {
    const res = await fetch(`${API_BASE}/notifications?userId=${encodeURIComponent(userId)}`);
    return handleResponse(res);
  },

  /**
   * Marks all alerts/notifications for a given user as read.
   */
  async markNotificationsRead(userId: string): Promise<{ success: boolean }> {
    const res = await fetch(`${API_BASE}/notifications/read`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId }),
    });
    return handleResponse(res);
  },

  async depositFunds(traderId: string, amount: number): Promise<{ success: boolean; cashBalance: number }> {
    const res = await fetch(`${API_BASE}/trader/deposit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ traderId, amount: String(amount) }),
    });
    return handleResponse(res);
  },

  async updateTraderPreferences(traderId: string, emailAlertsEnabled: boolean, alertThresholdPercent: number): Promise<{ success: boolean }> {
    const res = await fetch(`${API_BASE}/trader/preferences`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        traderId,
        emailAlertsEnabled: String(emailAlertsEnabled),
        alertThresholdPercent: String(alertThresholdPercent),
      }),
    });
    return handleResponse(res);
  },

  // Admin APIs
  async getAdminStats(): Promise<AdminStats> {
    const res = await fetch(`${API_BASE}/admin/stats`);
    return handleResponse(res);
  },

  async getAdminUsers(): Promise<User[]> {
    const res = await fetch(`${API_BASE}/admin/users`);
    return handleResponse(res);
  },

  async createAdminUser(data: {
    userId?: string;
    name: string;
    email: string;
    password: string;
    role: 'ADMIN' | 'TRADER';
    initialBalance?: number;
    department?: string;
  }): Promise<{ success: boolean; user: User }> {
    const res = await fetch(`${API_BASE}/admin/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  async updateAdminUser(data: {
    userId: string;
    name?: string;
    email?: string;
    password?: string;
  }): Promise<{ success: boolean; user: User }> {
    const res = await fetch(`${API_BASE}/admin/users/update`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse(res);
  },

  async deleteAdminUser(userId: string): Promise<{ success: boolean; message: string }> {
    const res = await fetch(`${API_BASE}/admin/users/delete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId }),
    });
    return handleResponse(res);
  },

  async getSecurityInfo(): Promise<{
    settings: SecuritySettings;
    healthScore: string;
    openIncidentsCount: number;
    incidents: SecurityIncident[];
  }> {
    const res = await fetch(`${API_BASE}/admin/security`);
    return handleResponse(res);
  },

  async updateSecuritySettings(settings: Partial<SecuritySettings>): Promise<{ success: boolean; settings: SecuritySettings }> {
    const res = await fetch(`${API_BASE}/admin/security`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings),
    });
    return handleResponse(res);
  },

  async getSystemSettings(): Promise<SystemSettings> {
    const res = await fetch(`${API_BASE}/admin/settings`);
    return handleResponse(res);
  },

  async updateSystemSettings(settings: Partial<SystemSettings>): Promise<{ success: boolean; settings: SystemSettings }> {
    const res = await fetch(`${API_BASE}/admin/settings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(settings),
    });
    return handleResponse(res);
  },

  async getAdminReport(type: 'user' | 'trade' | 'portfolio' | 'financial' | 'system'): Promise<{
    type: string;
    title: string;
    generatedAt: string;
    reportText: string;
  }> {
    const res = await fetch(`${API_BASE}/admin/reports?type=${type}`);
    return handleResponse(res);
  },

  async addStock(stock: { symbol: string; companyName: string; price: number; availableQuantity: number }): Promise<{ success: boolean; message: string; stock: Stock }> {
    const res = await fetch(`${API_BASE}/admin/stocks/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        symbol: stock.symbol,
        companyName: stock.companyName,
        price: String(stock.price),
        availableQuantity: String(stock.availableQuantity),
      }),
    });
    return handleResponse(res);
  },

  async updateStock(stock: { symbol: string; companyName?: string; price?: number; availableQuantity?: number }): Promise<{ success: boolean; message: string; stock: Stock }> {
    const res = await fetch(`${API_BASE}/admin/stocks/update`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        symbol: stock.symbol,
        companyName: stock.companyName,
        price: stock.price !== undefined ? String(stock.price) : undefined,
        availableQuantity: stock.availableQuantity !== undefined ? String(stock.availableQuantity) : undefined,
      }),
    });
    return handleResponse(res);
  },

  async deleteStock(symbol: string): Promise<{ success: boolean; message: string }> {
    const res = await fetch(`${API_BASE}/admin/stocks/delete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbol }),
    });
    return handleResponse(res);
  },
};
