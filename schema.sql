-- ==============================================================================
-- INDIAN STOCK TRADING PLATFORM — NON-DESTRUCTIVE PRODUCTION DATABASE SCHEMA (MySQL 8+)
-- Architecture: Express Gateway -> Java HttpServer/Servlets -> Service -> DAO -> JDBC
-- Note: This script NEVER drops existing tables or user data.
-- For destructive development reset + demo accounts, see scripts/dev_reset.sql.
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS stock_trading;
USE stock_trading;

-- ------------------------------------------------------------------------------
-- 0. SCHEMA MIGRATIONS TRACKING TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(50) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 1. USERS TABLE
-- Stores Trader and Administrator credentials, roles, and DECIMAL INR balances.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 50000.00,
    department VARCHAR(100) DEFAULT NULL,
    email_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_threshold_percent DECIMAL(5, 2) NOT NULL DEFAULT 2.00,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('TRADER', 'ADMIN')),
    CONSTRAINT chk_users_balance CHECK (balance >= 0.00),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 2. STOCKS TABLE (Indian Equities: NSE / BSE)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stocks (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) UNIQUE NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    exchange VARCHAR(10) NOT NULL DEFAULT 'NSE',
    price DECIMAL(15, 2) NOT NULL,
    available_quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    previous_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    day_high DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    day_low DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    volume_traded BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_stocks_price CHECK (price > 0.00),
    CONSTRAINT chk_stocks_qty CHECK (available_quantity >= 0),
    CONSTRAINT chk_stocks_status CHECK (status IN ('ACTIVE', 'HALTED', 'DISABLED')),
    INDEX idx_stocks_exchange (exchange)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 3. PORTFOLIO TABLE
-- One holding row per (user_id, symbol), weighted average cost in DECIMAL(15, 2).
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolio (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    stock_id INT NOT NULL DEFAULT 0,
    symbol VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    average_price DECIMAL(15, 2) NOT NULL,
    CONSTRAINT chk_portfolio_qty CHECK (quantity > 0),
    CONSTRAINT chk_portfolio_avg CHECK (average_price >= 0.00),
    CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_portfolio_user_symbol (user_id, symbol),
    INDEX idx_portfolio_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 4. TRANSACTIONS TABLE
-- Immutable audit ledger of BUY/SELL executions, fees, and total cash impact.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    stock_id INT NOT NULL DEFAULT 0,
    symbol VARCHAR(20) NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(15, 2) NOT NULL,
    idempotency_key VARCHAR(100) DEFAULT NULL,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tx_type CHECK (transaction_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_tx_qty CHECK (quantity > 0),
    CONSTRAINT chk_tx_price CHECK (price > 0.00),
    CONSTRAINT chk_tx_total CHECK (total_amount >= 0.00),
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    UNIQUE KEY uq_tx_user_idempotency (user_id, idempotency_key),
    INDEX idx_user_tx (user_id, transaction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 5. TRADE IDEMPOTENCY TABLE
-- Prevents accidental duplicate order execution across retries and concurrency.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_idempotency (
    user_id VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    transaction_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT fk_idemp_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_idemp_tx FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 6. WATCHLIST TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS watchlist (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_symbol (user_id, symbol),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_watchlist (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 7. LOGIN RATE LIMITS TABLE
-- Persistent shared brute-force & progressive temporary throttling state.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_rate_limits (
    rate_key VARCHAR(200) PRIMARY KEY,
    scope VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at BIGINT NOT NULL,
    throttle_until BIGINT NOT NULL DEFAULT 0,
    INDEX idx_rate_throttle_until (throttle_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- NON-SENSITIVE REFERENCE EQUITY CATALOG (Safe for Production)
-- Provision production admin user explicitly via ADMIN_BOOTSTRAP_USER/PASSWORD.
-- ------------------------------------------------------------------------------
INSERT IGNORE INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) VALUES
('RELIANCE', 'Reliance Industries Limited', 'NSE', 2980.50, 50000, 'ACTIVE', 2950.00, 3010.00, 2945.00, 75000),
('TCS', 'Tata Consultancy Services Limited', 'NSE', 4245.00, 35000, 'ACTIVE', 4210.00, 4280.00, 4190.00, 42000),
('HDFCBANK', 'HDFC Bank Limited', 'NSE', 1652.80, 60000, 'ACTIVE', 1640.00, 1665.00, 1635.00, 88000),
('INFY', 'Infosys Limited', 'NSE', 1895.40, 45000, 'ACTIVE', 1880.00, 1910.00, 1875.00, 56000),
('ICICIBANK', 'ICICI Bank Limited', 'NSE', 1280.20, 55000, 'ACTIVE', 1265.00, 1295.00, 1260.00, 62000),
('TATAMOTORS', 'Tata Motors Limited', 'NSE', 985.60, 40000, 'ACTIVE', 970.00, 998.00, 965.00, 94000),
('SBIN', 'State Bank of India', 'NSE', 842.10, 75000, 'ACTIVE', 835.00, 855.00, 830.00, 110000),
('BHARTIARTL', 'Bharti Airtel Limited', 'NSE', 1540.30, 30000, 'ACTIVE', 1525.00, 1558.00, 1520.00, 38000),
('ITC', 'ITC Limited', 'NSE', 492.70, 80000, 'ACTIVE', 488.00, 498.00, 485.00, 82000),
('KOTAKBANK', 'Kotak Mahindra Bank Limited', 'NSE', 1795.50, 25000, 'ACTIVE', 1780.00, 1815.00, 1775.00, 29000),
('LT', 'Larsen & Toubro Limited', 'NSE', 3650.00, 20000, 'ACTIVE', 3620.00, 3690.00, 3610.00, 24000),
('HINDUNILVR', 'Hindustan Unilever Limited', 'NSE', 2740.00, 30000, 'ACTIVE', 2725.00, 2765.00, 2715.00, 31000),
('BAJFINANCE', 'Bajaj Finance Limited', 'NSE', 7120.00, 15000, 'ACTIVE', 7050.00, 7190.00, 7020.00, 18000),
('MARUTI', 'Maruti Suzuki India Limited', 'NSE', 12450.00, 8000, 'ACTIVE', 12300.00, 12550.00, 12250.00, 12000),
('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'NSE', 1780.00, 22000, 'ACTIVE', 1765.00, 1795.00, 1755.00, 26000),
('WIPRO', 'Wipro Limited', 'NSE', 535.80, 40000, 'ACTIVE', 530.00, 542.00, 528.00, 45000),
('TATASTEEL', 'Tata Steel Limited', 'NSE', 154.60, 100000, 'ACTIVE', 152.00, 157.00, 151.00, 140000),
('VEDL', 'Vedanta Limited', 'BSE', 485.00, 50000, 'ACTIVE', 478.00, 492.00, 475.00, 65000);
