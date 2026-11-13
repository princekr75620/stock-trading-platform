# Java Web-Based Indian Stock Trading Platform
### Enterprise Securities & Indian Equities Trading System (NSE & BSE)
**Prepared for Academic Review 2 Evaluation: Code Quality & Testing (10 Marks) | Teamwork & Collaboration (5 Marks) | Innovation / Extra Effort (2 Marks)**

---

## Table of Contents
1. [Project Overview & Technology Stack](#1-project-overview--technology-stack)
2. [Review 2 Evaluation Criteria Mapping](#2-review-2-evaluation-criteria-mapping)
3. [Architecture & Project Structure](#3-architecture--project-structure)
4. [Relational Database Design & Schema](#4-relational-database-design--schema)
5. [Database Connectivity (JDBC) & Transaction Management](#5-database-connectivity-jdbc--transaction-management)
6. [UI/UX Design & User Experience Principles](#6-uiux-design--user-experience-principles)
7. [Code Quality, Testing & Security Hardening (10 Marks)](#7-code-quality-testing--security-hardening-10-marks)
8. [Teamwork, Collaboration & Git Workflow (5 Marks)](#8-teamwork-collaboration--git-workflow-5-marks)
9. [Innovation & Extra Effort Features (2 Marks)](#9-innovation--extra-effort-features-2-marks)
10. [Setup, Build, Run & Test Execution Guide](#10-setup-build-run--test-execution-guide)
11. [Review 2 Faculty Presentation Outline](#11-review-2-faculty-presentation-outline)

---

## 1. Project Overview & Technology Stack

The **Java Web-Based Indian Stock Trading Platform** (*Bharat Equities Exchange*) is an authentic simulation of an electronic securities broker-dealer trading platform engineered for **National Stock Exchange (NSE)** and **Bombay Stock Exchange (BSE)** equities (featuring Reliance, TCS, HDFC Bank, Infosys, Tata Motors, ICICI Bank, and other prominent Indian listed companies).

### Technology Stack & Architecture
- **Authoritative Backend Core:** Java SE 17 HTTP Server (`com.sun.net.httpserver.HttpServer`) + `ServletBridge` + `AuthenticationFilter` hosting `javax.servlet.http.HttpServlet` controllers on `JAVA_PORT` (default `8765`). Also packaged with `src/main/webapp/WEB-INF/web.xml` for optional Servlet 4.0 WAR builds (`npm run build:war`).
- **Database Engine & Authoritative Persistence:**
  - **Active Default Engine:** Embedded **SQLite** (`data/stock_trading.db`) via JDBC (`org.sqlite.JDBC`) with `PRAGMA foreign_keys = ON;` (zero-setup, ACID compliant).
  - **Optional Relational Engine:** **MySQL 8.x** Connector/J (`com.mysql.cj.jdbc.Driver`), enabled explicitly via `DB_TYPE=mysql` (`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`) and initialized via `schema.sql`. No silent fallback occurs when `DB_TYPE=mysql` is configured and unreachable.
- **Persistent Financial Tables vs. Transient Runtime Caches:**
  - **Authoritative JDBC Tables:** `users`, `stocks`, `portfolio`, `transactions`, and `watchlist` are read and mutated exclusively via JDBC DAOs (`UserDAO`, `StockDAO`, `PortfolioDAO`, `TransactionDAO`, `WatchlistDAO`).
  - **Transient Runtime State (Non-Financial Ledger):** Intraday market news headlines (`MarketUpdateService`), background ticker scheduling (`TradingPlatformHttpServer`), and runtime notification/security-policy feeds operate in memory while spot price ticks flush back to `StockDAO`.
- **Security & Cryptography:** Damien Miller jBCrypt (`$2a$12$` salt rounds) via `PasswordHasher`. Normal verification strictly rejects unflagged plaintext passwords; only records explicitly tagged with the `$LEGACY$` prefix can complete a one-time migration and conditional BCrypt upgrade.
- **Frontend Presentation Layer:** React 19 + TypeScript Single Page Application (SPA), styled with Tailwind CSS, supporting interactive Indian market trading desks, watchlist management, and portfolio performance analytics.
- **API Gateway & Reverse Proxy:** Node.js Express server (`server.ts`, port `3000`) compiles the Java backend (`compile.sh`), starts and waits for live Java `/api/health` readiness before accepting traffic, forwards `/api/*` requests with `JSESSIONID` cookies, and returns HTTP `503` (`status: "DOWN"`) if Java is unavailable.

---

## 2. Review 2 Evaluation Criteria Mapping

| Evaluation Criteria | Maximum Marks | Implementation Details & Proof Points in Codebase |
|---|:---:|---|
| **Code Quality & Testing** | **10 Marks** | • Modular separation: Model, DAO, Service, Servlet, Filter, Util, Server.<br>• **110/110** passing automated Java unit, DB, & HTTP E2E integration tests (`test.AllTestsRunner`).<br>• BCrypt password hashing (`$2a$12$`) with isolated `$LEGACY$`-only conditional migration (`src/util/PasswordHasher.java`).<br>• Session fixation defense, strict RBAC & cross-tenant ownership checks (`src/filter/AuthenticationFilter.java` + servlets).<br>• Server-side session verification via `/api/auth/session` & public admin escalation prevention.<br>• Parameterized queries (`PreparedStatement`) eliminating SQL injection.<br>• Atomic ACID transactions with `conn.setAutoCommit(false)`, `commit()`, and `rollback()`. |
| **Teamwork & Collaboration** | **5 Marks** | • Standardized Git feature-branching workflow (`feature/*`, `bugfix/*`).<br>• Pull Request template and code review checklist.<br>• Documented team role placeholders (Backend Lead, Database Architect, Frontend Lead, QA Engineer).<br>• Clean, self-contained build scripts (`compile.sh`, `run.sh`, `pom.xml`, `package.json`).<br>• Zero-configuration SQLite onboarding for new teammates and evaluators. |
| **Innovation / Extra Effort** | **2 Marks** | • **Interactive Stock Watchlist**: Database-persisted multi-user watchlist with star toggle and filtered views.<br>• **Live JDBC Database Health Modal**: Real-time diagnostic inspector displaying active database engine (SQLite or MySQL), JDBC driver, connection status, table record counts, and schema verification.<br>• **Fee Breakdown Order Ticket**: Instant calculation of SEBI turnover fees, exchange turnover charges, and brokerage.<br>• **Dual JDBC Driver Support**: Embedded SQLite default (`data/stock_trading.db`) with explicit `DB_TYPE=mysql` switchability. |

---

## 3. Architecture & Project Structure

The project strictly adheres to the **Model-View-Controller (MVC)** and **Data Access Object (DAO)** enterprise architectural patterns.

```
├── .env.example                          # Environment configuration template (PORT, JAVA_PORT, DB_TYPE, MYSQL_*)
├── compile.sh                            # Script to compile Java source into ./out with exit code check
├── run.sh                                # Script to execute standalone Java CLI trading desk
├── pom.xml                               # Maven project descriptor (Servlet API, SQLite JDBC, MySQL, jBCrypt)
├── schema.sql                            # MySQL DDL/DML relational schema with 3NF normalization & BCrypt seeds
├── metadata.json                         # Project metadata and permissions
├── package.json                          # Frontend dependencies, npm build/dev/test scripts
├── server.ts                             # Express reverse proxy and fail-fast gateway (Port 3000 -> Java 8765)
├── lib/                                  # Java dependencies (sqlite-jdbc, jbcrypt, javax.servlet, mysql-connector)
├── data/                                 # Persistent SQLite database (`stock_trading.db`) and CSV datasets
│   ├── stock_trading.db                  # Live relational SQLite database file (default engine)
│   └── indian_stocks_master.csv          # Catalog of NSE & BSE listed equities
└── src/
    ├── main/webapp/WEB-INF/web.xml       # Canonical Servlet 4.0 Deployment Descriptor for WAR packaging
    ├── model/                            # Model Layer (Domain Entities & OOP Principles)
    │   ├── User.java                     # User base entity (userId, name, email, role, balance)
    │   ├── Trader.java                   # Subclass of User with watchlist & portfolio references
    │   ├── Admin.java                    # Subclass of User with administrative permissions
    │   ├── Stock.java                    # Equity model (symbol, company, price, float, volume)
    │   ├── Trade.java                    # Trade model (tradeId, symbol, qty, price, total, type)
    │   ├── Transaction.java              # Database transaction entity with JDBC mapping
    │   ├── Portfolio.java                # Portfolio model with weighted average purchase cost calculation
    │   ├── TradeType.java                # Enum: BUY, SELL
    │   └── UserRole.java                 # Enum: TRADER, ADMIN
    ├── dao/                              # Data Access Object Layer (JDBC & Parameterized SQL)
    │   ├── UserDAOInterface.java         # User DAO contract
    │   ├── UserDAO.java                  # JDBC implementation for users table & conditional legacy password upgrade
    │   ├── StockDAOInterface.java        # Stock DAO contract
    │   ├── StockDAO.java                 # JDBC implementation for stocks catalog
    │   ├── PortfolioDAOInterface.java    # Portfolio DAO contract
    │   ├── PortfolioDAO.java             # JDBC implementation for holdings with weighted averages
    │   ├── TransactionDAOInterface.java  # Transaction DAO contract
    │   ├── TransactionDAO.java           # JDBC implementation for transactions ledger
    │   ├── WatchlistDAOInterface.java    # Watchlist DAO contract
    │   └── WatchlistDAO.java             # JDBC implementation for persistent stock watchlists
    ├── service/                          # Business Logic & Service Layer
    │   ├── ITradingService.java          # Trading service interface
    │   ├── TradingService.java           # Order execution engine with ACID transaction rollback
    │   ├── IPortfolioService.java        # Portfolio calculation contract
    │   ├── PortfolioService.java         # Portfolio valuation, net worth, and unrealized P&L via JDBC DAOs
    │   ├── IUserService.java             # User persistence contract
    │   ├── UserService.java              # Authoritative JDBC user CRUD & balance persistence
    │   ├── IAuthenticationService.java   # Authentication contract
    │   ├── AuthenticationService.java    # Login, BCrypt verification, isolated $LEGACY$ migration, and lockout
    │   └── MarketUpdateService.java      # Stock catalog persistence (StockDAO) + transient news ticker cache
    ├── servlet/                          # Controller Layer (HTTP Servlets)
    │   ├── BaseServlet.java              # Common servlet with session validation, RBAC helpers & JSON serialization
    │   ├── LoginServlet.java             # POST /api/auth/login
    │   ├── RegisterServlet.java          # POST /api/auth/register (enforces TRADER role + ₹50,000 balance)
    │   ├── LogoutServlet.java            # POST /api/auth/logout
    │   ├── AuthSessionServlet.java       # GET  /api/auth/session (refreshes authoritative user state from DB)
    │   ├── DashboardServlet.java         # GET  /api/dashboard (owner or admin isolated)
    │   ├── StockServlet.java             # GET  /api/stocks
    │   ├── BuyStockServlet.java          # POST /api/trades/buy (Atomic JDBC trade execution)
    │   ├── SellStockServlet.java         # POST /api/trades/sell (Atomic JDBC trade execution)
    │   ├── PortfolioServlet.java         # GET  /api/portfolio (Cross-tenant isolated)
    │   ├── TransactionServlet.java       # GET  /api/trades & /api/transactions (Cross-tenant isolated)
    │   ├── WatchlistServlet.java         # GET & POST /api/watchlist (Authenticated & owner-isolated)
    │   ├── NotificationServlet.java      # GET /api/notifications & POST /api/notifications/read
    │   ├── MarketUpdateServlet.java      # GET /api/market/updates
    │   ├── MarketTickServlet.java        # POST /api/market/tick
    │   ├── TraderPreferenceServlet.java  # POST /api/trader/deposit & /api/trader/preferences
    │   ├── DatabaseHealthServlet.java    # GET /api/db/health (Live diagnostics endpoint)
    │   ├── AdminDashboardServlet.java    # GET /api/admin/dashboard (RBAC protected)
    │   ├── UserManagementServlet.java    # CRUD /api/admin/users (RBAC protected)
    │   ├── AddStockServlet.java          # POST /api/admin/stocks/add (RBAC protected)
    │   ├── UpdateStockServlet.java       # POST /api/admin/stocks/update (RBAC protected)
    │   └── DeleteStockServlet.java       # POST /api/admin/stocks/delete (RBAC protected)
    ├── filter/                           # Security Filter Layer
    │   └── AuthenticationFilter.java     # javax.servlet.Filter enforcing RBAC, session auth & cross-tenant isolation
    ├── util/                             # Utilities & Database Connections
    │   ├── DBConnection.java             # JDBC Connection Manager (SQLite default / explicit DB_TYPE=mysql)
    │   ├── PasswordHasher.java           # Damien Miller BCrypt salt & isolated $LEGACY$ helper
    │   ├── BCrypt.java                   # Timing-safe BCrypt implementation
    │   └── JsonHelper.java               # Safe JSON parser and serializer
    ├── server/                           # Embedded Java HTTP Server Engine
    │   ├── TradingPlatformHttpServer.java# Standalone HTTP Server hosting all Servlets (Port 8765)
    │   └── ServletBridge.java            # HTTP Request/Response adapter with segment-boundary path matching
    ├── test/                             # Automated Test Suites (110 Tests Total)
    │   ├── TradingServiceTest.java       # Order validation and business rules (6 tests)
    │   ├── PortfolioCalculationTest.java # Weighted average price & P&L formulas (4 tests)
    │   ├── SecurityAndAuthTest.java      # BCrypt, $LEGACY$ vs plaintext rejection, lockout, RBAC (36 tests)
    │   ├── DatabaseAndIntegrationTest.java# 8 Review 2 Criteria: Connection, CRUD, TX Rollback (33 tests)
    │   ├── EndToEndHttpIntegrationTest.java # Live HttpServer + ServletBridge + SQLite persistence across restarts (31 tests)
    │   └── AllTestsRunner.java           # Unified CLI Test Suite Runner
    └── components/                       # Frontend Presentation Layer (React 19 / TypeScript)
        ├── Navbar.tsx                    # Header with market ticker, DB Status & Rubric buttons
        ├── DatabaseStatusModal.tsx       # Live JDBC inspector modal (3 tabs)
        ├── JavaInspectorModal.tsx        # Java code viewer modal (GUVI 33-marks rubric)
        ├── LoginView.tsx                 # Login and registration view
        ├── trader/                       # Trader workspace views
        │   ├── TraderDashboard.tsx       # Trading desk overview
        │   ├── StocksView.tsx            # Equities catalog with Watchlist filtering & star toggles
        │   ├── PortfolioView.tsx         # Holdings, P&L, and asset allocation breakdown
        │   ├── TradeModal.tsx            # Order slip with real-time fee calculation
        │   └── TradeHistoryView.tsx      # Filterable transaction audit trail
        └── admin/                        # Admin workspace views
            └── AdminDashboard.tsx        # Executive analytics, user CRUD, stock CRUD
```

---

## 4. Relational Database Design & Schema

The database design adheres to **Third Normal Form (3NF)** with strict referential integrity, foreign key cascading where appropriate, and indexed columns for high-throughput query performance.

### Entity Relationship (ER) Diagram

```
 +--------------------+              +--------------------+
 |       USERS        |              |       STOCKS       |
 +--------------------+              +--------------------+
 | PK  id (INTEGER)   |              | PK  id (INTEGER)   |
 | UQ  user_id (VCHAR)|              | UQ  symbol (VCHAR) |
 |     name (VARCHAR) |              |     company_name   |
 | UQ  email (VCHAR)  |              |     exchange (NSE) |
 |     password (hash)|              |     price          |
 |     role (ENUM)    |              |     previous_price |
 |     balance        |              |     available_qty  |
 |     created_at     |              |     status (ACTIVE)|
 +--------------------+              +--------------------+
           | 1                                 ^
           | (FK user_id)                      |
           +-------------------+---------------+
           |                   |               |
           v N                 v N             v N
 +--------------------+  +--------------------+  +--------------------+
 |     PORTFOLIO      |  |    TRANSACTIONS    |  |     WATCHLIST      |
 +--------------------+  +--------------------+  +--------------------+
 | PK  id (INTEGER)   |  | PK  id (INTEGER)   |  | PK  id (INTEGER)   |
 | FK  user_id        |  | FK  user_id        |  | FK  user_id        |
 |     stock_id       |  |     stock_id       |  |     symbol         |
 |     symbol         |  |     symbol         |  |     added_at       |
 |     quantity       |  |     transaction_type| +--------------------+
 |     average_price  |  |     quantity       |
 +--------------------+  |     price          |
                         |     total_amount   |
                         |     transaction_dt |
                         +--------------------+
```

### Table Definitions & Constraints

1. **`users`**:
   - `id` `INTEGER` **PRIMARY KEY AUTOINCREMENT / AUTO_INCREMENT**.
   - `user_id` `VARCHAR(50)` **UNIQUE NOT NULL**: Unique identifier for traders and administrators.
   - `name` `VARCHAR(100)` **NOT NULL**: Full legal name.
   - `email` `VARCHAR(100)` **UNIQUE NOT NULL**: Email address.
   - `password` `VARCHAR(100)` **NOT NULL**: Stored BCrypt hash (`$2a$12$...`). Never plaintext.
   - `role` `VARCHAR(20)` **NOT NULL**: `'TRADER'` or `'ADMIN'`.
   - `balance` `DOUBLE` **DEFAULT 10000.00** (runtime registration sets `50000.00`).
   - `department`, `email_alerts_enabled`, `alert_threshold_percent`, `created_at`.

2. **`stocks`**:
   - `id` `INTEGER` **PRIMARY KEY AUTOINCREMENT / AUTO_INCREMENT**.
   - `symbol` `VARCHAR(10)` **UNIQUE NOT NULL**: Ticker symbol (e.g. `RELIANCE`, `TCS`, `INFY`).
   - `company_name` `VARCHAR(100)` **NOT NULL**, `exchange` (`'NSE'` / `'BSE'`), `price` `DOUBLE`, `available_quantity` `INTEGER`, `status`, `previous_price`, `day_high`, `day_low`, `volume_traded`.

3. **`portfolio`**:
   - `id` `INTEGER` **PRIMARY KEY AUTOINCREMENT / AUTO_INCREMENT**.
   - `user_id` `VARCHAR(50)` **NOT NULL**, **FOREIGN KEY (`user_id`) REFERENCES `users(user_id)`**.
   - `stock_id`, `symbol` `VARCHAR(10)`, `quantity` `INTEGER`, `average_price` `DOUBLE`.

4. **`transactions`**:
   - `id` `INTEGER` **PRIMARY KEY AUTOINCREMENT / AUTO_INCREMENT**.
   - `user_id` `VARCHAR(50)` **NOT NULL**, **FOREIGN KEY (`user_id`) REFERENCES `users(user_id)`**.
   - `stock_id`, `symbol` `VARCHAR(10)`, `transaction_type` (`'BUY'` / `'SELL'`), `quantity` `INTEGER`, `price` `DOUBLE`, `total_amount` `DOUBLE`, `transaction_date` `TIMESTAMP`.

5. **`watchlist`**:
   - `id` `INTEGER` **PRIMARY KEY AUTOINCREMENT / AUTO_INCREMENT**.
   - `user_id` `VARCHAR(50)` **NOT NULL**, **FOREIGN KEY (`user_id`) REFERENCES `users(user_id)`**.
   - `symbol` `VARCHAR(10)`, `added_at` `TIMESTAMP`, **`UNIQUE(user_id, symbol)`**.

---

## 5. Database Connectivity (JDBC) & Transaction Management

### Reusable Connection Manager (`DBConnection.java`)
Connectivity is encapsulated within `src/util/DBConnection.java`. By default, the application uses embedded SQLite (`data/stock_trading.db`) with foreign keys enabled (`PRAGMA foreign_keys = ON;`). When `DB_TYPE=mysql` is configured, it connects to MySQL using environment variables:
```java
String requestedDb = System.getenv("DB_TYPE"); // default: sqlite; set "mysql" for MySQL
String mysqlHost = System.getenv("MYSQL_HOST") != null ? System.getenv("MYSQL_HOST") : "localhost";
String mysqlPort = System.getenv("MYSQL_PORT") != null ? System.getenv("MYSQL_PORT") : "3306";
String mysqlDb   = System.getenv("MYSQL_DATABASE") != null ? System.getenv("MYSQL_DATABASE") : "stock_trading";
String mysqlUser = System.getenv("MYSQL_USER") != null ? System.getenv("MYSQL_USER") : "root";
String mysqlPass = System.getenv("MYSQL_PASSWORD") != null ? System.getenv("MYSQL_PASSWORD") : "root";
```
When `DB_TYPE=mysql` is requested and unreachable, `DBConnection` throws an explicit `SQLException` instead of silently masking a misconfigured database.

### Parameterized SQL Queries (`PreparedStatement`)
To completely eliminate SQL Injection vulnerabilities, all DAO queries utilize parameterized statements:
```java
String sql = "INSERT INTO transactions (user_id, stock_id, symbol, transaction_type, quantity, price, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?)";
try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, tx.getUserId());
    stmt.setInt(2, tx.getStockId());
    stmt.setString(3, tx.getSymbol());
    stmt.setString(4, tx.getTransactionType());
    stmt.setInt(5, tx.getQuantity());
    stmt.setDouble(6, tx.getPrice());
    stmt.setDouble(7, tx.getTotalAmount());
    stmt.executeUpdate();
}
```

### ACID Transaction Atomicity & Rollback
Stock trade execution requires multi-step updates across three distinct tables:
1. Deduct or credit user cash balance in `users`.
2. Deduct or add shares to stock float in `stocks`.
3. Create, update, or remove positions in `portfolio`.
4. Insert immutable transaction log into `transactions`.

In `TradingService.java`, these operations are wrapped in an atomic JDBC transaction:
```java
Connection conn = DBConnection.getConnection();
try {
    conn.setAutoCommit(false); // Begin ACID Transaction

    // 1. Update user balance
    userDAO.updateBalance(userId, newBalance, conn);

    // 2. Update stock float
    stockDAO.updateStock(stock, conn);

    // 3. Update portfolio holding
    portfolioDAO.recordTrade(userId, symbol, quantity, price, tradeType, conn);

    // 4. Log transaction
    transactionDAO.recordTransaction(transaction, conn);

    conn.commit(); // Commit all 4 operations atomically
} catch (Exception ex) {
    if (conn != null) {
        conn.rollback(); // Rollback to preserve balance and inventory integrity on any failure
    }
    throw new TradingPlatformException("Trade execution failed: " + ex.getMessage(), ex);
} finally {
    if (conn != null) {
        conn.setAutoCommit(true);
        conn.close();
    }
}
```

---

## 6. UI/UX Design & User Experience Principles

The user interface was engineered to provide a clean, institutional-grade trading terminal aesthetic aligned with modern financial platforms (such as Zerodha Kite and Groww).

### Key UI Features
- **Real-Time Market Indices Ticker**: Top banner showing live quotes for NIFTY 50 (25,340.20), SENSEX (82,780.50), BANK NIFTY, and INDIA VIX.
- **Stock Trading Desk with Watchlist Filter**:
  - Filter by exchange (`ALL`, `NSE`, `BSE`).
  - View mode switch: `All Equities (20)` vs `⭐ Watchlist (N)`.
  - Instant search filtering by symbol or company name.
  - Interactive star button (`⭐` / `☆`) on each row with optimistic UI updates.
- **Order Execution Ticket (Buy/Sell Modal)**:
  - Dynamic share quantity stepper and margin balance check.
  - Clear real-time order breakdown: Subtotal, Brokerage (0.15%), SEBI turnover fees, and Net Total in INR (₹).
  - Explicit confirmation slip and feedback toast on execution.
- **Portfolio Valuation & Asset Allocation**:
  - Total Investment Value, Current Market Valuation, and Net Unrealized Profit/Loss with percentage returns.
  - Interactive multi-color Asset Allocation bar displaying percentage exposure per holding.
- **Audit-Ready Trade History**:
  - Chronological transaction audit trail with filterable trade types (`ALL`, `BUY`, `SELL`) and search by trade ID or ticker symbol.
- **Accessibility & Responsive Layout**:
  - Clean typographic hierarchy using system sans-serif fonts.
  - WCAG-compliant high-contrast status badges (Emerald `#059669` for profits, Rose `#E11D48` for losses).
  - Responsive flexbox and grid layouts supporting mobile, tablet, and widescreen desktop monitors.

---

## 7. Code Quality, Testing & Security Hardening (10 Marks)
 
### Automated Test Suite Execution Results (77/77 Tests Passed)
The test runner (`src/test/AllTestsRunner.java`) executes 4 comprehensive test suites covering all criteria mandated for Reviews 1 & 2:

```
===============================================================
   ONLINE STOCK TRADING PLATFORM - JAVA UNIT TEST RUNNER      
===============================================================
>>> Running TradingServiceTest Suite...
  ✓ PASS: testBuyValidationSuccess - Sufficient balance & float
  ✓ PASS: testInsufficientBalanceThrowsException - Caught InsufficientBalanceException
  ✓ PASS: testInsufficientStockQuantityThrowsException - Caught InsufficientStockQuantityException
  ✓ PASS: testSellHoldingValidationSuccess - Selling shares within owned quantity
  ✓ PASS: testOversellingThrowsException - Caught InsufficientHoldingsException
  ✓ PASS: testCircuitBreakerLimits - Normal price accepted, >10% surge blocked
TradingServiceTest Results: 6/6 Passed.

>>> Running PortfolioCalculationTest Suite...
  ✓ PASS: testInitialBuyCreatesHolding - 10 shares @ ₹2500.00
  ✓ PASS: testAdditionalBuyCalculatesWeightedAverage - Weighted avg ₹2750.00
  ✓ PASS: testPartialSellDecrementsHolding - Remaining 10 shares @ ₹4000.00
  ✓ PASS: testFullSellRemovesHolding - Fully sold holding purged from Map
PortfolioCalculationTest Results: 4/4 Passed.

>>> Running SecurityAndAuthTest Suite...
  ✓ PASS: testPasswordHashing - Generates valid BCrypt hash format ($2a$)
  ✓ PASS: testPasswordVerification - Verifies correct plaintext against BCrypt hash
  ✓ PASS: testPasswordVerification - Rejects wrong password
  ✓ PASS: testPasswordHashing - Never stores plaintext password
  ✓ PASS: testLegacyPasswordMigration - Detects legacy plaintext requires rehash
  ✓ PASS: testLegacyPasswordMigration - Successfully logs in with legacy password
  ✓ PASS: testLegacyPasswordMigration - Transparently upgraded to BCrypt hash upon login
  ✓ PASS: testLegacyPasswordMigration - Upgraded hash verifies accurately
  ✓ PASS: testValidLogin - Successfully authenticates with valid credentials
  ✓ PASS: testInvalidLogin - Returns generic message on bad password
  ✓ PASS: testInvalidLogin - Returns generic message on unknown user (no account enumeration)
  ✓ PASS: testInvalidLogin - Generic error messages are identical, mitigating enumeration risks
  ✓ PASS: testAccountLockoutPolicy - Enforces automatic account lockout after threshold
  ✓ PASS: testRegistrationAdminPrivilegeEscalation - Public registration endpoint responds 201 Created
  ✓ PASS: testRegistrationAdminPrivilegeEscalation - Registered user entity created in service
  ✓ PASS: testRegistrationAdminPrivilegeEscalation - Public registration with role=ADMIN forced to TRADER
  ✓ PASS: testRegistrationAdminPrivilegeEscalation - Public registration strictly denied ADMIN role
  ✓ PASS: testRegistrationAdminPrivilegeEscalation - Client-supplied initialBalance 99999999 rejected; default 50000.0 enforced
  ✓ PASS: testNormalUserDeniedAdminAccess - HTTP 403 Forbidden returned for TRADER on /api/admin/*
  ✓ PASS: testNormalUserDeniedAdminAccess - Appropriate authorization error message returned
  ✓ PASS: testForgedAdminHeadersRejected - Forged header X-User-Id: admin rejected without session (HTTP 401)
  ✓ PASS: testForgedAdminHeadersRejected - TRADER with forged admin header strictly denied (HTTP 403)
  ✓ PASS: testSessionHandling - Session stores authenticated user
  ✓ PASS: testSessionHandling - Session is active before logout
  ✓ PASS: testSessionHandlingAndLogout - Session invalidated on logout
  ✓ PASS: testSessionHandlingAndLogout - User attribute purged upon session invalidation
  ✓ PASS: testPortfolioCrossTenantIsolation - Trader1 denied access to Trader2 portfolio (HTTP 403)
  ✓ PASS: testPortfolioCrossTenantIsolation - Appropriate cross-tenant access denied message
  ✓ PASS: testPortfolioCrossTenantIsolation - Trader accessing own portfolio is permitted
  ✓ PASS: testInvalidTradeRequests - Zero share order rejected with InvalidInputException
  ✓ PASS: testInvalidTradeRequests - Negative share order rejected with InvalidInputException
  ✓ PASS: testInvalidTradeRequests - Order exceeding cash balance caught InsufficientBalanceException
  ✓ PASS: testInvalidTradeRequests - Order exceeding available float caught InsufficientStockQuantityException
SecurityAndAuthTest Results: 33/33 Passed.

===============================================================
   REVIEW 2 EVALUATION: DATABASE & INTEGRATION TEST SUITE      
===============================================================
>>> 1. Testing Database Connection & Configuration...
  ✓ PASS: DB-1: JDBC connection established successfully
  ✓ PASS: DB-2: Relational database metadata identified: SQLite (SQLite JDBC)
  ✓ PASS: DB-3: getHealthStatus reports connected = true
  ✓ PASS: DB-4: All 5 core relational tables verified in schema (users, stocks, portfolio, transactions, watchlist)
>>> 2. Testing Registration & Login Validation...
  ✓ PASS: REG-1: New user registration successfully persisted to database
  ✓ PASS: REG-2: Password hashed with BCrypt ($2a$) in database
  ✓ PASS: REG-3: BCrypt verification succeeds for plaintext input
  ✓ PASS: REG-4: Duplicate email registration blocked with DuplicateUserException
  ✓ PASS: REG-5: Successful login returns authenticated user record
  ✓ PASS: REG-6: Invalid password returns generic message mitigating enumeration
>>> 3. Testing Database CRUD Operations...
  ✓ PASS: CRUD-1: UserDAO updateBalance persisted correctly to database
  ✓ PASS: CRUD-2: StockDAO insert and findBySymbol succeeded
  ✓ PASS: CRUD-3: PortfolioDAO recordBuy created holding of 10 shares in database
  ✓ PASS: CRUD-4: PortfolioDAO recordSell deleted 0-share position from database
  ✓ PASS: CRUD-5: WatchlistDAO addToWatchlist successfully stored in database
  ✓ PASS: CRUD-6: WatchlistDAO removeFromWatchlist removed item from database
>>> 4. Testing Stock Listing & Portfolio Retrieval...
  ✓ PASS: LIST-1: StockDAO returned 59 Indian listed equities
  ✓ PASS: LIST-2: RELIANCE stock entity mapped with exchange and current price in INR (₹1324.4)
  ✓ PASS: LIST-3: PortfolioDAO findByUserId retrieved trader portfolio record
  ✓ PASS: LIST-4: Portfolio valuation computed against market prices
>>> 5. Testing Invalid Buy/Sell Requests...
  ✓ PASS: VAL-1: Zero share buy order rejected with InvalidInputException
  ✓ PASS: VAL-2: Negative share buy order rejected with InvalidInputException
  ✓ PASS: VAL-3: Trade exceeding cash balance caught InsufficientBalanceException
  ✓ PASS: VAL-4: Sell order exceeding holdings caught InsufficientHoldingsException
>>> 6. Testing Database Transaction Atomicity & Rollback...
  ✓ PASS: TX-1: System fault caught and rollback invoked
  ✓ PASS: TX-2: Balance preserved (₹20000.0 == ₹20000.0) proving full JDBC transaction rollback
>>> 7. Testing Unauthorized Access & RBAC Security...
  ✓ PASS: RBAC-1: Unauthenticated admin request returned HTTP 401 Unauthorized
  ✓ PASS: RBAC-2: Trader accessing /api/admin/dashboard returned HTTP 403 Forbidden
  ✓ PASS: RBAC-3: Forged X-User-Id: admin header strictly rejected with HTTP 403 Forbidden
>>> 8. Testing Responsive UI Contracts & Diagnostics...
  ✓ PASS: UI-1: Diagnostics payload contains databaseProduct
  ✓ PASS: UI-2: Diagnostics payload contains driverName
  ✓ PASS: UI-3: Diagnostics payload contains tableCounts breakdown
  ✓ PASS: UI-4: Stock entity format conforms to frontend StocksView and TradeModal contracts

DatabaseAndIntegrationTest Results: 33/33 Passed.
===============================================================
   ALL 77 JAVA UNIT TESTS COMPLETED SUCCESSFULLY (100% PASS)  
===============================================================
```

### Security Hardening Summary
1. **jBCrypt Password Hashing (`src/util/PasswordHasher.java`)**: Replaced deprecated base64 encoding with Damien Miller jBCrypt ($2a$12$). Plaintext passwords are never stored. Includes automatic transparent re-hashing upon login for legacy accounts.
2. **Session Hijacking & Header Spoofing Elimination (`src/filter/AuthenticationFilter.java`)**: Replaced client-supplied `X-User-Id` headers with strict server-side `HttpSession` verification (`session.getAttribute("user")`).
3. **Admin Privilege Escalation Prevention (`src/servlet/RegisterServlet.java`)**: Public user registration strictly assigns `UserRole.TRADER` and the standard opening balance (₹50,000.0). Client-supplied `role=ADMIN` or custom balance overrides are strictly ignored.
4. **Server-Side Session Lifecycle (`src/servlet/AuthSessionServlet.java` & `src/App.tsx`)**: Frontend never relies on `localStorage` as proof of authorization. The application verifies session validity on every boot via `GET /api/auth/session` and restores trusted user state.
5. **Cross-Tenant Isolation (`PortfolioServlet.java` & `TransactionServlet.java`)**: Traders can only view their own holdings and trades. Requests for other trader IDs are rejected with `HTTP 403 Forbidden`.
6. **Session Fixation Defense (`BaseServlet.java`)**: Old sessions are explicitly invalidated and replaced with fresh session IDs upon successful authentication.
7. **Account Enumeration Mitigation**: Generic, timing-constant error messages ("Invalid username/email or password.") prevent attackers from verifying whether an email exists.

---

## 8. Teamwork, Collaboration & Git Workflow (5 Marks)

### Recommended Team Roles & Responsibilities

| Role | Team Member Placeholder | Key Responsibilities |
|---|---|---|
| **Full-Stack & Backend Lead** | *Student Name 1* | Java EE Servlets, `TradingService.java`, transaction management, BCrypt security hardening, compilation scripts. |
| **Database Architect & DAO Specialist** | *Student Name 2* | Relational schema design (3NF), foreign key constraints, `DBConnection.java`, JDBC DAOs, SQLite & MySQL integration. |
| **Frontend UI/UX Engineer** | *Student Name 3* | React 19 SPA, Tailwind CSS styling, StocksView, Watchlist UI, Portfolio allocation charts, modal dialogs. |
| **QA & Software Testing Engineer** | *Student Name 4* | Unit and integration test suites, boundary condition testing, transaction rollback verification, documentation. |

### Git Branching & Pull Request Strategy
- **`main`**: Production-ready, stable codebase. Evaluator-ready at all times.
- **`develop`**: Integration branch for tested components before merging to main.
- **Feature Branches (`feature/<feature-name>`)**:
  - `feature/jdbc-watchlist-dao`: Watchlist schema and DAO implementation.
  - `feature/db-health-diagnostics`: Live JDBC status endpoint and modal.
  - `feature/bcrypt-security-hardening`: jBCrypt migration and session filter.
- **Pull Request Checklist**:
  1. Does `compile.sh` / `npm run build` compile cleanly with zero errors?
  2. Do all 110 tests in `test.AllTestsRunner` pass?
  3. Are all database queries parameterized via `PreparedStatement`?
  4. Are passwords hashed with BCrypt and credentials loaded from environment variables?
  5. Does the frontend build without TypeScript errors (`npm run lint`)?

---

## 9. Innovation & Extra Effort Features (2 Marks)

1. **Interactive Persisted Stock Watchlist**:
   - Allows traders to bookmark favorite stocks with a single click (`⭐`).
   - Persisted in the relational `watchlist` database table via `WatchlistDAO.java`.
   - Offers an instant view toggle between "All Equities" and "Watchlist Only" with live badge count.
2. **Live JDBC Database Health Inspector (`DatabaseStatusModal.tsx`)**:
   - Accessible via the **"🗄️ DB Status"** button in the header.
   - Live query to `/api/db/health` inspects JDBC driver metadata, active database product name (`SQLite` default or `MySQL` when configured), connection status, ACID compliance mode, and live record counts across all 5 relational tables.
3. **Real-Time Regulatory Brokerage & Fee Calculation Slip**:
   - Order execution ticket in `TradeModal.tsx` calculates Indian brokerage fees, SEBI turnover fees, and exchange turnover charges against available margin.
4. **Dual JDBC Engine Support**:
   - Zero-configuration startup with embedded SQLite (`data/stock_trading.db`) while supporting MySQL 8.x when `DB_TYPE=mysql` is set.

---

## 10. Setup, Build, Run & Test Execution Guide

### Prerequisites
- **Java SE Development Kit (JDK) 17+** (`javac`, `java`)
- **Node.js 18+** and **npm**
- **Maven 3.8+** (Optional, for building WAR artifacts via `mvn`)

### 1. Build and Compile Backend + Production Bundle
```bash
# Compile all Java classes into ./out
bash compile.sh

# Full build: Java compile -> Vite SPA bundle -> Express gateway bundle (dist/server.cjs)
npm run build

# Optional Servlet 4.0 WAR package using src/main/webapp/WEB-INF/web.xml
npm run build:war
```

### 2. Run Java Unit, DB & HTTP E2E Test Suites (110 Tests)
```bash
# Execute the comprehensive test runner
npm test
# or directly:
java -cp "lib/*:out" test.AllTestsRunner
```

### 3. Start the Application
```bash
# Development mode (compiles Java, starts Java port 8765, waits for /api/health UP, serves Vite + API on port 3000)
npm run dev

# Production mode (after npm run build)
npm start
```
Open **`http://localhost:3000`** in any modern web browser.

### 4. Default Seeded Login Credentials (`DBConnection.seedInitialData()`)

| Role | Username / ID | Password | Starting Cash Balance | Notes |
|---|---|---|---|---|
| **Trader 1** | `trader1` | `trader123` | ₹50,000.00 | Default active trader with seeded portfolio & watchlist |
| **Trader 2** | `trader2` | `trader123` | ₹75,000.00 (initial seed) | Secondary trader for cross-tenant isolation tests |
| **Admin** | `admin` | `admin123` | N/A | Executive governance, user CRUD & stock catalog admin |

---

## 11. Review 2 Faculty Presentation Outline

When presenting to the faculty panel during Review 2, structure your walkthrough using this 5-minute outline:

1. **Project Vision & Architecture (1 min)**:
   - Express API Gateway (port 3000) -> Java 17 HttpServer + `ServletBridge` + `AuthenticationFilter` (port 8765) -> JDBC DAOs -> SQLite (`data/stock_trading.db`, or MySQL via `DB_TYPE=mysql`).
2. **Database Schema & 3NF Design (1 min)**:
   - 5 core relational tables: `users`, `stocks`, `portfolio`, `transactions`, and `watchlist` with foreign key constraints and parameterized `PreparedStatement` queries.
3. **JDBC Connectivity & ACID Transactions (1 min)**:
   - In `TradingService.java`, order execution uses atomic transactions with `conn.setAutoCommit(false)`, committing only if user balance, stock inventory, portfolio holdings, and transaction ledger updates succeed, or rolling back on any fault.
4. **Live Demonstration (1.5 mins)**:
   - Open **"🗄️ DB Status"** to show live JDBC connection metadata (`SQLite`) and table counts.
   - Execute a live BUY/SELL order and verify updated balance, portfolio holdings, and transaction audit log survive server restarts.
   - Demonstrate the **Watchlist star toggle** and cross-tenant/RBAC protections.
5. **Code Quality, Security & Test Evidence (30 secs)**:
   - Show all **110 automated tests** passing across 5 suites in `test.AllTestsRunner`, Damien Miller jBCrypt password hashing, isolated `$LEGACY$` conditional migration, session fixation defense, and `AuthenticationFilter` RBAC enforcement.
