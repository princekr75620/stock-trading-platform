package util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, leak-safe JDBC Connection Pool enforcing:
 * - Strict maximum active+idle connection limit (maxPoolSize)
 * - Configurable minimum idle connections (minIdle)
 * - Bounded acquisition timeout (connectionTimeoutMs)
 * - Connection max lifetime retirement (maxLifetimeMs)
 * - Connection validation before reuse (isValid)
 * - Clean auto-commit reset & uncommitted TX rollback upon close()
 * - Deterministic shutdown closing all physical connections
 */
public class BoundedConnectionPool {

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection create() throws SQLException;
    }

    public static final class PoolStats {
        public final int activeConnections;
        public final int idleConnections;
        public final int totalConnections;
        public final int maxPoolSize;
        public final int minIdle;
        public final long connectionTimeoutMs;
        public final long maxLifetimeMs;
        public final boolean closed;

        public PoolStats(int activeConnections, int idleConnections, int totalConnections,
                         int maxPoolSize, int minIdle, long connectionTimeoutMs,
                         long maxLifetimeMs, boolean closed) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.maxPoolSize = maxPoolSize;
            this.minIdle = minIdle;
            this.connectionTimeoutMs = connectionTimeoutMs;
            this.maxLifetimeMs = maxLifetimeMs;
            this.closed = closed;
        }
    }

    private static final class PooledEntry {
        final Connection physical;
        final long createdAtMs;
        volatile long lastReturnedAtMs;

        PooledEntry(Connection physical, long nowMs) {
            this.physical = physical;
            this.createdAtMs = nowMs;
            this.lastReturnedAtMs = nowMs;
        }
    }

    private final ConnectionFactory factory;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeoutMs;
    private final long maxLifetimeMs;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final Deque<PooledEntry> idleQueue = new ArrayDeque<>();
    private int totalCreated = 0;
    private int activeCount = 0;
    private volatile boolean closed = false;

    public BoundedConnectionPool(ConnectionFactory factory,
                                 int maxPoolSize,
                                 int minIdle,
                                 long connectionTimeoutMs,
                                 long maxLifetimeMs) {
        if (factory == null) throw new IllegalArgumentException("ConnectionFactory is required.");
        if (maxPoolSize < 1) throw new IllegalArgumentException("maxPoolSize must be >= 1.");
        if (minIdle < 0 || minIdle > maxPoolSize) {
            throw new IllegalArgumentException("minIdle must be between 0 and maxPoolSize.");
        }
        if (connectionTimeoutMs < 1) throw new IllegalArgumentException("connectionTimeoutMs must be >= 1.");
        if (maxLifetimeMs < 100) throw new IllegalArgumentException("maxLifetimeMs must be >= 100.");

        this.factory = factory;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.maxLifetimeMs = maxLifetimeMs;
    }

    /**
     * Pre-warms minIdle connections best-effort without masking real connectivity errors if requested.
     */
    public void ensureMinIdle() throws SQLException {
        lock.lock();
        try {
            if (closed) return;
            while (idleQueue.size() < minIdle && totalCreated < maxPoolSize) {
                Connection phys = factory.create();
                idleQueue.addLast(new PooledEntry(phys, System.currentTimeMillis()));
                totalCreated++;
            }
        } finally {
            lock.unlock();
        }
    }

    public Connection getConnection() throws SQLException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(connectionTimeoutMs);

        while (true) {
            PooledEntry candidateToValidate = null;
            boolean shouldCreateNew = false;

            lock.lock();
            try {
                if (closed) {
                    throw new SQLException("Connection pool has been shut down.");
                }

                if (!idleQueue.isEmpty()) {
                    candidateToValidate = idleQueue.pollFirst();
                    activeCount++;
                } else if (totalCreated < maxPoolSize) {
                    totalCreated++;
                    activeCount++;
                    shouldCreateNew = true;
                } else {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        throw new SQLException("Connection pool exhausted: timed out after " + connectionTimeoutMs
                                + "ms (maxPoolSize=" + maxPoolSize + ", active=" + activeCount + ").");
                    }
                    try {
                        available.awaitNanos(remainingNanos);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting for pooled database connection.", ie);
                    }
                    continue;
                }
            } finally {
                lock.unlock();
            }

            if (shouldCreateNew) {
                try {
                    Connection physical = factory.create();
                    PooledEntry created = new PooledEntry(physical, System.currentTimeMillis());
                    return wrapConnection(created);
                } catch (Throwable t) {
                    lock.lock();
                    try {
                        activeCount--;
                        totalCreated--;
                        available.signal();
                    } finally {
                        lock.unlock();
                    }
                    if (t instanceof SQLException) throw (SQLException) t;
                    throw new SQLException("Failed to establish database connection: " + t.getMessage(), t);
                }
            }

            // Validate borrowed idle entry outside lock so network/driver checks never block other threads
            if (isHealthyAndFresh(candidateToValidate)) {
                return wrapConnection(candidateToValidate);
            }

            // Retire dead or expired physical connection and loop to obtain a fresh one
            closeQuietly(candidateToValidate.physical);
            lock.lock();
            try {
                activeCount--;
                totalCreated--;
                available.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean isHealthyAndFresh(PooledEntry entry) {
        if (entry == null || entry.physical == null) return false;
        long ageMs = System.currentTimeMillis() - entry.createdAtMs;
        if (ageMs >= maxLifetimeMs) {
            return false;
        }
        try {
            return !entry.physical.isClosed() && entry.physical.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Handle interface implemented by pooled Connection proxies so callers (e.g. TradingService)
     * can flag a connection as broken after an unrecoverable rollback/TX error so it is discarded
     * instead of recycled back to the idle pool.
     */
    public interface PoolManagedConnection {
        void markBroken();
        boolean isBroken();
    }

    /**
     * Marks a pooled Connection as broken so close() physically destroys it instead of returning it to idleQueue.
     * No-op for null or non-pooled direct DriverManager connections.
     */
    public static void markBroken(Connection conn) {
        if (conn instanceof PoolManagedConnection) {
            ((PoolManagedConnection) conn).markBroken();
        }
    }

    private Connection wrapConnection(PooledEntry entry) {
        AtomicBoolean handleClosed = new AtomicBoolean(false);
        AtomicBoolean handleBroken = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                BoundedConnectionPool.class.getClassLoader(),
                new Class<?>[]{Connection.class, PoolManagedConnection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("markBroken".equals(name)) {
                        handleBroken.set(true);
                        return null;
                    }
                    if ("isBroken".equals(name)) {
                        return handleBroken.get();
                    }
                    if ("close".equals(name)) {
                        if (handleClosed.compareAndSet(false, true)) {
                            releaseConnection(entry, handleBroken.get());
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return handleClosed.get() || entry.physical.isClosed();
                    }
                    if (handleClosed.get()) {
                        throw new SQLException("Connection handle has already been closed.");
                    }
                    try {
                        return method.invoke(entry.physical, args);
                    } catch (InvocationTargetException ite) {
                        throw ite.getCause() != null ? ite.getCause() : ite;
                    }
                }
        );
    }

    private void releaseConnection(PooledEntry entry, boolean brokenHint) {
        boolean keepReusable = !brokenHint && !closed && entry.physical != null;
        if (keepReusable) {
            long ageMs = System.currentTimeMillis() - entry.createdAtMs;
            if (ageMs >= maxLifetimeMs) {
                keepReusable = false;
            } else {
                try {
                    if (entry.physical.isClosed()) {
                        keepReusable = false;
                    } else {
                        if (!entry.physical.getAutoCommit()) {
                            // Never call setAutoCommit(true) if rollback fails and TX state is uncertain
                            entry.physical.rollback();
                            entry.physical.setAutoCommit(true);
                        }
                        entry.physical.clearWarnings();
                        entry.lastReturnedAtMs = System.currentTimeMillis();
                    }
                } catch (SQLException e) {
                    keepReusable = false;
                }
            }
        }

        if (!keepReusable) {
            closeQuietly(entry.physical);
        }

        lock.lock();
        try {
            activeCount = Math.max(0, activeCount - 1);
            if (keepReusable && !closed) {
                idleQueue.addLast(entry);
            } else {
                totalCreated = Math.max(0, totalCreated - 1);
            }
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            closed = true;
            while (!idleQueue.isEmpty()) {
                PooledEntry entry = idleQueue.pollFirst();
                closeQuietly(entry.physical);
                totalCreated = Math.max(0, totalCreated - 1);
            }
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public PoolStats getStats() {
        lock.lock();
        try {
            return new PoolStats(
                    activeCount,
                    idleQueue.size(),
                    totalCreated,
                    maxPoolSize,
                    minIdle,
                    connectionTimeoutMs,
                    maxLifetimeMs,
                    closed
            );
        } finally {
            lock.unlock();
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            if (!conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
            // Best-effort cleanup when discarding retired physical connection
        }
    }
}
