package service;

import dao.*;
import exception.*;
import model.*;
import util.BoundedConnectionPool;
import util.DBConnection;
import util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authoritative Core Trading Engine executing equity BUY and SELL orders with ACID JDBC transactions.
 * Enforces:
 * - BigDecimal financial precision (0.15% brokerage fee, HALF_UP rounding) via MoneyUtil
 * - Row-level locking on MySQL (`FOR UPDATE`), `transaction_mode=IMMEDIATE` on SQLite, and atomic conditional SQL guards on balances & inventory
 * - Idempotency key deduplication via `trade_idempotency` table inside the ACID transaction
 * - Active market status validation on BOTH buy and sell orders
 * - Strict separation between ACID DB transaction lifecycle and post-commit-only notification/cache side effects
 * - Discarding broken connections on rollback/autoCommit failures without calling setAutoCommit(true)
 */
public class TradingService implements ITradingService, TradingServiceInterface {

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    private final IMarketUpdateService marketUpdateService;
    private final IPortfolioService portfolioService;
    private final INotificationService notificationService;
    private final IUserService userService;
    private final ConnectionProvider connectionProvider;

    private final UserDAO userDAO;
    private final StockDAO stockDAO;
    private final PortfolioDAO portfolioDAO;
    private final TransactionDAO transactionDAO;

    public TradingService(IMarketUpdateService marketUpdateService,
                          IPortfolioService portfolioService,
                          INotificationService notificationService,
                          IUserService userService) {
        this(marketUpdateService, portfolioService, notificationService, userService, DBConnection::getConnection, true);
    }

    public TradingService(IMarketUpdateService marketUpdateService,
                          IPortfolioService portfolioService,
                          INotificationService notificationService,
                          IUserService userService,
                          ConnectionProvider customConnectionProvider) {
        this(marketUpdateService, portfolioService, notificationService, userService,
                customConnectionProvider != null ? customConnectionProvider : DBConnection::getConnection, false);
    }

    private TradingService(IMarketUpdateService marketUpdateService,
                           IPortfolioService portfolioService,
                           INotificationService notificationService,
                           IUserService userService,
                           ConnectionProvider connectionProvider,
                           boolean initDb) {
        this.marketUpdateService = marketUpdateService;
        this.portfolioService = portfolioService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.connectionProvider = connectionProvider;

        this.userDAO = new UserDAO();
        this.stockDAO = new StockDAO();
        this.portfolioDAO = new PortfolioDAO();
        this.transactionDAO = new TransactionDAO();

        if (initDb) {
            DBConnection.initializeDatabase();
        }
    }

    public TradingService(IMarketUpdateService marketUpdateService,
                          IPortfolioService portfolioService,
                          INotificationService notificationService,
                          IUserService userService,
                          List<Trade> ignoredInitialTrades) {
        this(marketUpdateService, portfolioService, notificationService, userService);
    }

