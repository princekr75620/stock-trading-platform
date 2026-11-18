package util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * High-precision financial arithmetic utility using BigDecimal.
 * Enforces consistent currency scale (2 decimal places, HALF_UP rounding)
 * and overflow/range validation across all trading, fee, and portfolio calculations.
 */
public final class MoneyUtil {
    public static final int CURRENCY_SCALE = 2;
    public static final int AVG_PRICE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Standard regulatory + brokerage fee rate (0.15% = 0.0015). */
    public static final BigDecimal TRADING_FEE_RATE = new BigDecimal("0.0015");
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(CURRENCY_SCALE, ROUNDING);
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final BigDecimal MAX_MONETARY_AMOUNT = new BigDecimal("1000000000.00"); // ₹100 Cr safety bound
    public static final int MAX_ORDER_QUANTITY = 1_000_000;

    private MoneyUtil() {}

    public static BigDecimal of(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Monetary amount must be a finite number.");
        }
        return BigDecimal.valueOf(value).setScale(CURRENCY_SCALE, ROUNDING);
    }

    public static BigDecimal ofExact(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Price value must be a finite number.");
        }
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal round(BigDecimal amount) {
        if (amount == null) return ZERO;
        return amount.setScale(CURRENCY_SCALE, ROUNDING);
    }

    public static BigDecimal calculateSubtotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be positive.");
        }
        if (quantity <= 0 || quantity > MAX_ORDER_QUANTITY) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_ORDER_QUANTITY + ".");
        }
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(CURRENCY_SCALE, ROUNDING);
        if (subtotal.compareTo(MAX_MONETARY_AMOUNT) > 0) {
            throw new IllegalArgumentException("Order value exceeds maximum single-order safety limit.");
        }
        return subtotal;
    }

    public static BigDecimal calculateFee(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return subtotal.multiply(TRADING_FEE_RATE).setScale(CURRENCY_SCALE, ROUNDING);
    }

    public static BigDecimal calculateBuyTotal(BigDecimal unitPrice, int quantity) {
        BigDecimal subtotal = calculateSubtotal(unitPrice, quantity);
        BigDecimal fee = calculateFee(subtotal);
        BigDecimal total = subtotal.add(fee).setScale(CURRENCY_SCALE, ROUNDING);
        if (total.compareTo(MAX_MONETARY_AMOUNT) > 0) {
            throw new IllegalArgumentException("Total buy order cost exceeds maximum safety limit.");
        }
        return total;
    }

    public static BigDecimal calculateSellNetProceeds(BigDecimal unitPrice, int quantity) {
        BigDecimal subtotal = calculateSubtotal(unitPrice, quantity);
        BigDecimal fee = calculateFee(subtotal);
        BigDecimal net = subtotal.subtract(fee).setScale(CURRENCY_SCALE, ROUNDING);
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return net;
    }

    public static BigDecimal calculateWeightedAveragePrice(int existingQty, BigDecimal existingAvg,
                                                           int addedQty, BigDecimal buyPrice) {
        int totalQty = existingQty + addedQty;
        if (totalQty <= 0) return ZERO;
        BigDecimal existingCost = existingAvg.multiply(BigDecimal.valueOf(existingQty));
        BigDecimal addedCost = buyPrice.multiply(BigDecimal.valueOf(addedQty));
        return existingCost.add(addedCost)
                .divide(BigDecimal.valueOf(totalQty), CURRENCY_SCALE, ROUNDING);
    }
}
