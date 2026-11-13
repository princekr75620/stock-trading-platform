package service;

import dao.*;
import exception.*;
import model.*;
import util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative Core Trading Engine executing equity BUY and SELL orders with ACID JDBC transactions.
 * Orchestrates UserDAO, StockDAO, PortfolioDAO, and TransactionDAO over a shared SQL Connection.
 * All financial records and trade history queries read directly from relational database persistence.
 */
public class TradingService implements ITradingService, TradingServiceInterface {

    private final IMarketUpdateService marketUpdateService;
    private final IPortfolioService portfolioService;
    private final INotificationService notificationService;
    private final IUserService userService;

    // DAO Layer components
    private final UserDAO userDAO;
    private final StockDAO stockDAO;
    private final PortfolioDAO portfolioDAO;
    private final TransactionDAO transactionDAO;

    public TradingService(IMarketUpdateService marketUpdateService,
                          IPortfolioService portfolioService,
                          INotificationService notificationService,
                          IUserService userService) {
        this.marketUpdateService = marketUpdateService;
        this.portfolioService = portfolioService;
        this.notificationService = notificationService;
        this.userService = userService;

        this.userDAO = new UserDAO();
        this.stockDAO = new StockDAO();
        this.portfolioDAO = new PortfolioDAO();
        this.transactionDAO = new TransactionDAO();

        // Ensure relational database schema exists
        DBConnection.initializeDatabase();
    }

    /**
     * Backward-compatible constructor; ignores legacy initialTrades so JDBC remains authoritative.
     */
    public TradingService(IMarketUpdateService marketUpdateService,
                          IPortfolioService portfolioService,
                          INotificationService notificationService,
                          IUserService userService,
                          List<Trade> ignoredInitialTrades) {
        this(marketUpdateService, portfolioService, notificationService, userService);
    }