    @Override
    public Trade executeBuy(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientStockQuantityException {
        try {
            Transaction tx = executeBuyTransaction(traderId, symbol, quantity, null);
            Stock stock = stockDAO.findBySymbol(tx.getSymbol());
            String stockName = stock != null ? stock.getName() : tx.getSymbol();
            return tx.toTrade(stockName);
        } catch (InsufficientBalanceException e) {
            throw new InvalidInputException(e.getMessage());
        } catch (InsufficientStockException e) {
            throw new InsufficientStockQuantityException(e.getMessage());
        } catch (InvalidStockException e) {
            throw new StockNotFoundException(e.getMessage());
        } catch (InvalidTransactionException e) {
            throw new InvalidInputException(e.getMessage());
        } catch (SQLException e) {
            throw new InvalidInputException("Database transaction failed: " + e.getMessage());
        }
    }

    @Override
    public Transaction executeBuyTransaction(String traderId, String symbol, int quantity)
            throws InsufficientBalanceException, InsufficientStockException,
                   InvalidStockException, InvalidTransactionException, SQLException {
        return executeBuyTransaction(traderId, symbol, quantity, null);
    }

    @Override
    public Transaction executeBuyTransaction(String traderId, String symbol, int quantity, String idempotencyKey)
            throws InsufficientBalanceException, InsufficientStockException,
                   InvalidStockException, InvalidTransactionException, SQLException {

        if (traderId == null || traderId.trim().isEmpty()) {
            throw new InvalidTransactionException("Authenticated trader ID is required.");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidTransactionException("Stock symbol cannot be empty.");
        }
        if (quantity <= 0 || quantity > MoneyUtil.MAX_ORDER_QUANTITY) {
            throw new InvalidTransactionException("Purchase quantity must be between 1 and " + MoneyUtil.MAX_ORDER_QUANTITY + ".");
        }
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.trim().isEmpty())
                ? idempotencyKey.trim() : null;
        if (trimmedKey != null && trimmedKey.length() > 100) {
            throw new InvalidTransactionException("Idempotency-Key exceeds maximum length.");
        }

        String uid = traderId.trim();
        String sym = symbol.toUpperCase().trim();
        Connection conn = null;
        boolean committed = false;
        boolean rollbackFailed = false;

        Transaction tx = null;
        String committedUserId = null;
        BigDecimal committedPrice = null;
        BigDecimal committedFee = null;
        BigDecimal committedTotal = null;
        BigDecimal remainingBalance = null;

        try {
            conn = connectionProvider.getConnection();
            if (DBConnection.isMySQL()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }
            conn.setAutoCommit(false);

            // Step 0: Reject duplicate order submissions within the same atomic transaction
            if (trimmedKey != null && transactionDAO.existsIdempotencyKey(conn, uid, trimmedKey)) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate BUY order blocked: this order request has already been processed.");
            }

            // Step 1: Lock & load authoritative stock quote & float from DB
            Stock stock = stockDAO.findBySymbolForUpdate(conn, sym);
            if (stock == null) {
                throw new InvalidStockException("Stock symbol '" + sym + "' was not found on exchange.", sym);
            }
            if (!"ACTIVE".equalsIgnoreCase(stock.getStatus())) {
                throw new InvalidTransactionException("Trading is currently halted or suspended for " + sym + ".");
            }
            if (stock.getPriceDecimal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidTransactionException("Stock '" + sym + "' has an invalid quote price.");
            }
            if (stock.getAvailableQuantity() < quantity) {
                throw new InsufficientStockException(sym, quantity, stock.getAvailableQuantity());
            }

            // Step 2: Lock & load authoritative user balance from DB
            User user = userDAO.findByIdForUpdate(conn, uid);
            if (user == null) {
                throw new InvalidTransactionException("Trader account not found in database: " + uid);
            }

            BigDecimal currentPrice = MoneyUtil.round(stock.getPriceDecimal());
            BigDecimal subtotal;
            BigDecimal feeAmount;
            BigDecimal totalAmount;
            try {
                subtotal = MoneyUtil.calculateSubtotal(currentPrice, quantity);
                feeAmount = MoneyUtil.calculateFee(subtotal);
                totalAmount = MoneyUtil.calculateBuyTotal(currentPrice, quantity);
            } catch (IllegalArgumentException ex) {
                throw new InvalidTransactionException(ex.getMessage());
            }

            BigDecimal currentBalance = MoneyUtil.round(user.getBalanceDecimal());
            if (currentBalance.compareTo(totalAmount) < 0) {
                throw new InsufficientBalanceException(totalAmount.doubleValue(), currentBalance.doubleValue());
            }

            // Step 3: Atomically deduct cash balance with conditional SQL balance >= totalAmount guard
            if (!userDAO.adjustBalanceAtomically(conn, user.getUserId(), totalAmount.negate(), totalAmount)) {
                throw new InsufficientBalanceException(totalAmount.doubleValue(), currentBalance.doubleValue());
            }

            // Step 4: Atomically decrement ACTIVE stock float & increment volume
            if (!stockDAO.updateQuantityAndVolume(conn, sym, quantity, true)) {
                throw new InsufficientStockException(sym, quantity, stock.getAvailableQuantity());
            }

            // Step 5: Upsert portfolio holdings & weighted average price using BigDecimal
            if (!portfolioDAO.recordBuy(conn, user.getUserId(), stock.getId(), sym, quantity, currentPrice)) {
                throw new SQLException("Failed to update portfolio holdings for " + sym);
            }

            // Step 6: Insert transaction audit record
            tx = new Transaction(
                    user.getUserId(),
                    stock.getId(),
                    sym,
                    "BUY",
                    quantity,
                    currentPrice,
                    feeAmount,
                    totalAmount,
                    trimmedKey
            );
            if (!transactionDAO.insert(conn, tx)) {
                throw new SQLException("Failed to record transaction ledger entry.");
            }

            // Step 7: Persist idempotency key in the same ACID transaction
            if (trimmedKey != null && !transactionDAO.recordIdempotencyKey(conn, user.getUserId(), trimmedKey, tx.getId())) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate BUY order blocked: this order request has already been processed.");
            }

