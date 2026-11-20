package dao;

import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Persistent relational DAO for authentication rate-limiting & progressive temporary throttling.
 * Survives server restarts and coordinates across multiple JVM instances sharing SQLite or MySQL.
 */
public class LoginRateLimitDAO {

    public static final class RateLimitState {
        public final String rateKey;
        public final String scope;
        public final int attemptCount;
        public final long lastAttemptAt;
        public final long throttleUntil;

        public RateLimitState(String rateKey, String scope, int attemptCount, long lastAttemptAt, long throttleUntil) {
            this.rateKey = rateKey;
            this.scope = scope;
            this.attemptCount = attemptCount;
            this.lastAttemptAt = lastAttemptAt;
            this.throttleUntil = throttleUntil;
        }
    }

    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    private final ConnectionSupplier connectionSupplier;
    private volatile boolean tableEnsured = false;

    public LoginRateLimitDAO() {
        this(DBConnection::getConnection);
    }

    public LoginRateLimitDAO(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = connectionSupplier != null ? connectionSupplier : DBConnection::getConnection;
    }

    private void ensureTableExists(Connection conn) throws SQLException {
        if (tableEnsured) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE IF NOT EXISTS login_rate_limits (" +
                "  rate_key VARCHAR(200) PRIMARY KEY," +
                "  scope VARCHAR(20) NOT NULL," +
                "  attempt_count INTEGER NOT NULL DEFAULT 0," +
                "  last_attempt_at BIGINT NOT NULL," +
                "  throttle_until BIGINT NOT NULL DEFAULT 0" +
                ")"
            );
            tableEnsured = true;
        }
    }

    /**
     * Checks whether the given key is currently in a temporary cooldown window.
     * Expired cooldown windows are cleared automatically; never locks permanently.
     *
     * @return remaining cooldown milliseconds (> 0 if throttled, 0 if allowed)
     */
    public long getRemainingThrottleMs(String rateKey, long windowResetMs, long nowMs) throws SQLException {
        if (rateKey == null || rateKey.isEmpty()) return 0L;
        try (Connection conn = connectionSupplier.get()) {
            ensureTableExists(conn);
            String selectSql = "SELECT attempt_count, last_attempt_at, throttle_until FROM login_rate_limits WHERE rate_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, rateKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return 0L;
                    }
                    long lastAttemptAt = rs.getLong("last_attempt_at");
                    long throttleUntil = rs.getLong("throttle_until");
                    if (throttleUntil > nowMs) {
                        return throttleUntil - nowMs;
                    }
                    // Inactivity window elapsed cleanly: expire previous counter automatically
                    if ((nowMs - lastAttemptAt) >= windowResetMs) {
                        deleteKeyQuietly(conn, rateKey);
                    }
                    return 0L;
                }
            }
        }
    }

    /**
     * Atomically records an authentication failure for the normalized key and computes
     * progressive exponential backoff once attempts reach maxAttempts.
     */
    public synchronized RateLimitState recordFailure(
            String rateKey,
            String scope,
            int maxAttempts,
            long baseBackoffMs,
            long maxBackoffMs,
            long windowResetMs,
            long nowMs) throws SQLException {

        int safeMaxAttempts = Math.max(1, maxAttempts);
        long safeBase = Math.max(50L, baseBackoffMs);
        long safeMax = Math.max(safeBase, maxBackoffMs);

        try (Connection conn = connectionSupplier.get()) {
            ensureTableExists(conn);
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                boolean mysql = DBConnection.isMySQL();
                String lockSql = mysql
                        ? "SELECT attempt_count, last_attempt_at, throttle_until FROM login_rate_limits WHERE rate_key = ? FOR UPDATE"
                        : "SELECT attempt_count, last_attempt_at, throttle_until FROM login_rate_limits WHERE rate_key = ?";

                boolean exists = false;
                int prevCount = 0;
                long prevLastAt = 0L;

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setString(1, rateKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            exists = true;
                            prevCount = rs.getInt("attempt_count");
                            prevLastAt = rs.getLong("last_attempt_at");
                        }
                    }
                }

                boolean expiredInactivity = (nowMs - prevLastAt) >= windowResetMs;
                int nextCount = (!exists || expiredInactivity) ? 1 : (prevCount + 1);

                long nextThrottleUntil = 0L;
                if (nextCount >= safeMaxAttempts) {
                    int exponent = Math.min(6, nextCount - safeMaxAttempts);
                    long backoffMs = Math.min(safeMax, safeBase * (1L << exponent));
                    nextThrottleUntil = nowMs + backoffMs;
                }

                if (!exists) {
                    String insSql = "INSERT INTO login_rate_limits (rate_key, scope, attempt_count, last_attempt_at, throttle_until) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insSql)) {
                        ins.setString(1, rateKey);
                        ins.setString(2, scope);
                        ins.setInt(3, nextCount);
                        ins.setLong(4, nowMs);
                        ins.setLong(5, nextThrottleUntil);
                        ins.executeUpdate();
                    }
                } else {
                    String updSql = "UPDATE login_rate_limits SET scope = ?, attempt_count = ?, last_attempt_at = ?, throttle_until = ? WHERE rate_key = ?";
                    try (PreparedStatement upd = conn.prepareStatement(updSql)) {
                        upd.setString(1, scope);
                        upd.setInt(2, nextCount);
                        upd.setLong(3, nowMs);
                        upd.setLong(4, nextThrottleUntil);
                        upd.setString(5, rateKey);
                        upd.executeUpdate();
                    }
                }

                conn.commit();
                return new RateLimitState(rateKey, scope, nextCount, nowMs, nextThrottleUntil);
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {}
            }
        }
    }

    public int getActiveAttemptCount(String rateKey, long windowResetMs, long nowMs) throws SQLException {
        if (rateKey == null || rateKey.isEmpty()) return 0;
        try (Connection conn = connectionSupplier.get()) {
            ensureTableExists(conn);
            String sql = "SELECT attempt_count, last_attempt_at FROM login_rate_limits WHERE rate_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, rateKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    int count = rs.getInt("attempt_count");
                    long lastAt = rs.getLong("last_attempt_at");
                    if ((nowMs - lastAt) >= windowResetMs) {
                        deleteKeyQuietly(conn, rateKey);
                        return 0;
                    }
                    return count;
                }
            }
        }
    }

    public void reset(String rateKey) throws SQLException {
        if (rateKey == null || rateKey.isEmpty()) return;
        try (Connection conn = connectionSupplier.get()) {
            ensureTableExists(conn);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM login_rate_limits WHERE rate_key = ?")) {
                ps.setString(1, rateKey);
                ps.executeUpdate();
            }
        }
    }

    public void purgeExpired(long cutoffLastAttemptMs, long nowMs) throws SQLException {
        try (Connection conn = connectionSupplier.get()) {
            ensureTableExists(conn);
            String sql = "DELETE FROM login_rate_limits WHERE (throttle_until > 0 AND throttle_until <= ?) OR (throttle_until = 0 AND last_attempt_at <= ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nowMs);
                ps.setLong(2, cutoffLastAttemptMs);
                ps.executeUpdate();
            }
        }
    }

    private static void deleteKeyQuietly(Connection conn, String rateKey) {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM login_rate_limits WHERE rate_key = ?")) {
            del.setString(1, rateKey);
            del.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
