# Java Web-Based Indian Stock Trading Platform
### Enterprise Securities & Indian Equities Trading System (NSE & BSE Reference Simulation)

---

## Table of Contents
1. [Project Overview & Technology Stack](#1-project-overview--technology-stack)
2. [Architecture & Project Structure](#2-architecture--project-structure)
3. [Relational Database Design & Schema](#3-relational-database-design--schema)
4. [JDBC Connectivity, MySQL TLS & Bounded Connection Pooling](#4-jdbc-connectivity-mysql-tls--bounded-connection-pooling)
5. [Database Migrations, Backup & Rollback Procedures](#5-database-migrations-backup--rollback-procedures)
6. [Environment Variables Reference (Node.js vs Java)](#6-environment-variables-reference-nodejs-vs-java)
7. [Security Architecture & Local-Only Demo Accounts](#7-security-architecture--local-only-demo-accounts)
8. [Automated Testing & Verification Results (183/183 Passed)](#8-automated-testing--verification-results-183183-passed)
9. [Setup, Build, CI & Production Deployment Guide](#9-setup-build-ci--production-deployment-guide)

---

## 1. Project Overview & Technology Stack

The **Java Web-Based Indian Stock Trading Platform** is a full-stack simulated equity broker-dealer platform modeled after Indian equities (**NSE** and **BSE** symbols such as Reliance, TCS, HDFC Bank, Infosys, Tata Motors, ICICI Bank, etc.) using INR (`₹`) currency arithmetic.

> **Market Data Disclaimer (Simulated / Delayed Reference Feed):**
> Stock quotes in `MarketUpdateService` / `LiveMarketDataService` combine best-effort Yahoo Finance chart lookups with a deterministic simulated micro-tick fallback when remote quotes are unavailable, and the top ticker bar (`Navbar.tsx`) displays reference index levels. **This platform does not connect to a direct paid live NSE/BSE exchange order book feed.** All account balances, portfolio holdings, fee calculations, and trade settlements are strictly persisted in the relational JDBC database.

### Technology Stack & Architecture
- **Authoritative Backend Core:** Java SE 17 `HttpServer` + `ServletBridge` + `AuthenticationFilter` hosting `javax.servlet.http.HttpServlet` controllers on `JAVA_PORT` (default `8765`), plus `src/main/webapp/WEB-INF/web.xml` for Servlet 4.0 WAR packaging (`npm run build:war`).
- **Database Engine & Authoritative Persistence:**
  - **Development Default Engine:** Embedded **SQLite** (`data/stock_trading.db`) via `org.sqlite.JDBC` with `PRAGMA foreign_keys = ON`, `busy_timeout=5000`, and `transaction_mode=IMMEDIATE`.
  - **Production Relational Engine:** **MySQL 8.x** (`com.mysql.cj.jdbc.Driver`), enabled explicitly via `DB_TYPE=mysql`, enforcing TLS certificate verification (`VERIFY_IDENTITY` / `VERIFY_CA`), `DECIMAL(15, 2)` monetary columns, and pessimistic `FOR UPDATE` row locking. No silent fallback to SQLite or in-memory maps occurs when `DB_TYPE=mysql` is configured.
- **Persistent Financial Ledger vs. Transient Runtime State:**
  - **Authoritative JDBC Tables:** `users`, `stocks`, `portfolio`, `transactions`, `trade_idempotency`, `watchlist`, and `schema_migrations` are read and mutated exclusively via JDBC DAOs (`UserDAO`, `StockDAO`, `PortfolioDAO`, `TransactionDAO`, `WatchlistDAO`).
  - **Transient Runtime State:** Intraday headline news ticker cache (`MarketUpdateService`), runtime notification feed (`NotificationService`), and security alert policy state operate in memory while stock price updates flush to `StockDAO`.
- **Financial Precision:** `java.math.BigDecimal` (`scale=2`, `RoundingMode.HALF_UP`) via `util.MoneyUtil` across order totals, 0.15% brokerage fees, cash balances, weighted-average cost basis, and dashboard/portfolio valuations.
- **Security & Cryptography:** Damien Miller jBCrypt (`$2a$12$`) via `PasswordHasher`, session-bound CSRF tokens + `Origin`/`Referer` validation, session fixation defense, sliding inactivity timeouts, owner-only/RBAC authorization (`AuthenticationFilter`), and fail-fast startup environment validation.
- **API Gateway & SPA Frontend:** Express gateway (`server.ts`, port `3000`, `dotenv/config`) + React 19 / TypeScript / Tailwind CSS SPA.

---

## 2. Architecture & Project Structure

```
├── .env.example                          # Non-secret environment template annotated by process ([Node.js], [Java], [Both])
├── .github/workflows/ci.yml              # GitHub Actions CI pipeline (JDK 17 + Node 22)
├── compile.sh                            # Java compilation script into ./out (exits non-zero on error)
├── pom.xml                               # Maven descriptor for Servlet 4.0 WAR packaging
├── schema.sql                            # Non-destructive MySQL 8+ DDL (DECIMAL(15, 2), UNIQUE & FK constraints)
├── package.json                          # Build, lint, test, dev, and start scripts
├── server.ts                             # Express reverse proxy + dotenv + production startup guard
├── lib/                                  # JDBC drivers & runtime JARs (sqlite-jdbc, mysql-connector-j, servlet-api)
├── data/                                 # Local SQLite database (`stock_trading.db`) and NSE/BSE equity CSV catalog
└── src/
    ├── main/webapp/WEB-INF/web.xml       # Canonical Servlet 4.0 Deployment Descriptor
    ├── model/                            # Domain models (User, Trader, Admin, Stock, Portfolio, Transaction, Trade)
    ├── dao/                              # Parameterized JDBC DAOs with row locking & atomic SQL guards
    ├── service/                          # TradingService (ACID TX), AuthenticationService, MarketUpdateService, etc.
    ├── servlet/                          # HTTP Servlets + BaseServlet RBAC/ownership helpers + DatabaseHealthServlet
    ├── filter/                           # AuthenticationFilter (Session auth, CSRF, Origin/Referer, RBAC, body limits)
    ├── util/                             # DBConnection, BoundedConnectionPool, EnvConfig, MoneyUtil, PasswordHasher
    ├── server/                           # TradingPlatformHttpServer, ServletBridge, JsonHelper
    ├── test/                             # 5 automated Java test suites (183 tests) + AllTestsRunner
    └── components/                       # React 19 + TypeScript UI workspace
```

---

## 3. Relational Database Design & Schema

All monetary columns use exact fixed-point **`DECIMAL(15, 2)`** across both MySQL (`schema.sql`) and SQLite (`DBConnection.java`) schemas:

1. **`users`**:
   - `id` (`INTEGER`/`INT` PK), `user_id` (`VARCHAR(50) UNIQUE`), `name`, `email` (`UNIQUE`), `password` (`$2a$12$...` BCrypt hash), `role` (`'TRADER' | 'ADMIN'`), `balance` (`DECIMAL(15, 2) CHECK (balance >= 0)`), `department`, `email_alerts_enabled`, `alert_threshold_percent` (`DECIMAL(5, 2)`), `must_change_password`, `created_at`.
2. **`stocks`**:
   - `id` PK, `symbol` (`VARCHAR(20) UNIQUE`), `company_name`, `exchange` (`'NSE' | 'BSE'`), `price` (`DECIMAL(15, 2) CHECK (price > 0)`), `available_quantity`, `status` (`'ACTIVE' | 'HALTED' | 'DISABLED'`), `previous_price`, `day_high`, `day_low` (all `DECIMAL(15, 2)`), `volume_traded`.
3. **`portfolio`**:
   - `id` PK, `user_id` FK -> `users(user_id) ON DELETE CASCADE`, `stock_id`, `symbol`, `quantity` (`CHECK (quantity > 0)`), `average_price` (`DECIMAL(15, 2)`), `UNIQUE(user_id, symbol)`.
4. **`transactions`**:
   - `id` PK, `user_id` FK -> `users(user_id)`, `stock_id`, `symbol`, `transaction_type` (`'BUY' | 'SELL'`), `quantity`, `price`, `fee_amount`, `total_amount` (`DECIMAL(15, 2)`), `idempotency_key`, `transaction_date`, `UNIQUE(user_id, idempotency_key)`.
5. **`trade_idempotency`**:
   - Composite PK `(user_id, idempotency_key)`, FK -> `transactions(id)`.
6. **`watchlist`**:
   - `id` PK, `user_id` FK -> `users(user_id) ON DELETE CASCADE`, `symbol`, `added_at`, `UNIQUE(user_id, symbol)`.
7. **`schema_migrations`**:
   - `version` (`VARCHAR(50)` PK), `description`, `applied_at`.

---

## 4. JDBC Connectivity, MySQL TLS & Bounded Connection Pooling

### MySQL TLS & Least-Privilege Policy (`DBConnection.java`)
- Insecure URL options such as `useSSL=false` and `allowPublicKeyRetrieval=true` are **completely removed**.
- All MySQL JDBC URLs explicitly configure **`tlsVersions=TLSv1.2,TLSv1.3`** and validate `sslMode`:
  - **`APP_ENV=production`**: Requires `DB_TYPE` explicitly configured (`mysql` or `sqlite`). For MySQL, `DB_SSL_MODE` **must** be `VERIFY_IDENTITY` (default in production) or `VERIFY_CA`, **`root` DB user is rejected**, and default/weak/placeholder passwords (<8 chars or default strings) cause immediate startup failure.
  - **Custom CA / Truststore Support**: Set `DB_SSL_CA` to a readable `.pem`/`.crt`/`.cer` CA bundle (automatically loaded into an ephemeral runtime `PKCS12` truststore) or a `.p12`/`.jks` truststore paired with `DB_SSL_TRUSTSTORE_TYPE` and `DB_SSL_TRUSTSTORE_PASSWORD`.

Example least-privilege MySQL grant (run as DBA; never grant `ALL PRIVILEGES ON *.*` or run app as `root`):
```sql
CREATE USER 'stock_app_user'@'%' IDENTIFIED BY '<STRONG_RANDOM_PASSWORD>' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON stock_trading.* TO 'stock_app_user'@'%';
FLUSH PRIVILEGES;
```

### Bounded Connection Pool (`BoundedConnectionPool.java`)
- Replaces unbounded pooling with a lock/condition-guarded pool (`maxPoolSize`, `minIdle`, `connectionTimeoutMs`, `maxLifetimeMs`):
  - Enforces strict `totalConnections <= maxPoolSize`.
  - Fails fast with `SQLException` when acquisition exceeds `DB_POOL_TIMEOUT_MS` rather than spawning extra connections.
  - Validates idle connections (`isClosed()` + `isValid(2)`) and retires expired connections beyond `maxLifetimeMs`.
  - Rolls back uncommitted work, resets `autoCommit=true`, and clears warnings before returning any pooled connection.
  - Registers JVM shutdown hook (`DBConnection.shutdownPool()`).

---

## 5. Database Migrations, Backup & Rollback Procedures

### Idempotent Schema & Upgrade Migrations (`DBConnection.runMigrations`)
On startup, `DBConnection.initializeSchemaAndMigrate` creates missing base tables and executes idempotent migrations tracked in `schema_migrations`:
- `001_users_columns_and_indexes`: Adds `email_alerts_enabled`, `alert_threshold_percent`, `must_change_password`, and `idx_users_role`.
- `002_transactions_fee_and_idempotency`: Adds `fee_amount`, `idempotency_key`, `uq_tx_user_idempotency`, and `idx_user_tx`.
- `003_portfolio_unique_and_consistent_indexes`: Enforces `UNIQUE(user_id, symbol)` on `portfolio` and consistent indexes across SQLite & MySQL.
- `004_monetary_decimal_precision`:
  - **MySQL**: Inspects `INFORMATION_SCHEMA.COLUMNS` and executes `ALTER TABLE ... MODIFY COLUMN ... DECIMAL(15, 2)` on any legacy floating-point monetary columns in-place without dropping tables.
  - **SQLite**: Inspects `PRAGMA table_info` for legacy `DOUBLE`/`FLOAT`/`REAL` monetary columns; wraps table rebuilds in an ACID transaction with pre/post row-count and monetary sum parity verification before swapping tables.

### Pre-Upgrade Backup & Rollback Procedure
Always back up before applying schema upgrades or major releases in production:

**MySQL Backup:**
```bash
mysqldump --single-transaction --routines --triggers -h "$MYSQL_HOST" -u "$MYSQL_USER" -p "$MYSQL_DATABASE" > backup_$(date +%Y%m%d_%H%M%S).sql
```
**MySQL Rollback (Restore):**
```bash
mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p "$MYSQL_DATABASE" < backup_<timestamp>.sql
```
**SQLite Backup & Rollback:**
```bash
sqlite3 data/stock_trading.db ".backup 'data/stock_trading_backup_$(date +%Y%m%d_%H%M%S).db'"
# To roll back (stop server first):
cp data/stock_trading_backup_<timestamp>.db data/stock_trading.db
```

---

## 6. Environment Variables Reference (Node.js vs Java)

`server.ts` loads `.env` via `import 'dotenv/config'` and forwards `process.env` to the spawned Java child process. When Java runs standalone (CLI/JUnit), `util.EnvConfig` reads OS environment variables first, JVM `-D` system properties second, and `.env` third without ever overriding real OS env vars or logging secret values.

| Variable | Owner Process | Default / Dev | Production Requirement |
|---|---|---|---|
| `PORT` | `[Node.js]` | `3000` | Public HTTP listener port (or behind reverse proxy) |
| `NODE_ENV` / `APP_ENV` | `[Both]` | `development` | Set to `production` to enforce strict DB/TLS/demo guards |
| `JAVA_HOST` / `JAVA_PORT` | `[Node.js]` | `127.0.0.1` / `8765` | Keep Java bound to `127.0.0.1` behind gateway |
| `APP_URL` / `ALLOWED_ORIGINS` | `[Both]` | `http://localhost:3000` | Production HTTPS origin allowlist |
| `SESSION_TIMEOUT_SECONDS` | `[Java]` | `1800` | Server-side sliding session expiry |
| `COOKIE_SECURE` / `COOKIE_SAMESITE` | `[Java]` | `false` / `Lax` | Set `COOKIE_SECURE=true` behind HTTPS |
| `DB_TYPE` | `[Both]` | `sqlite` | **Required** in prod (`mysql` or `sqlite`) |
| `SQLITE_DB_PATH` | `[Java]` | `data/stock_trading.db` | Custom path if using embedded SQLite |
| `DB_HOST` / `MYSQL_HOST` | `[Both]` | `127.0.0.1` | Required when `DB_TYPE=mysql` |
| `DB_PORT` / `MYSQL_PORT` | `[Both]` | `3306` | `1–65535` |
| `DB_NAME` / `MYSQL_DATABASE` | `[Both]` | `stock_trading` | Target MySQL schema |
| `DB_USER` / `MYSQL_USER` | `[Both]` | `stock_app_user` | **`root` forbidden in production** |
| `DB_PASSWORD` / `MYSQL_PASSWORD` | `[Both]` | *(none)* | Required strong password (>= 8 chars, non-default) |
| `DB_SSL_MODE` | `[Both]` | `VERIFY_IDENTITY` | `VERIFY_IDENTITY` or `VERIFY_CA` required in production |
| `DB_SSL_CA` | `[Java]` | *(empty)* | Path to CA `.pem`/`.crt` or `.p12`/`.jks` truststore |
| `DB_SSL_TRUSTSTORE_TYPE` / `_PASSWORD` | `[Java]` | *(auto)* | Optional truststore type/password for `.jks`/`.p12` |
| `DB_POOL_MAX` / `DB_POOL_MIN_IDLE` | `[Java]` | `10` / `2` | Bounded JDBC pool capacity & warm idle size |
| `DB_POOL_TIMEOUT_MS` / `DB_POOL_MAX_LIFETIME_MS` | `[Java]` | `5000` / `1800000` | Acquire timeout & max physical connection lifetime |
| `ADMIN_BOOTSTRAP_USER` / `EMAIL` / `PASSWORD` | `[Java]` | *(empty)* | First-boot production admin provisioning when `users` is empty |
| `SEED_DEMO_ACCOUNTS` | `[Java]` | `false` | Local development only; skipped automatically in production |

---

## 7. Security Architecture & Local-Only Demo Accounts

1. **Password Storage (`PasswordHasher.java`)**: Damien Miller jBCrypt (`$2a$12$`). Plaintext fallback is disabled; only records explicitly prefixed with `$LEGACY$` execute a one-time verified migration.
2. **Privilege Escalation Prevention (`RegisterServlet.java`)**: Public `/api/auth/register` always creates `UserRole.TRADER` with ₹50,000.00 default cash balance regardless of client JSON fields.
3. **CSRF & Session Security (`AuthenticationFilter.java` + `BaseServlet.java`)**: HttpOnly/SameSite cookies, session regeneration on login, strict session-bound `X-CSRF-Token` validation, `Origin`/`Referer` check, sliding session expiry, and owner/RBAC route isolation.

### Local Development Only Demo Accounts
The following accounts are seeded **only in local development** (`APP_ENV != production` or explicit dev seeding) and are **skipped automatically in production** (`APP_ENV=production`):

| Role | Username | Password (Local Dev Only) | Initial Balance |
|---|---|---|---|
| **Trader 1** | `trader1` | `trader123` | ₹45,280.00 |
| **Trader 2** | `trader2` | `trader123` | ₹85,600.00 |
| **Admin** | `admin` | `admin123` | N/A |

---

## 8. Automated Testing & Verification Results (183/183 Passed)

All 5 automated Java test suites executed cleanly after compilation via `npm test` (`java -cp "lib/*:out" test.AllTestsRunner`, exit code `0`):

| Test Suite | Passed / Total | Verified Coverage |
|---|---:|---|
| `TradingServiceTest` | **19 / 19** | `BigDecimal` 0.15% fees, `Idempotency-Key` deduplication, concurrent BUY/SELL safety, race conditions, DB rollback on triggers, DAO invalid-price/qty guards |
| `PortfolioCalculationTest` | **7 / 7** | Weighted-average `BigDecimal` cost basis, partial/full sell cost updates, P&L rounding, non-positive/`NaN` price guards, floating-point drift avoidance |
| `SecurityAndAuthTest` | **54 / 54** | BCrypt `$2a$` verification, `$LEGACY$` vs plaintext rejection, CSRF enforcement, `Origin`/`Referer` guards, RBAC, forged `X-User-Id` rejection, privilege escalation block, session expiry |
| `DatabaseAndIntegrationTest` | **58 / 58** | JDBC health & CRUD, production `DB_TYPE`/`root`/weak-password/TLS validation, PEM/PKCS12 truststore URL generation, `EnvConfig` precedence, `BoundedConnectionPool` limits/timeouts/eviction/rollback-on-close/shutdown, idempotent schema migrations + `DOUBLE` -> `DECIMAL(15, 2)` upgrade preserving balances/holdings/transactions, MySQL `ALTER TABLE` DDL generation |
| `EndToEndHttpIntegrationTest` | **45 / 45** | Live `TradingPlatformHttpServer` + `ServletBridge` HTTP round-trips: auth, CSRF, registration escalation block, ACID BUY/SELL trades, `409 Conflict` duplicate idempotency, persistence across server restarts, cross-tenant `403`, logout invalidation (`401`), session timeout |
| **Total** | **183 / 183 (100%)** | **All assertions passed with non-zero exit enforcement in `AllTestsRunner`** |

---

## 9. Setup, Build, CI & Production Deployment Guide

### Local Development
```bash
# 1. Install Node dependencies
npm install

# 2. Optional: create local .env from template
cp .env.example .env

# 3. Compile Java + run Vite/Express dev server (port 3000 -> Java port 8765)
npm run dev
```

### Verification Commands
```bash
# Compile Java backend cleanly
bash compile.sh

# Run all 183 Java unit, security, database, pool, migration & HTTP E2E tests
npm test

# Type-check frontend TypeScript
npm run lint

# Full production build (Java compile -> Vite SPA bundle -> Express gateway bundle)
npm run build
```

### GitHub Actions CI
`.github/workflows/ci.yml` runs on every `push` to `main` and every `pull_request`:
- Installs Temurin JDK 17 and Node.js 22 (`npm ci`)
- Executes `bash compile.sh`, `npm test`, `npm run lint`, and `npm run build`

### Production Deployment Checklist
1. Provision MySQL 8+ using `schema.sql` and a dedicated least-privilege application account (never `root`).
2. Set production environment variables (`APP_ENV=production`, `NODE_ENV=production`, `DB_TYPE=mysql`, `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DB_SSL_MODE=VERIFY_IDENTITY`, `DB_SSL_CA=/path/to/ca.pem`, `COOKIE_SECURE=true`, `ALLOWED_ORIGINS=https://your-domain.com`, `ADMIN_BOOTSTRAP_*` on first boot).
3. Build via `npm ci && npm run build`.
4. Place Express gateway behind HTTPS reverse proxy (Nginx / ALB / Cloud Run) terminating TLS and forwarding `X-Forwarded-Proto: https` and `X-Forwarded-Host`. Keep Java port `8765` bound strictly to `127.0.0.1`.
5. Launch via `npm start` (`node dist/server.cjs`).
