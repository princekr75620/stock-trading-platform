-- ==============================================================================
-- DEVELOPMENT-ONLY DESTRUCTIVE RESET & DEMO SEED SCRIPT (MySQL)
-- WARNING: DROPS ALL TABLES AND DESTROYS EXISTING DATA!
-- NEVER run this script in production. Use schema.sql for non-destructive setup.
-- Note: Password hashes below are BCrypt ($2a$12$) for local development ONLY.
-- Change all demo passwords immediately before exposing any non-local environment.
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS stock_trading;
USE stock_trading;

DROP TABLE IF EXISTS trade_idempotency;
DROP TABLE IF EXISTS watchlist;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS portfolio;
DROP TABLE IF EXISTS stocks;
DROP TABLE IF EXISTS users;

-- Run non-destructive schema definitions
SOURCE schema.sql;

-- Development-only demo user accounts (admin123 / trader123)
INSERT INTO users (user_id, name, email, password, role, balance, department) VALUES
('admin', 'Chief Risk Officer', 'admin@tradingplatform.internal', '$2a$12$7pxJbuVHljDPnxDXkjKn9ecECdeJIAnFJq97IYOCfTnbTriYsq8nS', 'ADMIN', 0.00, 'Enterprise Market Operations'),
('trader1', 'Alexander Morgan', 'alex.morgan@quantcap.com', '$2a$12$86egunIjqDaU0ok3e6YvA.IfM/CWcmrP2awjWSuvwX6LqT290lvBK', 'TRADER', 45280.00, NULL),
('trader2', 'Elena Rostova', 'elena.r@hedgealpha.io', '$2a$12$86egunIjqDaU0ok3e6YvA.IfM/CWcmrP2awjWSuvwX6LqT290lvBK', 'TRADER', 85600.00, NULL);

INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES
('trader1', 1, 'RELIANCE', 20, 2900.00),
('trader1', 2, 'TCS', 10, 4150.00),
('trader1', 4, 'INFY', 25, 1850.00);

INSERT INTO watchlist (user_id, symbol) VALUES
('trader1', 'RELIANCE'),
('trader1', 'TCS'),
('trader1', 'INFY'),
('trader2', 'TATAMOTORS'),
('trader2', 'SBIN');
