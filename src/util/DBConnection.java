package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.sql.*;
import java.util.*;

/**
 * Authoritative JDBC Connection Manager & Strictly Bounded Connection Pool.
 * - Loads configuration deterministically via EnvConfig without printing secrets.
 * - Enforces TLS certificate verification (sslMode=VERIFY_IDENTITY/VERIFY_CA) on MySQL
 *   and forbids root/default credentials or useSSL=false in production.
 * - Uses BoundedConnectionPool for genuine maxPoolSize, minIdle, timeout, maxLifetime, and shutdown control.
 * - Uses SQLite transaction_mode=IMMEDIATE + busy_timeout=5000 + foreign_keys=ON for dev/embedded ACID safety.
 * - Separates initial schema creation, idempotent data-preserving migrations (including DOUBLE -> DECIMAL), and seeding.
 */
public class DBConnection {
    private static final String DATA_DIR = "data";
    private static final String SQLITE_DB_PATH = EnvConfig.get("SQLITE_DB_PATH", DATA_DIR + File.separator + "stock_trading.db");

    private static final String DB_HOST = EnvConfig.first("MYSQL_HOST", "DB_HOST", "127.0.0.1");
    private static final String DB_PORT = EnvConfig.first("MYSQL_PORT", "DB_PORT", "3306");
    private static final String DB_NAME = EnvConfig.first("MYSQL_DATABASE", "DB_NAME", "stock_trading");
    private static final String DB_USER = EnvConfig.first("MYSQL_USER", "DB_USER", "");
    private static final String DB_PASSWORD = EnvConfig.first("MYSQL_PASSWORD", "DB_PASSWORD", "");
    private static final String DB_SSL_MODE = EnvConfig.first("DB_SSL_MODE", "MYSQL_SSL_MODE", "");
    private static final String DB_SSL_CA = EnvConfig.first("DB_SSL_CA", "MYSQL_SSL_CA", "");
    private static final String DB_SSL_TRUSTSTORE_TYPE = EnvConfig.first("DB_SSL_TRUSTSTORE_TYPE", "MYSQL_SSL_TRUSTSTORE_TYPE", "");
    private static final String DB_SSL_TRUSTSTORE_PASSWORD = EnvConfig.first("DB_SSL_TRUSTSTORE_PASSWORD", "MYSQL_SSL_TRUSTSTORE_PASSWORD", "");

    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_POOL_SIZE = EnvConfig.getPositiveInt("DB_POOL_MAX", "DB_POOL_SIZE", 10);
    private static final int DEFAULT_MIN_IDLE = Math.min(MAX_POOL_SIZE, EnvConfig.getPositiveInt("DB_POOL_MIN_IDLE", "MYSQL_POOL_MIN_IDLE", 2));
    private static final long POOL_TIMEOUT_MS = EnvConfig.getPositiveLong("DB_POOL_TIMEOUT_MS", "DB_CONNECTION_TIMEOUT_MS", 5000L);
    private static final long POOL_MAX_LIFETIME_MS = EnvConfig.getPositiveLong("DB_POOL_MAX_LIFETIME_MS", "MYSQL_POOL_MAX_LIFETIME_MS", 1_800_000L);

    private static final boolean useMySQL;
    private static volatile BoundedConnectionPool activePool = null;
    private static volatile boolean initialized = false;
    private static volatile String cachedPemTruststorePath = null;
    private static volatile String cachedPemTruststorePassword = null;

    static {
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Optional driver check
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Optional driver check
        }

        String requestedDb = EnvConfig.get("DB_TYPE");
        useMySQL = "mysql".equalsIgnoreCase(requestedDb != null ? requestedDb.trim() : "");
        try {
            validateConfiguration();
        } catch (IllegalStateException ex) {
            System.err.println("[JDBC] CONFIGURATION ERROR: " + ex.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(DBConnection::shutdownPool, "db-pool-shutdown"));
    }

    public static boolean isMySQL() {
        return useMySQL;
    }

    public static boolean isProductionMode() {
        return EnvConfig.isProductionMode();
    }

