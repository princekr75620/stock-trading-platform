package service;

import exception.InsufficientHoldingsException;
import exception.InsufficientStockQuantityException;
import exception.InvalidInputException;
import exception.StockNotFoundException;
import model.Trade;
import model.TradeType;

import java.util.List;

/**
 * Interface defining execution of equity trades and trade history querying.
 */
public interface ITradingService {
    Trade executeBuy(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientStockQuantityException;

    Trade executeSell(String traderId, String symbol, int quantity)
            throws StockNotFoundException, InvalidInputException, InsufficientHoldingsException;

    List<Trade> getAllTrades();
    List<Trade> getTradesForTrader(String traderId);
    int getTotalTradeCount();
    int getBuyTradeCount();
    int getSellTradeCount();
    double getTotalTradingVolume();
}
