package dev.sellerkit.reconkit.domain.money;

import java.util.Objects;

/**
 * Money held as a whole number of minor units.
 *
 * <p>There is no {@code double} and no {@code BigDecimal} anywhere in the money
 * path. Floating point loses won at scale, and BigDecimal survives arithmetic but
 * not the round trip through the database: a scale set in one place and read back
 * in another is how a fee comparison starts reporting differences that are not
 * there. A long of minor units compares exactly, every time.
 *
 * <p>KRW has zero decimal places, so one minor unit is one won. USD has two, so one
 * minor unit is one cent. The scale lives in {@link CurrencyScale}, not in the value.
 */
public record Money(long minorUnits, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3 letter code: " + currency);
        }
        currency = currency.toUpperCase();
    }

    public static Money of(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }

    public static Money krw(long won) {
        return new Money(won, "KRW");
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public Money abs() {
        return minorUnits < 0 ? negate() : this;
    }

    /**
     * Multiplies by a rate expressed in basis points and rounds half up.
     *
     * <p>Fee schedules are quoted as percentages, but a percentage times an integer
     * amount is not an integer. Every counterparty rounds, and the rounding rule is
     * part of the contract, not an implementation detail, so it is stated here and
     * asserted in tests rather than left to whatever the arithmetic happens to do.
     */
    public Money timesBasisPoints(int basisPoints) {
        long product = Math.multiplyExact(minorUnits, (long) basisPoints);
        long rounded = (product + (product >= 0 ? 5_000L : -5_000L)) / 10_000L;
        return new Money(rounded, currency);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    @Override
    public String toString() {
        return CurrencyScale.format(minorUnits, currency) + " " + currency;
    }
}
