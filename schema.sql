-- ==============================================================================
-- GUVI COLLEGE EVALUATION: INDIAN STOCK TRADING PLATFORM DATABASE SCHEMA (MySQL)
-- Architecture: Frontend -> Servlets -> Service -> DAO -> JDBC -> MySQL
-- Database: stock_trading
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS stock_trading;
USE stock_trading;

DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS portfolio;
DROP TABLE IF EXISTS stocks;
DROP TABLE IF EXISTS users;

-- ------------------------------------------------------------------------------
-- 1. USERS TABLE
-- Stores Trader and Administrator credentials, roles, and INR balances.
-- Encapsulation: User, Trader, Admin
-- ------------------------------------------------------------------------------
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL, -- 'TRADER' or 'ADMIN'
    balance DOUBLE DEFAULT 50000.00,
    department VARCHAR(100) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 2. STOCKS TABLE (Indian Equities: NSE / BSE)
-- Stores listed equities with real-time price quotes, available float, and exchange.
-- ------------------------------------------------------------------------------
CREATE TABLE stocks (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(10) UNIQUE NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    exchange VARCHAR(10) DEFAULT 'NSE', -- 'NSE' or 'BSE'
    price DOUBLE NOT NULL,
    available_quantity INT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE', -- 'ACTIVE' or 'DISABLED'
    previous_price DOUBLE DEFAULT 0.0,
    day_high DOUBLE DEFAULT 0.0,
    day_low DOUBLE DEFAULT 0.0,
    volume_traded BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 3. PORTFOLIO TABLE
-- Tracks Trader holdings, share count, and weighted average cost basis (₹).
-- ------------------------------------------------------------------------------
CREATE TABLE portfolio (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    stock_id INT DEFAULT 0,
    symbol VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    average_price DOUBLE NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_symbol (user_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------------
-- 4. TRANSACTIONS TABLE
-- Auditable ledger of all BUY and SELL transactions executed on the exchange.
-- ------------------------------------------------------------------------------
CREATE TABLE transactions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) NOT NULL,
    stock_id INT DEFAULT 0,
    symbol VARCHAR(10) NOT NULL,
    transaction_type VARCHAR(10) NOT NULL, -- 'BUY' or 'SELL'
    quantity INT NOT NULL,
    price DOUBLE NOT NULL,
    total_amount DOUBLE NOT NULL,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_tx (user_id, transaction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==============================================================================
-- INITIAL SEED DATA (Authentic Indian Market Equities - NSE / BSE)
-- ==============================================================================

-- Seed Users (Admins and Traders)
INSERT INTO users (user_id, name, email, password, role, balance, department) VALUES
('admin', 'Chief Risk Officer', 'admin@apex.com', 'admin123', 'ADMIN', 0.0, 'Enterprise Market Operations'),
('trader1', 'Alex Morgan', 'alex@apex.com', 'trader123', 'TRADER', 46444.80, NULL),
('trader2', 'Sarah Jenkins', 'sarah@apex.com', 'trader123', 'TRADER', 75000.00, NULL);

-- Seed Indian Equities (NIFTY 50 / NSE / BSE)
INSERT INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) VALUES
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

-- Seed Initial Portfolios (Indian Equities in ₹)
INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES
('trader1', 1, 'RELIANCE', 20, 2900.00),
('trader1', 2, 'TCS', 10, 4150.00),
('trader1', 4, 'INFY', 25, 1850.00);

-- Seed Initial Transactions (Auditable Ledger)
INSERT INTO transactions (user_id, stock_id, symbol, transaction_type, quantity, price, total_amount, transaction_date) VALUES
('trader1', 1, 'RELIANCE', 'BUY', 20, 2900.00, 58000.00, DATE_SUB(NOW(), INTERVAL 3 DAY)),
('trader1', 2, 'TCS', 'BUY', 10, 4150.00, 41500.00, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('trader1', 4, 'INFY', 'BUY', 25, 1850.00, 46250.00, DATE_SUB(NOW(), INTERVAL 1 DAY));
