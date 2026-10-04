package com.app.winterarc.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-floating-point quantity expressed in thousandths of a unit.
 *
 * <p>21.1 km is stored as {@code new Amount(21_100)}. All comparisons (e.g. "was the target met?")
 * are exact integer comparisons, so floating-point rounding can never decide whether a goal is
 * complete.
 */
public record Amount(long milli) implements Comparable<Amount>, java.io.Serializable {

    public static final long SCALE = 1_000L;
    public static final Amount ZERO = new Amount(0);

    public static Amount whole(long value) {
        return new Amount(Math.multiplyExact(value, SCALE));
    }

    /** Builds an amount from picker columns: whole units plus tenths (0..9). */
    public static Amount of(int whole, int tenths) {
        if (tenths < 0 || tenths > 9) throw new IllegalArgumentException("tenths must be 0..9");
        return new Amount(whole * SCALE + tenths * 100L);
    }

    /** Parses input such as "21.1" or "5". Returns null for invalid, negative or >3-decimal input. */
    @Nullable
    public static Amount parse(@Nullable String text) {
        if (text == null) return null;
        String normalized = text.trim().replace(',', '.');
        if (normalized.isEmpty()) return null;
        try {
            BigDecimal decimal = new BigDecimal(normalized);
            if (decimal.signum() < 0) return null;
            BigDecimal scaled = decimal.setScale(3, RoundingMode.UNNECESSARY);
            return new Amount(scaled.movePointRight(3).longValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    public static Amount min(Amount a, Amount b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static Amount max(Amount a, Amount b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public Amount plus(Amount other) {
        return new Amount(Math.addExact(milli, other.milli));
    }

    public Amount minus(Amount other) {
        return new Amount(Math.subtractExact(milli, other.milli));
    }

    public boolean isZero() {
        return milli == 0L;
    }

    public boolean isPositive() {
        return milli > 0L;
    }

    public boolean isWhole() {
        return milli % SCALE == 0L;
    }

    public boolean greaterThan(Amount other) {
        return milli > other.milli;
    }

    public boolean atLeast(Amount other) {
        return milli >= other.milli;
    }

    public boolean lessThan(Amount other) {
        return milli < other.milli;
    }

    /** Whole-unit part, e.g. 5 for 5.5. */
    public long wholePart() {
        return milli / SCALE;
    }

    /** First decimal digit, e.g. 5 for 5.5 (truncated). */
    public int tenthsPart() {
        return (int) ((milli % SCALE) / 100);
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros();
    }

    /** Only for display and charts, never for equality decisions. */
    public double toDouble() {
        return milli / (double) SCALE;
    }

    /** Ratio of this amount to {@code other}, or 0 when {@code other} is not positive. */
    public float ratioTo(Amount other) {
        if (other.milli <= 0L) return 0f;
        return (float) (milli / (double) other.milli);
    }

    @Override
    public int compareTo(Amount other) {
        return Long.compare(milli, other.milli);
    }

    @NonNull
    @Override
    public String toString() {
        return toBigDecimal().toPlainString();
    }
}