            // Step 8: Commit ACID transaction (point of durability)
            conn.commit();
            committed = true;
            committedUserId = user.getUserId();
            committedPrice = currentPrice;
            committedFee = feeAmount;
            committedTotal = totalAmount;
            remainingBalance = currentBalance.subtract(totalAmount);

        } catch (Exception e) {
            if (conn != null && !committed) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    rollbackFailed = true;
                    BoundedConnectionPool.markBroken(conn);
                    System.err.println("[TradingService] Failed to rollback BUY transaction; discarding connection: " + rbEx.getMessage());
                }
            }
            if (trimmedKey != null && e instanceof SQLException && isUniqueConstraintViolation((SQLException) e)) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate BUY order blocked: this order request has already been processed.");
            }
            if (e instanceof DuplicateOrderException) throw (DuplicateOrderException) e;
            if (e instanceof InsufficientBalanceException) throw (InsufficientBalanceException) e;
            if (e instanceof InsufficientStockException) throw (InsufficientStockException) e;
            if (e instanceof InvalidStockException) throw (InvalidStockException) e;
            if (e instanceof InvalidTransactionException) throw (InvalidTransactionException) e;
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Transaction execution failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                if (!rollbackFailed) {
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException acEx) {
                        BoundedConnectionPool.markBroken(conn);
                        System.err.println("[TradingService] Failed resetting autoCommit on BUY connection; discarding: " + acEx.getMessage());
                    }
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }

        // Post-commit side effects execute strictly outside DB TX lifecycle and cannot fail/rollback a committed trade
        runPostCommitBuySideEffects(committedUserId, sym, quantity, committedPrice, committedFee, committedTotal, remainingBalance);
        return tx;
    }

    @Override
    public Trade executeSell(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientHoldingsException {
        try {
            Transaction tx = executeSellTransaction(traderId, symbol, quantity, null);
            Stock stock = stockDAO.findBySymbol(tx.getSymbol());
            String stockName = stock != null ? stock.getName() : tx.getSymbol();
            return tx.toTrade(stockName);
        } catch (InvalidStockException e) {
            throw new StockNotFoundException(e.getMessage());
        } catch (InvalidTransactionException e) {
            throw new InvalidInputException(e.getMessage());
        } catch (InsufficientHoldingsException e) {
            throw e;
        } catch (SQLException e) {
            throw new InvalidInputException("Database transaction failed: " + e.getMessage());
        }
    }

    @Override
    public Transaction executeSellTransaction(String traderId, String symbol, int quantity)
            throws InsufficientHoldingsException, InvalidStockException,
                   InvalidTransactionException, SQLException {
        return executeSellTransaction(traderId, symbol, quantity, null);
    }

    @Override
    public Transaction executeSellTransaction(String traderId, String symbol, int quantity, String idempotencyKey)
            throws InsufficientHoldingsException, InvalidStockException,
                   InvalidTransactionException, SQLException {

        if (traderId == null || traderId.trim().isEmpty()) {
            throw new InvalidTransactionException("Authenticated trader ID is required.");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidTransactionException("Stock symbol cannot be empty.");
        }
        if (quantity <= 0 || quantity > MoneyUtil.MAX_ORDER_QUANTITY) {
            throw new InvalidTransactionException("Sell quantity must be between 1 and " + MoneyUtil.MAX_ORDER_QUANTITY + ".");
        }
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.trim().isEmpty())
                ? idempotencyKey.trim() : null;
        if (trimmedKey != null && trimmedKey.length() > 100) {
            throw new InvalidTransactionException("Idempotency-Key exceeds maximum length.");
        }

        String uid = traderId.trim();
        String sym = symbol.toUpperCase().trim();
        Connection conn = null;
        boolean committed = false;
        boolean rollbackFailed = false;

        Transaction tx = null;
        String committedUserId = null;
        BigDecimal committedPrice = null;
        BigDecimal committedFee = null;
        BigDecimal committedNetProceeds = null;
        BigDecimal updatedBalance = null;

        try {
            conn = connectionProvider.getConnection();
            if (DBConnection.isMySQL()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }
            conn.setAutoCommit(false);

            // Step 0: Deduplicate retried/double-clicked SELL requests
            if (trimmedKey != null && transactionDAO.existsIdempotencyKey(conn, uid, trimmedKey)) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate SELL order blocked: this order request has already been processed.");
            }

            // Step 1: Lock & load authoritative stock from DB and enforce ACTIVE status
            Stock stock = stockDAO.findBySymbolForUpdate(conn, sym);
            if (stock == null) {
                throw new InvalidStockException("Stock symbol '" + sym + "' was not found on exchange.", sym);
            }
            if (!"ACTIVE".equalsIgnoreCase(stock.getStatus())) {
                throw new InvalidTransactionException("Trading is currently halted or suspended for " + sym + ".");
            }
            if (stock.getPriceDecimal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidTransactionException("Stock '" + sym + "' has an invalid quote price.");
            }

            // Step 2: Lock & load authoritative user from DB
            User user = userDAO.findByIdForUpdate(conn, uid);
            if (user == null) {
                throw new InvalidTransactionException("Trader account not found in database: " + uid);
            }

            // Step 3: Check portfolio holdings strictly in DB within transaction
            Map<String, Integer> holdings = portfolioDAO.getHoldings(conn, user.getUserId());
            int ownedQty = holdings.getOrDefault(sym, 0);
            if (ownedQty < quantity) {
                throw new InsufficientHoldingsException(String.format(
                        "Cannot sell %d shares of %s. You currently own only %d shares.",
                        quantity, sym, ownedQty
                ));
            }

            BigDecimal currentPrice = MoneyUtil.round(stock.getPriceDecimal());
            BigDecimal subtotal;
            BigDecimal feeAmount;
            BigDecimal netProceeds;
            try {
                subtotal = MoneyUtil.calculateSubtotal(currentPrice, quantity);
                feeAmount = MoneyUtil.calculateFee(subtotal);
                netProceeds = MoneyUtil.calculateSellNetProceeds(currentPrice, quantity);
            } catch (IllegalArgumentException ex) {
                throw new InvalidTransactionException(ex.getMessage());
            }

            BigDecimal currentBalance = MoneyUtil.round(user.getBalanceDecimal());
            if (currentBalance.add(netProceeds).compareTo(MoneyUtil.MAX_MONETARY_AMOUNT) > 0) {
                throw new InvalidTransactionException("Resulting balance exceeds platform safety limit.");
            }

            // Step 4: Decrease/remove portfolio holding atomically with quantity >= ? guard
            if (!portfolioDAO.recordSell(conn, user.getUserId(), sym, quantity)) {
                throw new InsufficientHoldingsException(sym, quantity, ownedQty);
            }

            // Step 5: Credit net proceeds (after 0.15% fee) to cash balance atomically
            if (!userDAO.adjustBalanceAtomically(conn, user.getUserId(), netProceeds, null)) {
                throw new SQLException("Failed to update cash balance for trader: " + user.getUserId());
            }

            // Step 6: Return shares to market float for ACTIVE stock
            if (!stockDAO.updateQuantityAndVolume(conn, sym, quantity, false)) {
                throw new InvalidTransactionException("Failed to update active stock inventory for " + sym);
            }

            // Step 7: Insert transaction audit record
            tx = new Transaction(
                    user.getUserId(),
                    stock.getId(),
                    sym,
                    "SELL",
                    quantity,
                    currentPrice,
                    feeAmount,
                    netProceeds,
                    trimmedKey
            );
            if (!transactionDAO.insert(conn, tx)) {
                throw new SQLException("Failed to record transaction ledger entry.");
            }

            // Step 8: Persist idempotency key atomically
            if (trimmedKey != null && !transactionDAO.recordIdempotencyKey(conn, user.getUserId(), trimmedKey, tx.getId())) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate SELL order blocked: this order request has already been processed.");
            }

            // Step 9: Commit ACID transaction (point of durability)
            conn.commit();
            committed = true;
            committedUserId = user.getUserId();
            committedPrice = currentPrice;
            committedFee = feeAmount;
            committedNetProceeds = netProceeds;
            updatedBalance = currentBalance.add(netProceeds);

        } catch (Exception e) {
            if (conn != null && !committed) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    rollbackFailed = true;
                    BoundedConnectionPool.markBroken(conn);
                    System.err.println("[TradingService] Failed to rollback SELL transaction; discarding connection: " + rbEx.getMessage());
                }
            }
            if (trimmedKey != null && e instanceof SQLException && isUniqueConstraintViolation((SQLException) e)) {
                throw new DuplicateOrderException(trimmedKey,
                        "Duplicate SELL order blocked: this order request has already been processed.");
            }
            if (e instanceof DuplicateOrderException) throw (DuplicateOrderException) e;
            if (e instanceof InsufficientHoldingsException) throw (InsufficientHoldingsException) e;
            if (e instanceof InvalidStockException) throw (InvalidStockException) e;
            if (e instanceof InvalidTransactionException) throw (InvalidTransactionException) e;
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Sell order execution failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                if (!rollbackFailed) {
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException acEx) {
                        BoundedConnectionPool.markBroken(conn);
                        System.err.println("[TradingService] Failed resetting autoCommit on SELL connection; discarding: " + acEx.getMessage());
                    }
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }

        // Post-commit side effects execute strictly outside DB TX lifecycle and cannot fail/rollback a committed trade
        runPostCommitSellSideEffects(committedUserId, sym, quantity, committedPrice, committedFee, committedNetProceeds, updatedBalance);
        return tx;
    }

    private void runPostCommitBuySideEffects(String userId, String symbol, int quantity,
                                             BigDecimal price, BigDecimal fee, BigDecimal total, BigDecimal newBalance) {
        if (portfolioService != null) {
            try {
                portfolioService.updateOnBuy(userId, symbol, quantity, price.doubleValue());
            } catch (Exception ex) {
                System.err.println("[TradingService] Post-commit BUY portfolio cache update warning for " + symbol + ": " + ex.getMessage());
            }
        }
        if (notificationService != null) {
            try {
                notificationService.sendNotification(new Notification(
                        "NOTIF-" + (System.currentTimeMillis() % 100000),
                        userId,
                        "Order Executed: BUY " + symbol,
                        String.format("Purchased %d shares of %s at ₹%.2f (Fee: ₹%.2f). Total: ₹%.2f. Cash remaining: ₹%.2f.",
                                quantity, symbol, price.doubleValue(), fee.doubleValue(),
                                total.doubleValue(), newBalance.doubleValue()),
                        Notification.NotificationType.TRADE_BUY
                ));
            } catch (Exception ex) {
                System.err.println("[TradingService] Post-commit BUY notification warning for " + userId + ": " + ex.getMessage());
            }
        }
    }

    private void runPostCommitSellSideEffects(String userId, String symbol, int quantity,
                                              BigDecimal price, BigDecimal fee, BigDecimal netProceeds, BigDecimal newBalance) {
        if (portfolioService != null) {
            try {
                portfolioService.updateOnSell(userId, symbol, quantity, price.doubleValue());
            } catch (Exception ex) {
                System.err.println("[TradingService] Post-commit SELL portfolio cache update warning for " + symbol + ": " + ex.getMessage());
            }
        }
        if (notificationService != null) {
            try {
                notificationService.sendNotification(new Notification(
                        "NOTIF-" + (System.currentTimeMillis() % 100000),
                        userId,
                        "Order Executed: SELL " + symbol,
                        String.format("Sold %d shares of %s at ₹%.2f (Fee: ₹%.2f). Net Proceeds: ₹%.2f. Balance: ₹%.2f.",
                                quantity, symbol, price.doubleValue(), fee.doubleValue(),
                                netProceeds.doubleValue(), newBalance.doubleValue()),
                        Notification.NotificationType.TRADE_SELL
                ));
            } catch (Exception ex) {
                System.err.println("[TradingService] Post-commit SELL notification warning for " + userId + ": " + ex.getMessage());
            }
        }
    }

    private boolean isUniqueConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return "23000".equals(state) || "23505".equals(state) || msg.contains("unique") || msg.contains("duplicate");
    }

    @Override
    public ArrayList<Transaction> getTransactionsByUser(String traderId) {
        try {
            return transactionDAO.findByUserId(traderId);
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching transactions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> getAllTransactions() {
        try {
            return transactionDAO.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching all transactions: " + e.getMessage(), e);
        }
    }

    private List<Trade> mapTransactionsToTrades(List<Transaction> txList) throws SQLException {
        Map<String, Stock> stocksBySymbol = stockDAO.findAllAsMap();
        List<Trade> trades = new ArrayList<>(txList.size());
        for (Transaction tx : txList) {
            Stock st = stocksBySymbol.get(tx.getSymbol().toUpperCase());
            String stockName = (st != null) ? st.getName() : tx.getSymbol();
            trades.add(tx.toTrade(stockName));
        }
        return trades;
    }

    @Override
    public List<Trade> getAllTrades() {
        try {
            return mapTransactionsToTrades(transactionDAO.findAll());
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading trades: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Trade> getTradesForTrader(String traderId) {
        if (traderId == null || traderId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return mapTransactionsToTrades(transactionDAO.findByUserId(traderId.trim()));
        } catch (SQLException e) {
            throw new RuntimeException("Database error loading trades for trader '" + traderId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public int getTotalTradeCount() {
        return getAllTransactions().size();
    }

    @Override
    public int getBuyTradeCount() {
        int count = 0;
        for (Transaction tx : getAllTransactions()) {
            if ("BUY".equalsIgnoreCase(tx.getTransactionType())) count++;
        }
        return count;
    }

    @Override
    public int getSellTradeCount() {
        int count = 0;
        for (Transaction tx : getAllTransactions()) {
            if ("SELL".equalsIgnoreCase(tx.getTransactionType())) count++;
        }
        return count;
    }

    @Override
    public double getTotalTradingVolume() {
        BigDecimal vol = BigDecimal.ZERO;
        for (Transaction tx : getAllTransactions()) {
            vol = vol.add(tx.getTotalAmountDecimal());
        }
        return MoneyUtil.round(vol).doubleValue();
    }
}
