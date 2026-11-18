package dao;

import model.Stock;
import util.DBConnection;
import util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Data Access Object for Indian Stock entities.
 * Supports SQLite and MySQL dialects, BigDecimal monetary reads/writes,
 * pessimistic row locking on MySQL, and atomic stock float updates.
 */
public class StockDAO implements StockDAOInterface {

    private static final String SELECT_FIELDS =
            "id, symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded";

    @Override
    public Stock findBySymbol(String symbol) throws SQLException {
        if (symbol == null) return null;
        try (Connection conn = DBConnection.getConnection()) {
            return findBySymbol(conn, symbol);
        }
    }

    @Override
    public Stock findBySymbol(Connection conn, String symbol) throws SQLException {
        return findBySymbolInternal(conn, symbol, false);
    }

    @Override
    public Stock findBySymbolForUpdate(Connection conn, String symbol) throws SQLException {
        return findBySymbolInternal(conn, symbol, true);
    }

    private Stock findBySymbolInternal(Connection conn, String symbol, boolean forUpdate) throws SQLException {
        if (symbol == null) return null;
        String sql = "SELECT " + SELECT_FIELDS + " FROM stocks WHERE symbol = ?"
                + (forUpdate && DBConnection.isMySQL() ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol.toUpperCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToStock(rs);
                }
            }
        }
        return null;
    }

    @Override
    public Stock findById(int id) throws SQLException {
        String sql = "SELECT " + SELECT_FIELDS + " FROM stocks WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToStock(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Stock> findAll() throws SQLException {
        List<Stock> list = new ArrayList<>();
        String sql = "SELECT " + SELECT_FIELDS + " FROM stocks ORDER BY symbol ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToStock(rs));
            }
        }
        return list;
    }

    @Override
    public Map<String, Stock> findAllAsMap() throws SQLException {
        Map<String, Stock> map = new HashMap<>();
        for (Stock s : findAll()) {
            map.put(s.getSymbol(), s);
        }
        return map;
    }

    @Override
    public List<Stock> search(String query, int page, int pageSize) throws SQLException {
        List<Stock> list = new ArrayList<>();
        int offset = Math.max(0, (page - 1) * pageSize);

        String sql;
        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (hasQuery) {
            sql = "SELECT " + SELECT_FIELDS + " FROM stocks WHERE symbol LIKE ? OR company_name LIKE ? ORDER BY symbol ASC LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT " + SELECT_FIELDS + " FROM stocks ORDER BY symbol ASC LIMIT ? OFFSET ?";
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasQuery) {
                String pattern = "%" + query.trim().toUpperCase() + "%";
                String namePattern = "%" + query.trim() + "%";
                ps.setString(1, pattern);
                ps.setString(2, namePattern);
                ps.setInt(3, pageSize);
                ps.setInt(4, offset);
            } else {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToStock(rs));
                }
            }
        }
        return list;
    }

    @Override
    public boolean insert(Stock stock) throws SQLException {
        String sql = "INSERT INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            populateStockPreparedStatement(ps, stock);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet gk = ps.getGeneratedKeys()) {
                    if (gk.next()) {
                        stock.setId(gk.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public int insertBatch(List<Stock> stocks) throws SQLException {
        if (stocks == null || stocks.isEmpty()) return 0;
        String sql;
        if (DBConnection.isMySQL()) {
            sql = "INSERT INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE company_name=VALUES(company_name), exchange=VALUES(exchange), price=VALUES(price), " +
                  "available_quantity=VALUES(available_quantity), status=VALUES(status), previous_price=VALUES(previous_price), " +
                  "day_high=VALUES(day_high), day_low=VALUES(day_low), volume_traded=VALUES(volume_traded)";
        } else {
            sql = "INSERT INTO stocks (symbol, company_name, exchange, price, available_quantity, status, previous_price, day_high, day_low, volume_traded) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON CONFLICT(symbol) DO UPDATE SET company_name=excluded.company_name, exchange=excluded.exchange, price=excluded.price, " +
                  "available_quantity=excluded.available_quantity, status=excluded.status, previous_price=excluded.previous_price, " +
                  "day_high=excluded.day_high, day_low=excluded.day_low, volume_traded=excluded.volume_traded";
        }

        try (Connection conn = DBConnection.getConnection()) {
            boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Stock stock : stocks) {
                    populateStockPreparedStatement(ps, stock);
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                conn.commit();
                int count = 0;
                for (int r : results) {
                    if (r >= 0 || r == Statement.SUCCESS_NO_INFO) count++;
                }
                return count;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        }
    }

    private void populateStockPreparedStatement(PreparedStatement ps, Stock stock) throws SQLException {
        ps.setString(1, stock.getSymbol().toUpperCase().trim());
        ps.setString(2, stock.getName());
        ps.setString(3, stock.getExchange());
        ps.setBigDecimal(4, MoneyUtil.of(stock.getCurrentPrice()));
        ps.setInt(5, stock.getAvailableQuantity());
        ps.setString(6, stock.getStatus());
        double prev = stock.getPreviousPrice() > 0 ? stock.getPreviousPrice() : stock.getCurrentPrice();
        double high = stock.getDayHigh() > 0 ? stock.getDayHigh() : stock.getCurrentPrice();
        double low = stock.getDayLow() > 0 ? stock.getDayLow() : stock.getCurrentPrice();
        ps.setBigDecimal(7, MoneyUtil.of(prev));
        ps.setBigDecimal(8, MoneyUtil.of(high));
        ps.setBigDecimal(9, MoneyUtil.of(low));
        ps.setLong(10, stock.getVolumeTraded());
    }

    @Override
    public boolean update(Stock stock) throws SQLException {
        String sql = "UPDATE stocks SET company_name = ?, exchange = ?, price = ?, available_quantity = ?, status = ?, previous_price = ?, day_high = ?, day_low = ?, volume_traded = ? WHERE symbol = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stock.getName());
            ps.setString(2, stock.getExchange());
            ps.setBigDecimal(3, MoneyUtil.of(stock.getCurrentPrice()));
            ps.setInt(4, stock.getAvailableQuantity());
            ps.setString(5, stock.getStatus());
            ps.setBigDecimal(6, MoneyUtil.of(stock.getPreviousPrice()));
            ps.setBigDecimal(7, MoneyUtil.of(stock.getDayHigh()));
            ps.setBigDecimal(8, MoneyUtil.of(stock.getDayLow()));
            ps.setLong(9, stock.getVolumeTraded());
            ps.setString(10, stock.getSymbol().toUpperCase().trim());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateQuantityAndVolume(Connection conn, String symbol, int quantityDelta, boolean isBuy) throws SQLException {
        String sql = isBuy ?
                "UPDATE stocks SET available_quantity = available_quantity - ?, volume_traded = volume_traded + ? WHERE symbol = ? AND status = 'ACTIVE' AND available_quantity >= ?" :
                "UPDATE stocks SET available_quantity = available_quantity + ?, volume_traded = volume_traded + ? WHERE symbol = ? AND status = 'ACTIVE'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantityDelta);
            ps.setInt(2, quantityDelta);
            ps.setString(3, symbol.toUpperCase().trim());
            if (isBuy) {
                ps.setInt(4, quantityDelta);
            }
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(String symbol) throws SQLException {
        String sql = "DELETE FROM stocks WHERE symbol = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol.toUpperCase().trim());
            return ps.executeUpdate() > 0;
        }
    }

    private Stock mapRowToStock(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String symbol = rs.getString("symbol");
        String name = rs.getString("company_name");
        String exchange = "NSE";
        try {
            exchange = rs.getString("exchange");
            if (exchange == null) exchange = "NSE";
        } catch (SQLException ignored) {}

        BigDecimal priceDec = rs.getBigDecimal("price");
        double price = priceDec != null ? MoneyUtil.round(priceDec).doubleValue() : 0.0;
        int availableQty = rs.getInt("available_quantity");
        String status = "ACTIVE";
        try {
            status = rs.getString("status");
            if (status == null) status = "ACTIVE";
        } catch (SQLException ignored) {}

        BigDecimal prevDec = rs.getBigDecimal("previous_price");
        BigDecimal highDec = rs.getBigDecimal("day_high");
        BigDecimal lowDec = rs.getBigDecimal("day_low");
        double prevPrice = prevDec != null ? MoneyUtil.round(prevDec).doubleValue() : price;
        double dayHigh = (highDec != null && highDec.doubleValue() > 0) ? MoneyUtil.round(highDec).doubleValue() : price;
        double dayLow = (lowDec != null && lowDec.doubleValue() > 0) ? MoneyUtil.round(lowDec).doubleValue() : price;
        long volume = rs.getLong("volume_traded");

        Stock stock = new Stock(id, symbol, name, exchange, price, availableQty, status);
        stock.setPreviousPrice(prevPrice);
        stock.setDayHigh(dayHigh);
        stock.setDayLow(dayLow);
        stock.setVolumeTraded(volume);
        return stock;
    }
}
