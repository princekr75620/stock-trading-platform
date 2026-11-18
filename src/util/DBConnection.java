package util;

import java.io.File;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Authoritative JDBC Connection Manager & Lightweight Bounded Connection Pool.
 * - Validates mandatory configuration at startup without logging secrets.
 * - Uses SQLite transaction_mode=IMMEDIATE + busy_timeout=5000 for concurrent write safety.
 * - Separates initial schema creation, idempotent data-preserving migrations, and dev seeding.
 * - Supports SQLite (default) and MySQL (explicit DB_TYPE=mysql) with DECIMAL monetary schemas.
 */
public class DBConnection {
    private static final String DATA_DIR = "data";
    private static final String SQLITE_DB_PATH = System.getenv("SQLITE_DB_PATH") != null
            ? System.getenv("SQLITE_DB_PATH")
            : DATA_DIR + File.separator + "stock_trading.db";

    private static final String DB_HOST = firstEnv("MYSQL_HOST", "DB_HOST", "127.0.0.1");
    private static final String DB_PORT = firstEnv("MYSQL_PORT", "DB_PORT", "3306");
    private static final String DB_NAME = firstEnv("MYSQL_DATABASE", "DB_NAME", "stock_trading");
    private static final String DB_USER = firstEnv("MYSQL_USER", "DB_USER", "");
    private static final String DB_PASSWORD = firstEnv("MYSQL_PASSWORD", "DB_PASSWORD", "");

    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_POOL_SIZE = parsePositiveInt(System.getenv("DB_POOL_SIZE"), 10);
    private static final ConcurrentLinkedDeque<Connection> mysqlPool = new ConcurrentLinkedDeque<>();

    private static final boolean useMySQL;
    private static volatile boolean initialized = false;

    static {
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}

