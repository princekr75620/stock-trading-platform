export type UserRole = 'ADMIN' | 'TRADER';

export interface User {
  userId: string;
  name: string;
  email: string;
  role: UserRole;
  createdAt: string;
  cashBalance?: number;
  emailAlertsEnabled?: boolean;
  alertThresholdPercent?: number;
  watchlist?: string[];
  department?: string;
}

export interface Stock {
  symbol: string;
  name: string;
  exchange?: string;
  status?: string;
  currentPrice: number;
  previousPrice: number;
  priceChange: number;
  priceChangePercent: number;
  availableQuantity: number;
  dayHigh: number;
  dayLow: number;
  volumeTraded: number;
}

export interface Trade {
  tradeId: string;
  traderId: string;
  stockSymbol: string;
  stockName: string;
  quantity: number;
  tradeType: 'BUY' | 'SELL';
  price: number;
  totalAmount: number;
  dateTime: string;
}

export interface Holding {
  symbol: string;
  name: string;
  quantity: number;
  avgPurchasePrice: number;
  currentPrice: number;
  investmentValue: number;
  currentValue: number;
  profitLoss: number;
  profitLossPercent: number;
}

export interface Portfolio {
  traderId: string;
  cashBalance: number;
  currentPortfolioValue: number;
  totalInvestmentValue: number;
  unrealizedProfitLoss: number;
  unrealizedProfitLossPercent: number;
  realizedProfitLoss: number;
  totalNetWorth: number;
  holdingsCount: number;
  holdings: Holding[];
}

export interface MarketUpdate {
  updateId: string;
  stockSymbol: string;
  stockName: string;
  headline: string;
  currentPrice: number;
  priceDelta: number;
  percentChange: number;
  timestamp: string;
}

export interface NotificationItem {
  notificationId: string;
  recipientUserId: string;
  title: string;
  message: string;
  type: 'TRADE_BUY' | 'TRADE_SELL' | 'MARKET_ALERT' | 'SECURITY_ALERT' | 'SYSTEM_EVENT';
  isRead: boolean;
  timestamp: string;
}

export interface SecuritySettings {
  minimumPasswordLength: number;
  requireSpecialCharacters: boolean;
  passwordExpiryDays: number;
  maxFailedLoginAttempts: number;
  sessionTimeoutMinutes: number;
  autoLockoutEnabled: boolean;
  highValueTradeThreshold: number;
  requireTwoStepForHighValue: boolean;
  ipAnomalyDetectionEnabled: boolean;
}

export interface SecurityIncident {
  incidentId: string;
  userId: string;
  incidentType: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED';
  resolved: boolean;
  timestamp: string;
}

export interface SystemSettings {
  exchangeName: string;
  tradingFeePercent: number;
  tradingStatus: 'OPEN' | 'CLOSED' | 'PRE_MARKET' | 'AFTER_HOURS';
  maintenanceMode: boolean;
  marketUpdateIntervalSeconds: number;
  systemVersion: string;
  maxConcurrentUsers: number;
  systemStatus: string;
  metrics?: Record<string, string>;
}

export interface AdminStats {
  totalUsers: number;
  totalTraders: number;
  totalAdmins: number;
  totalTrades: number;
  buyTrades: number;
  sellTrades: number;
  totalTradingVolume: number;
  feeRevenue: number;
  systemStatus: string;
  tradingStatus: string;
  securityStatus: string;
  openIncidents: number;
}
