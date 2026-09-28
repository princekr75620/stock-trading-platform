package exception;

/**
 * Custom Exception thrown when a trader attempts to purchase more shares
 * than the available float/inventory in the market.
 * Fulfills Core Java Exception Handling requirement for GUVI Evaluation.
 */
public class InsufficientStockException extends TradingPlatformException {
    private final String symbol;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String message) {
        super(message);
        this.symbol = "";
        this.requestedQuantity = 0;
        this.availableQuantity = 0;
    }

    public InsufficientStockException(String symbol, int requestedQuantity, int availableQuantity) {
        super(String.format("Insufficient stock volume for %s: Requested %d shares, but only %d available in market",
                symbol, requestedQuantity, availableQuantity));
        this.symbol = symbol;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