        String requestedDb = System.getenv("DB_TYPE");
        useMySQL = "mysql".equalsIgnoreCase(requestedDb != null ? requestedDb.trim() : "");
        try {
            validateConfiguration();
        } catch (IllegalStateException ex) {
            System.err.println("[JDBC] CONFIGURATION ERROR: " + ex.getMessage());
        }
    }

    private static String firstEnv(String primary, String secondary, String fallback) {
        String v1 = System.getenv(primary);
        if (v1 != null && !v1.trim().isEmpty()) return v1.trim();
        String v2 = System.getenv(secondary);
        if (v2 != null && !v2.trim().isEmpty()) return v2.trim();
        return fallback;
    }

    private static int parsePositiveInt(String val, int def) {
        if (val == null || val.trim().isEmpty()) return def;
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean isMySQL() {
        return useMySQL;
    }

    public static boolean isProductionMode() {
        String env = firstEnv("APP_ENV", "NODE_ENV", "development");
        return "production".equalsIgnoreCase(env);
    }

    /**
     * Validates active environment database configuration at startup.
     */
    public static void validateConfiguration() {
        validateConfigurationValues(
                System.getenv("DB_TYPE"),
                DB_HOST,
                DB_PORT,
                DB_NAME,
                DB_USER,
                DB_PASSWORD,
                isProductionMode()
        );
    }

    /**
     * Pure validation helper ensuring invalid engines, ports, or weak/missing MySQL credentials
     * fail fast with clear error messages that never disclose password values.
     */
    public static void validateConfigurationValues(
            String dbType,
            String host,
            String portStr,
            String dbName,
            String user,
            String password,
            boolean productionMode) {
        String normalizedType = (dbType == null || dbType.trim().isEmpty()) ? "sqlite" : dbType.trim().toLowerCase();
        if (!"sqlite".equals(normalizedType) && !"mysql".equals(normalizedType)) {
            throw new IllegalStateException("Unsupported DB_TYPE '" + normalizedType + "'. Allowed values: sqlite, mysql.");
        }

        if ("mysql".equals(normalizedType)) {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("DB_HOST / MYSQL_HOST is required when DB_TYPE=mysql.");
            }
            if (dbName == null || dbName.trim().isEmpty()) {
                throw new IllegalStateException("DB_NAME / MYSQL_DATABASE is required when DB_TYPE=mysql.");
            }
            try {
                int port = Integer.parseInt(portStr != null ? portStr.trim() : "");
                if (port < 1 || port > 65535) {
                    throw new IllegalStateException("DB_PORT / MYSQL_PORT must be between 1 and 65535.");
                }
            } catch (NumberFormatException e) {
                throw new IllegalStateException("DB_PORT / MYSQL_PORT must be a valid TCP port number.");
            }
            if (user == null || user.trim().isEmpty() || password == null || password.isEmpty()) {
                throw new IllegalStateException("DB_TYPE=mysql requires non-empty DB_USER/MYSQL_USER and DB_PASSWORD/MYSQL_PASSWORD.");
            }
            if (productionMode && ("root".equalsIgnoreCase(password.trim())
                    || "replace_with_strong_database_password".equals(password.trim())
                    || password.length() < 8)) {
                throw new IllegalStateException("Production MySQL password does not meet minimum strength requirements (min 8 chars, non-default).");
            }
        }
    }

    /**
     * Obtains a JDBC Connection with configured timeouts, foreign-key enforcement,
     * and immediate write transaction semantics on SQLite.
     */
    public static Connection getConnection() throws SQLException {
        try {
            validateConfiguration();
        } catch (IllegalStateException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }

        if (useMySQL) {
            Connection pooled;
            while ((pooled = mysqlPool.pollFirst()) != null) {
                try {
                    if (!pooled.isClosed() && pooled.isValid(2)) {
                        return wrapPooledConnection(pooled);
                    }
                    pooled.close();
                } catch (SQLException ignored) {
                    try { pooled.close(); } catch (SQLException ignored2) {}
                }
            }

            String mysqlUrl = String.format(
                    "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=10000",
                    DB_HOST, DB_PORT, DB_NAME);
            try {
                Connection raw = DriverManager.getConnection(mysqlUrl, DB_USER, DB_PASSWORD);
                return wrapPooledConnection(raw);
            } catch (SQLException e) {
                System.err.println("[JDBC] CRITICAL: Configured MySQL database connection failed.");
                throw new SQLException("Configured MySQL database connection failed.", e);
            }
        }

        File dbFile = new File(SQLITE_DB_PATH);
        File dir = dbFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        String sqliteUrl = "jdbc:sqlite:" + SQLITE_DB_PATH + "?busy_timeout=5000&transaction_mode=IMMEDIATE";
        Connection conn = DriverManager.getConnection(sqliteUrl);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON;");
            s.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    private static Connection wrapPooledConnection(Connection physical) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        try {
                            if (!physical.isClosed()) {
                                if (!physical.getAutoCommit()) {
                                    physical.rollback();
                                    physical.setAutoCommit(true);
                                }
                                if (mysqlPool.size() < MAX_POOL_SIZE) {
                                    mysqlPool.offerFirst(physical);
                                    return null;
                                }
                            }
                        } catch (SQLException ignored) {}
                        physical.close();
                        return null;
                    }
                    return method.invoke(physical, args);
                }
        );
    }

    /**
     * Initializes configuration, creates initial tables (if missing), executes idempotent
     * data-preserving migrations, and seeds development/catalog defaults.
     */
    public static synchronized void initializeDatabase() {
        if (initialized) return;

        validateConfiguration();
        System.out.println("[JDBC] Initializing Relational Database Schemas...");
        try (Connection conn = getConnection()) {
            initializeSchemaAndMigrate(conn, useMySQL);
            seedInitialData(conn);
            initialized = true;
            System.out.println("[JDBC] Database initialization completed successfully.");
        } catch (SQLException e) {
            System.err.println("[JDBC] Error initializing database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Separates initial schema creation from upgrade migrations so both SQLite and MySQL
     * can safely initialize or upgrade existing tables without dropping user data.
     */
    public static void initializeSchemaAndMigrate(Connection conn, boolean mysqlMode) throws SQLException {
        createInitialSchema(conn, mysqlMode);
        runMigrations(conn, mysqlMode);
    }

    public static void createInitialSchema(Connection conn, boolean mysqlMode) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!mysqlMode) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "  version VARCHAR(50) PRIMARY KEY," +
                    "  description VARCHAR(255) NOT NULL," +
                    "  applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  user_id VARCHAR(50) UNIQUE NOT NULL," +
                    "  name VARCHAR(100) NOT NULL," +
                    "  email VARCHAR(150) UNIQUE NOT NULL," +
                    "  password VARCHAR(255) NOT NULL," +
                    "  role VARCHAR(20) NOT NULL CHECK (role IN ('TRADER', 'ADMIN'))," +
                    "  balance DECIMAL(15, 2) NOT NULL DEFAULT 50000.00 CHECK (balance >= 0)," +
                    "  department VARCHAR(100)," +
                    "  email_alerts_enabled INTEGER NOT NULL DEFAULT 1," +
                    "  alert_threshold_percent DECIMAL(5, 2) NOT NULL DEFAULT 2.00," +
                    "  must_change_password INTEGER NOT NULL DEFAULT 0," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS stocks (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  symbol VARCHAR(20) UNIQUE NOT NULL," +
                    "  company_name VARCHAR(150) NOT NULL," +
                    "  exchange VARCHAR(10) NOT NULL DEFAULT 'NSE'," +
                    "  price DECIMAL(15, 2) NOT NULL CHECK (price > 0)," +
                    "  available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0)," +
                    "  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'HALTED', 'DISABLED'))," +
                    "  previous_price DECIMAL(15, 2) DEFAULT 0.00," +
                    "  day_high DECIMAL(15, 2) DEFAULT 0.00," +
                    "  day_low DECIMAL(15, 2) DEFAULT 0.00," +
                    "  volume_traded BIGINT DEFAULT 0" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS portfolio (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  stock_id INTEGER NOT NULL DEFAULT 0," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  quantity INTEGER NOT NULL CHECK (quantity > 0)," +
                    "  average_price DECIMAL(15, 2) NOT NULL CHECK (average_price >= 0)," +
                    "  UNIQUE(user_id, symbol)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS transactions (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  stock_id INTEGER NOT NULL DEFAULT 0," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('BUY', 'SELL'))," +
                    "  quantity INTEGER NOT NULL CHECK (quantity > 0)," +
                    "  price DECIMAL(15, 2) NOT NULL CHECK (price > 0)," +
                    "  fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00 CHECK (fee_amount >= 0)," +
                    "  total_amount DECIMAL(15, 2) NOT NULL CHECK (total_amount >= 0)," +
                    "  idempotency_key VARCHAR(100)," +
                    "  transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id)" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS trade_idempotency (" +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  idempotency_key VARCHAR(100) NOT NULL," +
                    "  transaction_id INTEGER NOT NULL," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  PRIMARY KEY (user_id, idempotency_key)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE," +
                    "  FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE" +
                    ");"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS watchlist (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  UNIQUE(user_id, symbol)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ");"
                );
            } else {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "  version VARCHAR(50) PRIMARY KEY," +
                    "  description VARCHAR(255) NOT NULL," +
                    "  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "  id INT PRIMARY KEY AUTO_INCREMENT," +
                    "  user_id VARCHAR(50) UNIQUE NOT NULL," +
                    "  name VARCHAR(100) NOT NULL," +
                    "  email VARCHAR(150) UNIQUE NOT NULL," +
                    "  password VARCHAR(255) NOT NULL," +
                    "  role VARCHAR(20) NOT NULL," +
                    "  balance DECIMAL(15, 2) NOT NULL DEFAULT 50000.00," +
                    "  department VARCHAR(100) DEFAULT NULL," +
                    "  email_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE," +
                    "  alert_threshold_percent DECIMAL(5, 2) NOT NULL DEFAULT 2.00," +
                    "  must_change_password BOOLEAN NOT NULL DEFAULT FALSE," +
                    "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  CONSTRAINT chk_users_role CHECK (role IN ('TRADER', 'ADMIN'))," +
                    "  CONSTRAINT chk_users_balance CHECK (balance >= 0.00)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS stocks (" +
                    "  id INT PRIMARY KEY AUTO_INCREMENT," +
                    "  symbol VARCHAR(20) UNIQUE NOT NULL," +
                    "  company_name VARCHAR(150) NOT NULL," +
                    "  exchange VARCHAR(10) NOT NULL DEFAULT 'NSE'," +
                    "  price DECIMAL(15, 2) NOT NULL," +
                    "  available_quantity INT NOT NULL," +
                    "  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                    "  previous_price DECIMAL(15, 2) NOT NULL DEFAULT 0.00," +
                    "  day_high DECIMAL(15, 2) NOT NULL DEFAULT 0.00," +
                    "  day_low DECIMAL(15, 2) NOT NULL DEFAULT 0.00," +
                    "  volume_traded BIGINT NOT NULL DEFAULT 0," +
                    "  CONSTRAINT chk_stocks_price CHECK (price > 0.00)," +
                    "  CONSTRAINT chk_stocks_qty CHECK (available_quantity >= 0)," +
                    "  CONSTRAINT chk_stocks_status CHECK (status IN ('ACTIVE', 'HALTED', 'DISABLED'))" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS portfolio (" +
                    "  id INT PRIMARY KEY AUTO_INCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  stock_id INT NOT NULL DEFAULT 0," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  quantity INT NOT NULL," +
                    "  average_price DECIMAL(15, 2) NOT NULL," +
                    "  CONSTRAINT chk_portfolio_qty CHECK (quantity > 0)," +
                    "  CONSTRAINT chk_portfolio_avg CHECK (average_price >= 0.00)," +
                    "  UNIQUE KEY uq_portfolio_user_symbol (user_id, symbol)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS transactions (" +
                    "  id INT PRIMARY KEY AUTO_INCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  stock_id INT NOT NULL DEFAULT 0," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  transaction_type VARCHAR(10) NOT NULL," +
                    "  quantity INT NOT NULL," +
                    "  price DECIMAL(15, 2) NOT NULL," +
                    "  fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00," +
                    "  total_amount DECIMAL(15, 2) NOT NULL," +
                    "  idempotency_key VARCHAR(100) DEFAULT NULL," +
                    "  transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  CONSTRAINT chk_tx_type CHECK (transaction_type IN ('BUY', 'SELL'))," +
                    "  CONSTRAINT chk_tx_qty CHECK (quantity > 0)," +
                    "  CONSTRAINT chk_tx_price CHECK (price > 0.00)," +
                    "  CONSTRAINT chk_tx_total CHECK (total_amount >= 0.00)," +
                    "  UNIQUE KEY uq_tx_user_idempotency (user_id, idempotency_key)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS trade_idempotency (" +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  idempotency_key VARCHAR(100) NOT NULL," +
                    "  transaction_id INT NOT NULL," +
                    "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  PRIMARY KEY (user_id, idempotency_key)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE," +
                    "  FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS watchlist (" +
                    "  id INT PRIMARY KEY AUTO_INCREMENT," +
                    "  user_id VARCHAR(50) NOT NULL," +
                    "  symbol VARCHAR(20) NOT NULL," +
                    "  added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY uq_user_symbol (user_id, symbol)," +
                    "  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
            }
        }
    }

    /**
     * Executes idempotent, non-destructive upgrade migrations for both SQLite and MySQL
     * using JDBC DatabaseMetaData inspection. Preserves all existing user/portfolio/tx data.
     */
    public static void runMigrations(Connection conn, boolean mysqlMode) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Ensure migration tracking table exists even on pre-existing DBs
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations (" +
                "  version VARCHAR(50) PRIMARY KEY," +
                "  description VARCHAR(255) NOT NULL," +
                "  applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // Migration 001: Users alert & password rotation columns
            if (!hasColumn(conn, "users", "email_alerts_enabled")) {
                stmt.execute(mysqlMode
                        ? "ALTER TABLE users ADD COLUMN email_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE"
                        : "ALTER TABLE users ADD COLUMN email_alerts_enabled INTEGER NOT NULL DEFAULT 1");
            }
            if (!hasColumn(conn, "users", "alert_threshold_percent")) {
                stmt.execute("ALTER TABLE users ADD COLUMN alert_threshold_percent DECIMAL(5, 2) NOT NULL DEFAULT 2.00");
            }
            if (!hasColumn(conn, "users", "must_change_password")) {
                stmt.execute(mysqlMode
                        ? "ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE"
                        : "ALTER TABLE users ADD COLUMN must_change_password INTEGER NOT NULL DEFAULT 0");
            }
            ensureIndex(conn, stmt, mysqlMode, "users", "idx_users_role", false, "role");
            recordMigration(conn, mysqlMode, "001_users_columns_and_indexes", "Add alert preference and password policy columns on users");

            // Migration 002: Transactions fee_amount, idempotency_key & trade_idempotency
            if (!hasColumn(conn, "transactions", "fee_amount")) {
                stmt.execute("ALTER TABLE transactions ADD COLUMN fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00");
            }
            if (!hasColumn(conn, "transactions", "idempotency_key")) {
                stmt.execute("ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(100)");
            }
            ensureIndex(conn, stmt, mysqlMode, "transactions", "uq_tx_user_idempotency", true, "user_id, idempotency_key");
            ensureIndex(conn, stmt, mysqlMode, "transactions", "idx_user_tx", false, "user_id, transaction_date");
            recordMigration(conn, mysqlMode, "002_transactions_fee_and_idempotency", "Add fee_amount, idempotency_key, and ledger indexes");

            // Migration 003: Portfolio unique (user_id, symbol) constraint & consistent indexes across both engines
            if (!hasUniqueIndexOnColumns(conn, "portfolio", Arrays.asList("user_id", "symbol"))) {
                ensureIndex(conn, stmt, mysqlMode, "portfolio", "uq_portfolio_user_symbol", true, "user_id, symbol");
            }
            ensureIndex(conn, stmt, mysqlMode, "portfolio", "idx_portfolio_user", false, "user_id");
            ensureIndex(conn, stmt, mysqlMode, "stocks", "idx_stocks_exchange", false, "exchange");
            ensureIndex(conn, stmt, mysqlMode, "watchlist", "idx_user_watchlist", false, "user_id");
            recordMigration(conn, mysqlMode, "003_portfolio_unique_and_consistent_indexes", "Synchronize unique portfolio(user_id, symbol) and indexes");
        }
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        for (String tbl : new String[]{tableName, tableName.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getColumns(catalog, null, tbl, null)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (columnName.equalsIgnoreCase(col)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasIndex(Connection conn, String tableName, String indexName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        for (String tbl : new String[]{tableName, tableName.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getIndexInfo(catalog, null, tbl, false, false)) {
                while (rs.next()) {
                    String idx = rs.getString("INDEX_NAME");
                    if (indexName.equalsIgnoreCase(idx)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasUniqueIndexOnColumns(Connection conn, String tableName, List<String> expectedCols) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        Map<String, List<String>> uniqueIndexes = new HashMap<>();
        for (String tbl : new String[]{tableName, tableName.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = meta.getIndexInfo(catalog, null, tbl, true, false)) {
                while (rs.next()) {
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                    String idxName = rs.getString("INDEX_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    if (!nonUnique && idxName != null && colName != null) {
                        uniqueIndexes.computeIfAbsent(idxName.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                                .add(colName.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        List<String> target = new ArrayList<>();
        for (String c : expectedCols) target.add(c.toLowerCase(Locale.ROOT));
        for (List<String> cols : uniqueIndexes.values()) {
            if (cols.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureIndex(Connection conn, Statement stmt, boolean mysqlMode,
                                    String table, String indexName, boolean unique, String columns) throws SQLException {
        if (hasIndex(conn, table, indexName)) {
            return;
        }
        if (!mysqlMode) {
            String sql = "CREATE " + (unique ? "UNIQUE " : "") + "INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")";
            stmt.execute(sql);
        } else {
            String sql = "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + indexName + " ON " + table + "(" + columns + ")";
            stmt.execute(sql);
        }
    }

    private static void recordMigration(Connection conn, boolean mysqlMode, String version, String description) throws SQLException {
        String sql = mysqlMode
                ? "INSERT IGNORE INTO schema_migrations (version, description) VALUES (?, ?)"
                : "INSERT OR IGNORE INTO schema_migrations (version, description) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }

    private static void seedInitialData(Connection conn) throws SQLException {
        boolean isProd = isProductionMode();
        boolean seedDemoExplicit = "true".equalsIgnoreCase(System.getenv("SEED_DEMO_ACCOUNTS"));

        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                if (isProd && !seedDemoExplicit) {
                    String adminUser = System.getenv("ADMIN_BOOTSTRAP_USER");
                    String adminEmail = System.getenv("ADMIN_BOOTSTRAP_EMAIL");
                    String adminPass = System.getenv("ADMIN_BOOTSTRAP_PASSWORD");
                    if (adminUser != null && !adminUser.trim().isEmpty()
                            && adminEmail != null && !adminEmail.trim().isEmpty()
                            && adminPass != null && adminPass.length() >= 8) {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO users (user_id, name, email, password, role, balance, department, must_change_password) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                            ins.setString(1, adminUser.trim());
                            ins.setString(2, "Platform Administrator");
                            ins.setString(3, adminEmail.trim());
                            ins.setString(4, PasswordHasher.hashPassword(adminPass));
                            ins.setString(5, "ADMIN");
                            ins.setDouble(6, 0.0);
                            ins.setString(7, "Enterprise Market Operations");
                            ins.setInt(8, 0);
                            ins.executeUpdate();
                        }
                        System.out.println("[JDBC] Provisioned production bootstrap admin account from environment variables.");
                    } else {
                        System.out.println("[JDBC] Production mode detected with empty users table: skipped default demo credentials.");
                    }
                } else {
                    System.out.println("[JDBC] Seeding development/demo users into database with BCrypt hashes...");
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO users (user_id, name, email, password, role, balance, department, must_change_password) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ins.setString(1, "admin");
                        ins.setString(2, "Chief Risk Officer");
                        ins.setString(3, "admin@tradingplatform.internal");
                        ins.setString(4, PasswordHasher.hashPassword("admin123"));
                        ins.setString(5, "ADMIN");
                        ins.setDouble(6, 0.0);
                        ins.setString(7, "Enterprise Market Operations");
                        ins.setInt(8, 1);
                        ins.executeUpdate();

                        ins.setString(1, "trader1");
                        ins.setString(2, "Alexander Morgan");
                        ins.setString(3, "alex.morgan@quantcap.com");
                        ins.setString(4, PasswordHasher.hashPassword("trader123"));
                        ins.setString(5, "TRADER");
                        ins.setDouble(6, 45280.00);
                        ins.setString(7, null);
                        ins.setInt(8, 1);
                        ins.executeUpdate();

                        ins.setString(1, "trader2");
                        ins.setString(2, "Elena Rostova");
                        ins.setString(3, "elena.r@hedgealpha.io");
                        ins.setString(4, PasswordHasher.hashPassword("trader123"));
                        ins.setString(5, "TRADER");
                        ins.setDouble(6, 85600.00);
                        ins.setString(7, null);
                        ins.setInt(8, 1);
                        ins.executeUpdate();
                    }
                }
            }
        }

        // Seed Indian stocks catalog (non-sensitive reference market data)
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM stocks");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                File csvMaster = new File("data" + File.separator + "indian_stocks_master.csv");
                boolean importedFromCSV = false;
                if (csvMaster.exists()) {
                    try {
                        service.StockImportService importer = new service.StockImportService(new dao.StockDAO());
                        int count = importer.importStocksFromCSV(csvMaster.getAbsolutePath());
                        importedFromCSV = count > 0;
                    } catch (Exception ignored) {}
                }

                if (!importedFromCSV) {
                    String[][] sampleIndianStocks = {
                        {"RELIANCE", "Reliance Industries Limited", "NSE", "1324.40", "50000", "ACTIVE", "1324.00", "1330.20", "1305.50", "19730000"},
                        {"TCS", "Tata Consultancy Services Limited", "NSE", "2475.40", "35000", "ACTIVE", "2498.80", "2499.90", "2449.60", "2894000"},
                        {"HDFCBANK", "HDFC Bank Limited", "NSE", "798.15", "60000", "ACTIVE", "784.10", "801.90", "785.95", "64825000"},
                        {"INFY", "Infosys Limited", "NSE", "1154.10", "45000", "ACTIVE", "1171.60", "1168.00", "1139.00", "10098000"},
                        {"ICICIBANK", "ICICI Bank Limited", "NSE", "1589.00", "55000", "ACTIVE", "1565.00", "1592.00", "1572.20", "27470000"},
                        {"TATAMOTORS", "Tata Motors Limited", "NSE", "436.65", "40000", "ACTIVE", "428.75", "438.35", "425.50", "7829000"},
                        {"SBIN", "State Bank of India", "NSE", "1042.00", "75000", "ACTIVE", "1016.00", "1048.60", "1022.30", "24062000"},
                        {"BHARTIARTL", "Bharti Airtel Limited", "NSE", "2072.10", "30000", "ACTIVE", "2085.50", "2089.00", "2040.40", "6063000"},
                        {"ITC", "ITC Limited", "NSE", "278.80", "80000", "ACTIVE", "283.45", "283.45", "277.90", "18568000"},
                        {"KOTAKBANK", "Kotak Mahindra Bank Limited", "NSE", "479.50", "25000", "ACTIVE", "480.50", "483.20", "474.65", "19611000"},
                        {"LT", "Larsen & Toubro Limited", "NSE", "4185.00", "20000", "ACTIVE", "4110.00", "4194.10", "4097.40", "3250000"},
                        {"HINDUNILVR", "Hindustan Unilever Limited", "NSE", "1990.30", "30000", "ACTIVE", "1997.00", "2006.50", "1980.00", "1456000"},
                        {"BAJFINANCE", "Bajaj Finance Limited", "NSE", "1064.85", "15000", "ACTIVE", "1081.75", "1088.00", "1061.85", "9198000"},
                        {"MARUTI", "Maruti Suzuki India Limited", "NSE", "12928.00", "8000", "ACTIVE", "12815.00", "12929.00", "12657.00", "557000"},
                        {"SUNPHARMA", "Sun Pharmaceutical Industries Ltd.", "NSE", "1904.50", "22000", "ACTIVE", "1915.00", "1943.90", "1900.20", "3125000"},
                        {"WIPRO", "Wipro Limited", "NSE", "199.00", "40000", "ACTIVE", "197.55", "199.00", "194.70", "11757000"},
                        {"TATASTEEL", "Tata Steel Limited", "NSE", "184.85", "100000", "ACTIVE", "190.20", "194.00", "184.34", "49881000"},
                        {"VEDL", "Vedanta Limited", "BSE", "655.40", "50000", "ACTIVE", "648.00", "659.00", "645.00", "65000"}
                    };

                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        for (String[] s : sampleIndianStocks) {
                            ins.setString(1, s[0]);
                            ins.setString(2, s[1]);
                            ins.setString(3, s[2]);
                            ins.setDouble(4, Double.parseDouble(s[3]));
                            ins.setInt(5, Integer.parseInt(s[4]));
                            ins.setString(6, s[5]);
                            ins.setDouble(7, Double.parseDouble(s[6]));
                            ins.setDouble(8, Double.parseDouble(s[7]));
                            ins.setDouble(9, Double.parseDouble(s[8]));
                            ins.setLong(10, Long.parseLong(s[9]));
                            ins.executeUpdate();
                        }
                    }
                }
            }
        }

        if (!isProd || seedDemoExplicit) {
            try (PreparedStatement chk = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE user_id = 'trader1'");
                 ResultSet urs = chk.executeQuery()) {
                boolean hasTrader1 = urs.next() && urs.getInt(1) > 0;
                if (hasTrader1) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM portfolio");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (PreparedStatement ins = conn.prepareStatement(
                                    "INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES (?, ?, ?, ?, ?)")) {
                                ins.setString(1, "trader1"); ins.setInt(2, 1); ins.setString(3, "RELIANCE"); ins.setInt(4, 20); ins.setDouble(5, 2900.00); ins.executeUpdate();
                                ins.setString(1, "trader1"); ins.setInt(2, 2); ins.setString(3, "TCS"); ins.setInt(4, 10); ins.setDouble(5, 4150.00); ins.executeUpdate();
                                ins.setString(1, "trader1"); ins.setInt(2, 4); ins.setString(3, "INFY"); ins.setInt(4, 25); ins.setDouble(5, 1850.00); ins.executeUpdate();
                            }
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM watchlist");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO watchlist (user_id, symbol) VALUES (?, ?)")) {
                                String[][] defaultWatchlists = {
                                    {"trader1", "RELIANCE"}, {"trader1", "TCS"}, {"trader1", "INFY"},
                                    {"trader2", "TATAMOTORS"}, {"trader2", "SBIN"}
                                };
                                for (String[] entry : defaultWatchlists) {
                                    ins.setString(1, entry[0]);
                                    ins.setString(2, entry[1]);
                                    ins.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Diagnostic Health Status Inspector without exposing filesystem paths, credentials, or stack traces.
     */
    public static Map<String, Object> getHealthStatus() {
        Map<String, Object> map = new HashMap<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            map.put("connected", true);
            map.put("databaseProduct", meta.getDatabaseProductName());
            map.put("databaseVersion", meta.getDatabaseProductVersion());
            map.put("driverName", meta.getDriverName());
            map.put("driverVersion", meta.getDriverVersion());
            map.put("engineType", useMySQL ? "MySQL (Relational Production)" : "SQLite (Embedded Relational ACID)");
            map.put("jdbcUrl", useMySQL ? "jdbc:mysql://[configured-host]/[configured-db]" : "jdbc:sqlite:[embedded-storage]");

            String[] tables = {"users", "stocks", "portfolio", "transactions", "watchlist"};
            Map<String, Integer> counts = new HashMap<>();
            for (String tbl : tables) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tbl);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        counts.put(tbl, rs.getInt(1));
                    }
                } catch (Exception ignored) {
                    counts.put(tbl, 0);
                }
            }
            map.put("tableCounts", counts);
            map.put("verifiedTables", tables.length);
        } catch (Exception e) {
            System.err.println("[DBConnection] Health check error: " + e.getMessage());
            map.put("connected", false);
            map.put("error", "Database connection unavailable.");
        }
        return map;
    }
}
