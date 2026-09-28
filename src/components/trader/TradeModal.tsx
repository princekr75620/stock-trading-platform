import React, { useState, useEffect } from 'react';
import { Stock, Portfolio, User } from '../../types';
import { api } from '../../services/api';

interface TradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  stock: Stock | null;
  initialType?: 'BUY' | 'SELL';
  currentUser: User;
  portfolio: Portfolio | null;
  onTradeCompleted: () => void;
}

export const TradeModal: React.FC<TradeModalProps> = ({
  isOpen,
  onClose,
  stock,
  initialType = 'BUY',
  currentUser,
  portfolio,
  onTradeCompleted,
}) => {
  const [tradeType, setTradeType] = useState<'BUY' | 'SELL'>(initialType);
  const [quantity, setQuantity] = useState<number>(10);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    setTradeType(initialType);
    setError(null);
    setSuccessMsg(null);
    setQuantity(10);
  }, [stock, initialType, isOpen]);

  if (!isOpen || !stock) return null;

  const currentPrice = stock.currentPrice || 0.01;
  const cashBalance = portfolio?.cashBalance ?? currentUser.cashBalance ?? 0;
  const ownedHolding = portfolio?.holdings?.find(h => h.symbol === stock.symbol);
  const ownedQuantity = ownedHolding ? ownedHolding.quantity : 0;

  const subtotal = quantity * currentPrice;
  const estimatedFee = subtotal * 0.0015; // 0.15% fee
  const totalCost = tradeType === 'BUY' ? subtotal + estimatedFee : subtotal - estimatedFee;

  const canAffordBuy = cashBalance >= totalCost && quantity > 0;
  const canAffordSell = ownedQuantity >= quantity && quantity > 0;
  const isTradeValid = tradeType === 'BUY' ? canAffordBuy : canAffordSell;

  const maxBuyQty = Math.max(0, Math.floor(cashBalance / (currentPrice * 1.0015)));
  const maxSellQty = Math.max(0, ownedQuantity);

  const handleExecuteTrade = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isTradeValid) return;
    setLoading(true);
    setError(null);

    try {
      if (tradeType === 'BUY') {
        const res = await api.executeBuy(currentUser.userId, stock.symbol, quantity);
        setSuccessMsg(res.message || `Successfully purchased ${quantity} shares of ${stock.symbol}`);
      } else {
        const res = await api.executeSell(currentUser.userId, stock.symbol, quantity);
        setSuccessMsg(res.message || `Successfully sold ${quantity} shares of ${stock.symbol}`);
      }
      onTradeCompleted();
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setError(err.message || 'Transaction could not be completed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
      <div className="bg-white border border-gray-300 rounded-lg shadow-md max-w-md w-full p-6 relative">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-gray-200 mb-4">
          <div>
            <h3 className="text-lg font-bold text-gray-900">
              {tradeType === 'BUY' ? 'Buy Stock' : 'Sell Stock'} - {stock.symbol}
            </h3>
            <p className="text-xs text-gray-500">{stock.name}</p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-lg font-bold px-2 py-0.5 rounded"
          >
            &times;
          </button>
        </div>

        {/* Trade Type Selector */}
        <div className="grid grid-cols-2 gap-2 mb-4">
          <button
            type="button"
            onClick={() => { setTradeType('BUY'); setError(null); }}
            className={`py-2 text-sm font-semibold rounded border transition ${
              tradeType === 'BUY'
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-gray-50 text-gray-700 border-gray-300 hover:bg-gray-100'
            }`}
          >
            BUY
          </button>
          <button
            type="button"
            onClick={() => { setTradeType('SELL'); setError(null); }}
            className={`py-2 text-sm font-semibold rounded border transition ${
              tradeType === 'SELL'
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-gray-50 text-gray-700 border-gray-300 hover:bg-gray-100'
            }`}
          >
            SELL
          </button>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded">
            {error}
          </div>
        )}

        {successMsg && (
          <div className="mb-4 p-3 bg-green-50 border border-green-200 text-green-700 text-xs rounded font-medium">
            {successMsg}
          </div>
        )}

        <form onSubmit={handleExecuteTrade} className="space-y-4">
          {/* Key Info row */}
          <div className="bg-gray-50 p-3 rounded border border-gray-200 text-xs space-y-1.5">
            <div className="flex justify-between">
              <span className="text-gray-600">Current Market Price:</span>
              <span className="font-semibold text-gray-900">₹{currentPrice.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Cash Balance:</span>
              <span className="font-semibold text-gray-900">₹{cashBalance.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">You Currently Own:</span>
              <span className="font-semibold text-gray-900">{ownedQuantity} shares</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Max You Can {tradeType}:</span>
              <span className="font-semibold text-gray-900">
                {tradeType === 'BUY' ? maxBuyQty : maxSellQty} shares
              </span>
            </div>
          </div>

          {/* Quantity input */}
          <div>
            <div className="flex justify-between items-center mb-1">
              <label className="text-xs font-medium text-gray-700">Quantity of Shares</label>
              <button
                type="button"
                onClick={() => setQuantity(tradeType === 'BUY' ? maxBuyQty : maxSellQty)}
                className="text-xs text-blue-600 hover:underline"
              >
                Set Max
              </button>
            </div>
            <input
              type="number"
              min="1"
              max={tradeType === 'BUY' ? maxBuyQty : maxSellQty}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 0))}
              className="w-full px-3 py-2 border border-gray-300 rounded text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-blue-500"
              required
            />
          </div>

          {/* Order Summary */}
          <div className="border-t border-gray-200 pt-3 text-xs space-y-1">
            <div className="flex justify-between text-gray-600">
              <span>Estimated Subtotal:</span>
              <span>₹{subtotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Platform Trading Fee (0.15%):</span>
              <span>₹{estimatedFee.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-sm font-bold text-gray-900 pt-1 border-t border-gray-100">
              <span>Total Estimated {tradeType === 'BUY' ? 'Cost' : 'Proceeds'}:</span>
              <span className="text-blue-600">₹{totalCost.toFixed(2)}</span>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex justify-end space-x-3 pt-3 border-t border-gray-200">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded text-sm text-gray-700 hover:bg-gray-50 font-medium"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !isTradeValid}
              className={`px-5 py-2 rounded text-sm font-semibold text-white transition disabled:opacity-50 ${
                tradeType === 'BUY' ? 'bg-blue-600 hover:bg-blue-700' : 'bg-red-600 hover:bg-red-700'
              }`}
            >
              {loading ? 'Processing...' : `Confirm ${tradeType}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