    /**
     * Validates active environment database configuration at startup.
     */
    public static void validateConfiguration() {
        validateConfigurationValues(
                EnvConfig.get("DB_TYPE"),
                DB_HOST,
                DB_PORT,
                DB_NAME,
                DB_USER,
                DB_PASSWORD,
                DB_SSL_MODE,
                DB_SSL_CA,
                isProductionMode()
        );
    }

    /**
     * Backward-compatible overload for validation tests.
     */
    public static void validateConfigurationValues(
            String dbType,
            String host,
            String portStr,
            String dbName,
            String user,
            String password,
            boolean productionMode) {
        String defaultSslMode = productionMode ? "VERIFY_IDENTITY" : "REQUIRED";
        validateConfigurationValues(dbType, host, portStr, dbName, user, password, defaultSslMode, null, productionMode);
    }

    /**
     * Validates database engine, host/port, credentials, least-privilege user, and TLS mode.
     * Never exposes secret credential values in thrown exception messages or logs.
     */
    public static void validateConfigurationValues(
            String dbType,
            String host,
            String portStr,
            String dbName,
            String user,
            String password,
            String sslMode,
            String sslCaPath,
            boolean productionMode) {
        String rawType = (dbType == null || dbType.trim().isEmpty()) ? null : dbType.trim().toLowerCase(Locale.ROOT);
        if (productionMode && rawType == null) {
            throw new IllegalStateException("Production startup requires DB_TYPE to be explicitly configured ('mysql' or 'sqlite').");
        }
        String normalizedType = rawType != null ? rawType : "sqlite";
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

            String trimmedUser = user.trim();
            String trimmedPass = password.trim();
            if (productionMode && "root".equalsIgnoreCase(trimmedUser)) {
                throw new IllegalStateException("Production MySQL connections must use a dedicated least-privilege database user; 'root' is forbidden.");
            }
            if (productionMode && (
                    "root".equalsIgnoreCase(trimmedPass)
                    || "replace_with_strong_database_password".equalsIgnoreCase(trimmedPass)
                    || "changeme".equalsIgnoreCase(trimmedPass)
                    || "password".equalsIgnoreCase(trimmedPass)
                    || "admin123".equalsIgnoreCase(trimmedPass)
                    || password.length() < 8)) {
                throw new IllegalStateException("Production MySQL password does not meet minimum strength requirements (min 8 chars, non-default).");
            }

            String resolvedSsl = resolveSslMode(sslMode, sslCaPath, productionMode);
            if ("DISABLED".equals(resolvedSsl) || "NONE".equals(resolvedSsl)) {
                throw new IllegalStateException("Insecure MySQL TLS mode '" + resolvedSsl + "' is not permitted. Configure TLS verification.");
            }
            if (productionMode && !"VERIFY_CA".equals(resolvedSsl) && !"VERIFY_IDENTITY".equals(resolvedSsl)) {
                throw new IllegalStateException("Production MySQL requires certificate-verified TLS (DB_SSL_MODE=VERIFY_IDENTITY or VERIFY_CA).");
            }
            if (sslCaPath != null && !sslCaPath.trim().isEmpty()) {
                File caFile = new File(sslCaPath.trim());
                if (!caFile.isFile() || !caFile.canRead()) {
                    throw new IllegalStateException("Configured DB_SSL_CA certificate/truststore file does not exist or is unreadable.");
                }
            }
        }
    }

    public static String resolveSslMode(String configuredSslMode, String sslCaPath, boolean productionMode) {
        String raw = (configuredSslMode == null || configuredSslMode.trim().isEmpty())
                ? null
                : configuredSslMode.trim().toUpperCase(Locale.ROOT);
        if (raw == null) {
            if (productionMode) return "VERIFY_IDENTITY";
            return (sslCaPath != null && !sslCaPath.trim().isEmpty()) ? "VERIFY_CA" : "REQUIRED";
        }
        return raw;
    }

    /**
     * Builds a TLS-enforced MySQL JDBC connection URL (never emits useSSL=false or allowPublicKeyRetrieval=true).
     */
    public static String buildMySqlJdbcUrl(
            String host,
            String port,
            String dbName,
            String configuredSslMode,
            String sslCaPath,
            String trustStoreType,
            boolean productionMode) {
        String sslMode = resolveSslMode(configuredSslMode, sslCaPath, productionMode);
        if ("DISABLED".equals(sslMode) || "NONE".equals(sslMode)) {
            throw new IllegalStateException("Insecure MySQL TLS mode '" + sslMode + "' is forbidden.");
        }
        if (productionMode && !"VERIFY_CA".equals(sslMode) && !"VERIFY_IDENTITY".equals(sslMode)) {
            throw new IllegalStateException("Production MySQL requires VERIFY_CA or VERIFY_IDENTITY TLS mode.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("jdbc:mysql://").append(host.trim()).append(":").append(port.trim()).append("/").append(dbName.trim());
        sb.append("?sslMode=").append(sslMode);
        sb.append("&tlsVersions=TLSv1.2,TLSv1.3");
        sb.append("&serverTimezone=UTC&connectTimeout=5000&socketTimeout=10000");

        if (sslCaPath != null && !sslCaPath.trim().isEmpty()) {
            String trimmedCa = sslCaPath.trim();
            String lower = trimmedCa.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pem") || lower.endsWith(".crt") || lower.endsWith(".cer")) {
                String[] storeInfo = ensurePemTruststore(trimmedCa);
                sb.append("&trustCertificateKeyStoreUrl=file:").append(storeInfo[0]);
                sb.append("&trustCertificateKeyStoreType=PKCS12");
                sb.append("&trustCertificateKeyStorePassword=").append(storeInfo[1]);
            } else {
                String resolvedType = (trustStoreType != null && !trustStoreType.trim().isEmpty())
                        ? trustStoreType.trim().toUpperCase(Locale.ROOT)
                        : (lower.endsWith(".p12") || lower.endsWith(".pfx") ? "PKCS12" : "JKS");
                sb.append("&trustCertificateKeyStoreUrl=file:").append(new File(trimmedCa).getAbsolutePath());
                sb.append("&trustCertificateKeyStoreType=").append(resolvedType);
                if (DB_SSL_TRUSTSTORE_PASSWORD != null && !DB_SSL_TRUSTSTORE_PASSWORD.isEmpty()) {
                    sb.append("&trustCertificateKeyStorePassword=").append(DB_SSL_TRUSTSTORE_PASSWORD);
                }
            }
        }
        return sb.toString();
    }

    private static synchronized String[] ensurePemTruststore(String pemPath) {
        if (cachedPemTruststorePath != null && cachedPemTruststorePassword != null
                && new File(cachedPemTruststorePath).isFile()) {
            return new String[]{cachedPemTruststorePath, cachedPemTruststorePassword};
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            try (FileInputStream fis = new FileInputStream(pemPath)) {
                int idx = 0;
                for (Certificate cert : cf.generateCertificates(fis)) {
                    ks.setCertificateEntry("mysql-ca-" + (++idx), cert);
                }
                if (idx == 0) {
                    throw new IllegalStateException("DB_SSL_CA PEM file contains no valid X.509 certificates.");
                }
            }
            byte[] randBytes = new byte[18];
            new SecureRandom().nextBytes(randBytes);
            String tempPass = Base64.getUrlEncoder().withoutPadding().encodeToString(randBytes);
            File tempStore = File.createTempFile("mysql_ca_trust_", ".p12");
            tempStore.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempStore)) {
                ks.store(fos, tempPass.toCharArray());
            }
            cachedPemTruststorePath = tempStore.getAbsolutePath();
            cachedPemTruststorePassword = tempPass;
            return new String[]{cachedPemTruststorePath, cachedPemTruststorePassword};
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load DB_SSL_CA certificate file: " + e.getMessage(), e);
        }
    }

    private static BoundedConnectionPool getOrCreatePool() throws SQLException {
        BoundedConnectionPool pool = activePool;
        if (pool != null && !pool.getStats().closed) {
            return pool;
        }
        synchronized (DBConnection.class) {
            if (activePool != null && !activePool.getStats().closed) {
                return activePool;
            }
            int minIdle = useMySQL ? DEFAULT_MIN_IDLE : Math.min(1, MAX_POOL_SIZE);
            BoundedConnectionPool.ConnectionFactory factory = useMySQL
                    ? DBConnection::createPhysicalMySqlConnection
                    : DBConnection::createPhysicalSqliteConnection;
            activePool = new BoundedConnectionPool(factory, MAX_POOL_SIZE, minIdle, POOL_TIMEOUT_MS, POOL_MAX_LIFETIME_MS);
            return activePool;
        }
    }

    private static Connection createPhysicalMySqlConnection() throws SQLException {
        String mysqlUrl;
        try {
            mysqlUrl = buildMySqlJdbcUrl(
                    DB_HOST, DB_PORT, DB_NAME,
                    DB_SSL_MODE, DB_SSL_CA, DB_SSL_TRUSTSTORE_TYPE,
                    isProductionMode()
            );
        } catch (IllegalStateException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
        try {
            return DriverManager.getConnection(mysqlUrl, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            System.err.println("[JDBC] CRITICAL: Configured MySQL TLS database connection failed.");
            throw new SQLException("Configured MySQL database connection failed.", e);
        }
    }

    private static Connection createPhysicalSqliteConnection() throws SQLException {
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

    /**
     * Obtains a bounded, validated pooled JDBC Connection with configured timeouts,
     * TLS verification on MySQL, foreign-key enforcement, and IMMEDIATE write locking on SQLite.
     */
    public static Connection getConnection() throws SQLException {
        try {
            validateConfiguration();
        } catch (IllegalStateException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
        return getOrCreatePool().getConnection();
    }

    public static BoundedConnectionPool.PoolStats getPoolStats() {
        BoundedConnectionPool pool = activePool;
        return pool != null ? pool.getStats() : null;
    }

    public static synchronized void shutdownPool() {
        if (activePool != null) {
            activePool.shutdown();
            activePool = null;
        }
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

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS login_rate_limits (" +
                    "  rate_key VARCHAR(200) PRIMARY KEY," +
                    "  scope VARCHAR(20) NOT NULL," +
                    "  attempt_count INTEGER NOT NULL DEFAULT 0," +
                    "  last_attempt_at BIGINT NOT NULL," +
                    "  throttle_until BIGINT NOT NULL DEFAULT 0" +
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

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS login_rate_limits (" +
                    "  rate_key VARCHAR(200) PRIMARY KEY," +
                    "  scope VARCHAR(20) NOT NULL," +
                    "  attempt_count INT NOT NULL DEFAULT 0," +
                    "  last_attempt_at BIGINT NOT NULL," +
                    "  throttle_until BIGINT NOT NULL DEFAULT 0," +
                    "  INDEX idx_rate_throttle_until (throttle_until)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
            }
        }
    }

    /**
     * Executes idempotent, non-destructive upgrade migrations for both SQLite and MySQL
     * using JDBC DatabaseMetaData inspection. Preserves all existing user/portfolio/tx data
     * and converts legacy DOUBLE/FLOAT/REAL monetary columns safely to DECIMAL(15, 2).
     */
    public static void runMigrations(Connection conn, boolean mysqlMode) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
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

            // Migration 004: Convert any legacy DOUBLE/FLOAT/REAL monetary columns safely to DECIMAL(15, 2)
            if (mysqlMode) {
                migrateMySqlMonetaryColumnsToDecimal(conn);
            } else {
                migrateSqliteLegacyDoubleMonetaryColumns(conn);
            }
            recordMigration(conn, mysqlMode, "004_monetary_decimal_precision", "Convert legacy DOUBLE/REAL monetary columns safely to DECIMAL(15, 2)");

            // Migration 005: Persistent authentication rate-limiting storage
            stmt.execute(mysqlMode
                    ? "CREATE TABLE IF NOT EXISTS login_rate_limits (" +
                      "  rate_key VARCHAR(200) PRIMARY KEY," +
                      "  scope VARCHAR(20) NOT NULL," +
                      "  attempt_count INT NOT NULL DEFAULT 0," +
                      "  last_attempt_at BIGINT NOT NULL," +
                      "  throttle_until BIGINT NOT NULL DEFAULT 0," +
                      "  INDEX idx_rate_throttle_until (throttle_until)" +
                      ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                    : "CREATE TABLE IF NOT EXISTS login_rate_limits (" +
                      "  rate_key VARCHAR(200) PRIMARY KEY," +
                      "  scope VARCHAR(20) NOT NULL," +
                      "  attempt_count INTEGER NOT NULL DEFAULT 0," +
                      "  last_attempt_at BIGINT NOT NULL," +
                      "  throttle_until BIGINT NOT NULL DEFAULT 0" +
                      ")");
            ensureIndex(conn, stmt, mysqlMode, "login_rate_limits", "idx_rate_throttle_until", false, "throttle_until");
            recordMigration(conn, mysqlMode, "005_login_rate_limits", "Add persistent authentication rate-limit table across restarts/instances");
        }
    }

    /**
     * Generates deterministic MySQL ALTER TABLE MODIFY COLUMN DDL to convert legacy floating-point
     * monetary columns to exact DECIMAL(15, 2) without dropping tables or losing data.
     */
    public static String generateMySqlMonetaryAlterSql(String table, String column, boolean notNull, String defaultExpr) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(table).append(" MODIFY COLUMN ").append(column).append(" DECIMAL(15, 2)");
        if (notNull) sb.append(" NOT NULL");
        if (defaultExpr != null && !defaultExpr.isEmpty()) sb.append(" DEFAULT ").append(defaultExpr);
        return sb.toString();
    }

    private static void migrateMySqlMonetaryColumnsToDecimal(Connection conn) throws SQLException {
        String[][] targets = {
            {"users", "balance", "true", "50000.00"},
            {"stocks", "price", "true", ""},
            {"stocks", "previous_price", "true", "0.00"},
            {"stocks", "day_high", "true", "0.00"},
            {"stocks", "day_low", "true", "0.00"},
            {"portfolio", "average_price", "true", ""},
            {"transactions", "price", "true", ""},
            {"transactions", "fee_amount", "true", "0.00"},
            {"transactions", "total_amount", "true", ""}
        };
        try (Statement stmt = conn.createStatement()) {
            for (String[] t : targets) {
                String table = t[0];
                String col = t[1];
                boolean notNull = Boolean.parseBoolean(t[2]);
                String def = t[3];
                if (needsMySqlDecimalUpgrade(conn, table, col)) {
                    stmt.execute(generateMySqlMonetaryAlterSql(table, col, notNull, def));
                }
            }
        }
    }

    private static boolean needsMySqlDecimalUpgrade(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dataType = rs.getString("DATA_TYPE");
                    int prec = rs.getInt("NUMERIC_PRECISION");
                    int scale = rs.getInt("NUMERIC_SCALE");
                    return !"decimal".equalsIgnoreCase(dataType) || prec < 15 || scale != 2;
                }
            }
        }
        return false;
    }

    private static boolean sqliteTableHasLegacyFloatingMonetaryColumn(Connection conn, String table, String... columns) throws SQLException {
        Set<String> targetCols = new HashSet<>(Arrays.asList(columns));
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                if (name != null && targetCols.contains(name.toLowerCase(Locale.ROOT))) {
                    String upper = type != null ? type.toUpperCase(Locale.ROOT) : "";
                    if (upper.contains("DOUBLE") || upper.contains("FLOAT") || upper.contains("REAL")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void migrateSqliteLegacyDoubleMonetaryColumns(Connection conn) throws SQLException {
        boolean upgradeUsers = sqliteTableHasLegacyFloatingMonetaryColumn(conn, "users", "balance", "alert_threshold_percent");
        boolean upgradeStocks = sqliteTableHasLegacyFloatingMonetaryColumn(conn, "stocks", "price", "previous_price", "day_high", "day_low");
        boolean upgradePortfolio = sqliteTableHasLegacyFloatingMonetaryColumn(conn, "portfolio", "average_price");
        boolean upgradeTransactions = sqliteTableHasLegacyFloatingMonetaryColumn(conn, "transactions", "price", "fee_amount", "total_amount");

        if (!upgradeUsers && !upgradeStocks && !upgradePortfolio && !upgradeTransactions) {
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF;");
            conn.setAutoCommit(false);

            if (upgradeUsers) {
                long beforeCount = queryLongScalar(conn, "SELECT COUNT(*) FROM users");
                double beforeSum = queryDoubleScalar(conn, "SELECT COALESCE(SUM(ROUND(balance, 2)), 0.0) FROM users");
                st.execute("DROP TABLE IF EXISTS users_mig_new;");
                st.execute(
                    "CREATE TABLE users_mig_new (" +
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
                st.execute(
                    "INSERT INTO users_mig_new (id, user_id, name, email, password, role, balance, department, email_alerts_enabled, alert_threshold_percent, must_change_password, created_at) " +
                    "SELECT id, user_id, name, email, password, role, ROUND(balance, 2), department, COALESCE(email_alerts_enabled, 1), ROUND(COALESCE(alert_threshold_percent, 2.00), 2), COALESCE(must_change_password, 0), created_at FROM users;"
                );
                long afterCount = queryLongScalar(conn, "SELECT COUNT(*) FROM users_mig_new");
                double afterSum = queryDoubleScalar(conn, "SELECT COALESCE(SUM(balance), 0.0) FROM users_mig_new");
                if (beforeCount != afterCount || Math.abs(beforeSum - afterSum) > 0.01) {
                    throw new SQLException("Migration parity check failed on users table.");
                }
                st.execute("DROP TABLE users;");
                st.execute("ALTER TABLE users_mig_new RENAME TO users;");
                st.execute("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);");
            }

            if (upgradeStocks) {
                long beforeCount = queryLongScalar(conn, "SELECT COUNT(*) FROM stocks");
                st.execute("DROP TABLE IF EXISTS stocks_mig_new;");
                st.execute(
                    "CREATE TABLE stocks_mig_new (" +
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
                st.execute(
                    "INSERT INTO stocks_mig_new (id, symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) " +
                    "SELECT id, symbol, company_name, exchange, ROUND(price, 2), available_quantity, status, ROUND(COALESCE(previous_price, 0.00), 2), ROUND(COALESCE(day_high, 0.00), 2), ROUND(COALESCE(day_low, 0.00), 2), COALESCE(volume_traded, 0) FROM stocks;"
                );
                long afterCount = queryLongScalar(conn, "SELECT COUNT(*) FROM stocks_mig_new");
                if (beforeCount != afterCount) {
                    throw new SQLException("Migration parity check failed on stocks table.");
                }
                st.execute("DROP TABLE stocks;");
                st.execute("ALTER TABLE stocks_mig_new RENAME TO stocks;");
                st.execute("CREATE INDEX IF NOT EXISTS idx_stocks_exchange ON stocks(exchange);");
            }

            if (upgradePortfolio) {
                long beforeCount = queryLongScalar(conn, "SELECT COUNT(*) FROM portfolio");
                st.execute("DROP TABLE IF EXISTS portfolio_mig_new;");
                st.execute(
                    "CREATE TABLE portfolio_mig_new (" +
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
                st.execute(
                    "INSERT INTO portfolio_mig_new (id, user_id, stock_id, symbol, quantity, average_price) " +
                    "SELECT id, user_id, stock_id, symbol, quantity, ROUND(average_price, 2) FROM portfolio;"
                );
                long afterCount = queryLongScalar(conn, "SELECT COUNT(*) FROM portfolio_mig_new");
                if (beforeCount != afterCount) {
                    throw new SQLException("Migration parity check failed on portfolio table.");
                }
                st.execute("DROP TABLE portfolio;");
                st.execute("ALTER TABLE portfolio_mig_new RENAME TO portfolio;");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_portfolio_user_symbol ON portfolio(user_id, symbol);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_portfolio_user ON portfolio(user_id);");
            }

            if (upgradeTransactions) {
                long beforeCount = queryLongScalar(conn, "SELECT COUNT(*) FROM transactions");
                double beforeSum = queryDoubleScalar(conn, "SELECT COALESCE(SUM(ROUND(total_amount, 2)), 0.0) FROM transactions");
                st.execute("DROP TABLE IF EXISTS transactions_mig_new;");
                st.execute(
                    "CREATE TABLE transactions_mig_new (" +
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
                st.execute(
                    "INSERT INTO transactions_mig_new (id, user_id, stock_id, symbol, transaction_type, quantity, price, fee_amount, total_amount, idempotency_key, transaction_date) " +
                    "SELECT id, user_id, stock_id, symbol, transaction_type, quantity, ROUND(price, 2), ROUND(COALESCE(fee_amount, 0.00), 2), ROUND(total_amount, 2), idempotency_key, transaction_date FROM transactions;"
                );
                long afterCount = queryLongScalar(conn, "SELECT COUNT(*) FROM transactions_mig_new");
                double afterSum = queryDoubleScalar(conn, "SELECT COALESCE(SUM(total_amount), 0.0) FROM transactions_mig_new");
                if (beforeCount != afterCount || Math.abs(beforeSum - afterSum) > 0.01) {
                    throw new SQLException("Migration parity check failed on transactions table.");
                }
                st.execute("DROP TABLE transactions;");
                st.execute("ALTER TABLE transactions_mig_new RENAME TO transactions;");
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_tx_user_idempotency ON transactions(user_id, idempotency_key);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_user_tx ON transactions(user_id, transaction_date);");
            }

            conn.commit();
        } catch (SQLException ex) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw ex;
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) {}
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            } catch (SQLException ignored) {}
        }
    }

    private static long queryLongScalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static double queryDoubleScalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
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
        seedUsersIfEmpty(
                conn,
                isProd,
                EnvConfig.get("ADMIN_BOOTSTRAP_USER"),
                EnvConfig.get("ADMIN_BOOTSTRAP_EMAIL"),
                EnvConfig.get("ADMIN_BOOTSTRAP_PASSWORD")
        );

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
                    } catch (Exception csvEx) {
                        System.err.println("[JDBC] CSV catalog import fallback triggered: " + csvEx.getMessage());
                    }
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
                            ins.setBigDecimal(4, new java.math.BigDecimal(s[3]));
                            ins.setInt(5, Integer.parseInt(s[4]));
                            ins.setString(6, s[5]);
                            ins.setBigDecimal(7, new java.math.BigDecimal(s[6]));
                            ins.setBigDecimal(8, new java.math.BigDecimal(s[7]));
                            ins.setBigDecimal(9, new java.math.BigDecimal(s[8]));
                            ins.setLong(10, Long.parseLong(s[9]));
                            ins.executeUpdate();
                        }
                    }
                }
            }
        }

        if (!isProd) {
            try (PreparedStatement chk = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE user_id = 'trader1'");
                 ResultSet urs = chk.executeQuery()) {
                boolean hasTrader1 = urs.next() && urs.getInt(1) > 0;
                if (hasTrader1) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM portfolio");
                         ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (PreparedStatement ins = conn.prepareStatement(
                                    "INSERT INTO portfolio (user_id, stock_id, symbol, quantity, average_price) VALUES (?, ?, ?, ?, ?)")) {
                                ins.setString(1, "trader1"); ins.setInt(2, 1); ins.setString(3, "RELIANCE"); ins.setInt(4, 20); ins.setBigDecimal(5, new java.math.BigDecimal("2900.00")); ins.executeUpdate();
                                ins.setString(1, "trader1"); ins.setInt(2, 2); ins.setString(3, "TCS"); ins.setInt(4, 10); ins.setBigDecimal(5, new java.math.BigDecimal("4150.00")); ins.executeUpdate();
                                ins.setString(1, "trader1"); ins.setInt(2, 4); ins.setString(3, "INFY"); ins.setInt(4, 25); ins.setBigDecimal(5, new java.math.BigDecimal("1850.00")); ins.executeUpdate();
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
     * Seeds initial user records strictly when the users table is empty.
     * In production mode, default demo credentials (admin/trader1/trader2) are NEVER inserted;
     * only validated ADMIN_BOOTSTRAP_* credentials are accepted if explicitly provided.
     */
    public static void seedUsersIfEmpty(Connection conn, boolean isProd,
                                        String adminUser, String adminEmail, String adminPass) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        if (isProd) {
            String u = adminUser != null ? adminUser.trim() : "";
            String e = adminEmail != null ? adminEmail.trim().toLowerCase(Locale.ROOT) : "";
            String p = adminPass != null ? adminPass : "";

            boolean anyProvided = !u.isEmpty() || !e.isEmpty() || !p.isEmpty();
            if (!anyProvided) {
                System.out.println("[JDBC] Production mode detected with empty users table: skipped default demo credentials.");
                return;
            }
            if (u.isEmpty() || e.isEmpty() || p.isEmpty()) {
                throw new SQLException("Production bootstrap admin configuration invalid: ADMIN_BOOTSTRAP_USER, ADMIN_BOOTSTRAP_EMAIL, and ADMIN_BOOTSTRAP_PASSWORD must all be provided together.");
            }
            if (!u.matches("^[a-zA-Z0-9_-]{3,40}$")) {
                throw new SQLException("Production bootstrap admin configuration invalid: ADMIN_BOOTSTRAP_USER must be 3-40 alphanumeric, underscore, or hyphen characters.");
            }
            if (!e.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new SQLException("Production bootstrap admin configuration invalid: ADMIN_BOOTSTRAP_EMAIL must be a valid email address.");
            }
            String strengthError = PasswordHasher.validatePasswordStrength(p);
            if (strengthError != null) {
                throw new SQLException("Production bootstrap admin configuration invalid: " + strengthError);
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO users (user_id, name, email, password, role, balance, department, must_change_password) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, u);
                ins.setString(2, "Platform Administrator");
                ins.setString(3, e);
                ins.setString(4, PasswordHasher.hashPassword(p));
                ins.setString(5, "ADMIN");
                ins.setBigDecimal(6, new java.math.BigDecimal("0.00"));
                ins.setString(7, "Executive Operations");
                ins.setInt(8, 0);
                ins.executeUpdate();
            }
            return;
        }

        // Development / Evaluation mode default accounts with BCrypt hashes and password-change notice flag
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users (user_id, name, email, password, role, balance, department, must_change_password) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ins.setString(1, "admin");
            ins.setString(2, "Platform Administrator");
            ins.setString(3, "admin@apex.com");
            ins.setString(4, PasswordHasher.hashPassword("admin123"));
            ins.setString(5, "ADMIN");
            ins.setBigDecimal(6, new java.math.BigDecimal("0.00"));
            ins.setString(7, "Executive Operations");
            ins.setInt(8, 1);
            ins.executeUpdate();

            ins.setString(1, "trader1");
            ins.setString(2, "Alex Mercer");
            ins.setString(3, "alex@apex.com");
            ins.setString(4, PasswordHasher.hashPassword("trader123"));
            ins.setString(5, "TRADER");
            ins.setBigDecimal(6, new java.math.BigDecimal("50000.00"));
            ins.setNull(7, Types.VARCHAR);
            ins.setInt(8, 1);
            ins.executeUpdate();

            ins.setString(1, "trader2");
            ins.setString(2, "Elena Vance");
            ins.setString(3, "elena@apex.com");
            ins.setString(4, PasswordHasher.hashPassword("trader123"));
            ins.setString(5, "TRADER");
            ins.setBigDecimal(6, new java.math.BigDecimal("75000.00"));
            ins.setNull(7, Types.VARCHAR);
            ins.setInt(8, 1);
            ins.executeUpdate();
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
            map.put("engineType", useMySQL ? "MySQL (Relational Production TLS)" : "SQLite (Embedded Relational ACID)");
            map.put("jdbcUrl", useMySQL ? "jdbc:mysql://[configured-host]/[configured-db]" : "jdbc:sqlite:[embedded-storage]");

            BoundedConnectionPool.PoolStats stats = getPoolStats();
            if (stats != null) {
                map.put("poolMaxSize", stats.maxPoolSize);
                map.put("poolActive", stats.activeConnections);
                map.put("poolIdle", stats.idleConnections);
            }

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