    /**
     * Executes BUY order with atomic JDBC transaction management.
     */
    @Override
    public synchronized Trade executeBuy(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientStockQuantityException {
        try {
            Transaction tx = executeBuyTransaction(traderId, symbol, quantity);
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

    /**
     * Core BUY transaction with full ACID rollback on any failure.
     */
    public synchronized Transaction executeBuyTransaction(String traderId, String symbol, int quantity)
            throws InsufficientBalanceException, InsufficientStockException,
                   InvalidStockException, InvalidTransactionException, SQLException {

        if (traderId == null || traderId.trim().isEmpty()) {
            throw new InvalidTransactionException("Authenticated trader ID is required.");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidTransactionException("Stock symbol cannot be empty.");
        }
        if (quantity <= 0) {
            throw new InvalidTransactionException("Purchase quantity must be greater than zero.");
        }

        String uid = traderId.trim();
        String sym = symbol.toUpperCase().trim();
        Connection conn = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // BEGIN ACID JDBC TRANSACTION

            // Step 1: Load authoritative stock quote & float from DB within transaction
            Stock stock = stockDAO.findBySymbol(conn, sym);
            if (stock == null) {
                throw new InvalidStockException("Stock symbol '" + sym + "' was not found on exchange.", sym);
            }
            if (!"ACTIVE".equalsIgnoreCase(stock.getStatus())) {
                throw new InvalidTransactionException("Trading is currently halted for " + sym + ".");
            }
            if (stock.getAvailableQuantity() < quantity) {
                throw new InsufficientStockException(sym, quantity, stock.getAvailableQuantity());
            }

            // Step 2: Load authoritative user balance from DB within transaction
            User user = userDAO.findById(conn, uid);
            if (user == null) {
                throw new InvalidTransactionException("Trader account not found in database: " + uid);
            }

            double currentPrice = stock.getCurrentPrice();
            double totalAmount = Math.round((currentPrice * quantity) * 100.0) / 100.0;
            double currentBalance = user.getBalance();

            if (currentBalance < totalAmount) {
                throw new InsufficientBalanceException(totalAmount, currentBalance);
            }

            // Step 3: Deduct balance in DB
            double newBalance = Math.round((currentBalance - totalAmount) * 100.0) / 100.0;
            if (!userDAO.updateBalance(conn, user.getUserId(), newBalance)) {
                throw new SQLException("Failed to update cash balance for trader: " + user.getUserId());
            }

            // Step 4: Atomically decrement stock float & increment volume in DB
            if (!stockDAO.updateQuantityAndVolume(conn, sym, quantity, true)) {
                throw new InsufficientStockException(sym, quantity, stock.getAvailableQuantity());
            }

            // Step 5: Upsert portfolio holdings & weighted average price in DB
            if (!portfolioDAO.recordBuy(conn, user.getUserId(), stock.getId(), sym, quantity, currentPrice)) {
                throw new SQLException("Failed to update portfolio holdings for " + sym);
            }

            // Step 6: Insert transaction audit record in DB
            Transaction tx = new Transaction(
                    user.getUserId(),
                    stock.getId(),
                    sym,
                    "BUY",
                    quantity,
                    currentPrice,
                    totalAmount
            );
            if (!transactionDAO.insert(conn, tx)) {
                throw new SQLException("Failed to record transaction ledger entry.");
            }

            // Step 7: Commit ACID transaction
            conn.commit();

            if (portfolioService != null) {
                portfolioService.updateOnBuy(user.getUserId(), sym, quantity, currentPrice);
            }

            if (notificationService != null) {
                notificationService.sendNotification(new Notification(
                        "NOTIF-" + (System.currentTimeMillis() % 100000),
                        user.getUserId(),
                        "Order Executed: BUY " + sym,
                        String.format("Purchased %d shares of %s at ₹%.2f. Total: ₹%.2f. Cash remaining: ₹%.2f.",
                                quantity, sym, currentPrice, totalAmount, newBalance),
                        Notification.NotificationType.TRADE_BUY
                ));
            }

            return tx;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    System.err.println("[TradingService] Rolling back BUY transaction due to: " + e.getMessage());
                    conn.rollback();
                } catch (SQLException rbEx) {
                    System.err.println("[TradingService] Failed to rollback transaction: " + rbEx.getMessage());
                }
            }
            if (e instanceof InsufficientBalanceException) throw (InsufficientBalanceException) e;
            if (e instanceof InsufficientStockException) throw (InsufficientStockException) e;
            if (e instanceof InvalidStockException) throw (InvalidStockException) e;
            if (e instanceof InvalidTransactionException) throw (InvalidTransactionException) e;
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Transaction execution failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Executes SELL order with atomic JDBC transaction management.
     */
    @Override
    public synchronized Trade executeSell(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientHoldingsException {
        try {
            Transaction tx = executeSellTransaction(traderId, symbol, quantity);
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

    /**
     * Core SELL transaction with full ACID rollback on any failure.
     */
    public synchronized Transaction executeSellTransaction(String traderId, String symbol, int quantity)
            throws InsufficientHoldingsException, InvalidStockException,
                   InvalidTransactionException, SQLException {

        if (traderId == null || traderId.trim().isEmpty()) {
            throw new InvalidTransactionException("Authenticated trader ID is required.");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidTransactionException("Stock symbol cannot be empty.");
        }
        if (quantity <= 0) {
            throw new InvalidTransactionException("Sell quantity must be greater than zero.");
        }

        String uid = traderId.trim();
        String sym = symbol.toUpperCase().trim();
        Connection conn = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // BEGIN ACID JDBC TRANSACTION

            // Step 1: Load authoritative stock from DB
            Stock stock = stockDAO.findBySymbol(conn, sym);
            if (stock == null) {
                throw new InvalidStockException("Stock symbol '" + sym + "' was not found on exchange.", sym);
            }

            // Step 2: Load authoritative user from DB
            User user = userDAO.findById(conn, uid);
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

            double currentPrice = stock.getCurrentPrice();
            double totalProceeds = Math.round((currentPrice * quantity) * 100.0) / 100.0;

            // Step 4: Credit cash balance in DB
            double newBalance = Math.round((user.getBalance() + totalProceeds) * 100.0) / 100.0;
            if (!userDAO.updateBalance(conn, user.getUserId(), newBalance)) {
                throw new SQLException("Failed to update cash balance for trader: " + user.getUserId());
            }

            // Step 5: Decrease/remove portfolio holding in DB
            if (!portfolioDAO.recordSell(conn, user.getUserId(), sym, quantity)) {
                throw new SQLException("Failed to update portfolio holding for " + sym);
            }

            // Step 6: Return shares to market float in DB
            if (!stockDAO.updateQuantityAndVolume(conn, sym, quantity, false)) {
                throw new SQLException("Failed to update stock inventory for " + sym);
            }

            // Step 7: Insert transaction audit record in DB
            Transaction tx = new Transaction(
                    user.getUserId(),
                    stock.getId(),
                    sym,
                    "SELL",
                    quantity,
                    currentPrice,
                    totalProceeds
            );
            if (!transactionDAO.insert(conn, tx)) {
                throw new SQLException("Failed to record transaction ledger entry.");
            }

            // Step 8: Commit ACID transaction
            conn.commit();

            double realizedGain = 0.0;
            if (portfolioService != null) {
                realizedGain = portfolioService.updateOnSell(user.getUserId(), sym, quantity, currentPrice);
            }

            if (notificationService != null) {
                notificationService.sendNotification(new Notification(
                        "NOTIF-" + (System.currentTimeMillis() % 100000),
                        user.getUserId(),
                        "Order Executed: SELL " + sym,
                        String.format("Sold %d shares of %s at ₹%.2f. Proceeds: ₹%.2f. Balance: ₹%.2f.",
                                quantity, sym, currentPrice, totalProceeds, newBalance),
                        Notification.NotificationType.TRADE_SELL
                ));
            }

            return tx;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    System.err.println("[TradingService] Rolling back SELL transaction due to: " + e.getMessage());
                    conn.rollback();
                } catch (SQLException rbEx) {
                    System.err.println("[TradingService] Failed to rollback transaction: " + rbEx.getMessage());
                }
            }
            if (e instanceof InsufficientHoldingsException) throw (InsufficientHoldingsException) e;
            if (e instanceof InvalidStockException) throw (InvalidStockException) e;
            if (e instanceof InvalidTransactionException) throw (InvalidTransactionException) e;
            if (e instanceof SQLException) throw (SQLException) e;
            throw new SQLException("Sell order execution failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
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
        double vol = 0.0;
        for (Transaction tx : getAllTransactions()) {
            vol += tx.getTotalAmount();
        }
        return Math.round(vol * 100.0) / 100.0;
    }
}
