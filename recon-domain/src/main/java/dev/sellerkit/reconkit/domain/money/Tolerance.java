package dev.sellerkit.reconkit.domain.money;

/**
 * How far apart two amounts may be before the difference is called real.
 *
 * <p>Two knobs, not one. An absolute floor absorbs single unit rounding, which every
 * counterparty produces on percentage fees. A relative band absorbs the drift that
 * scales with the amount. A card network that rounds a 2.2% fee on a 1,000,000 won
 * transaction is off by a won; the same rule on a 33 won transaction is off by a
 * won too. One threshold cannot cover both without either flooding the queue or
 * hiding real losses.
 *
 * @param absoluteMinorUnits allowed difference in minor units, inclusive
 * @param relativeBasisPoints allowed difference as basis points of the larger side
 */
public record Tolerance(long absoluteMinorUnits, int relativeBasisPoints) {

    public static final Tolerance EXACT = new Tolerance(0L, 0);

    public Tolerance {
        if (absoluteMinorUnits < 0) {
            throw new IllegalArgumentException("absolute tolerance cannot be negative");
        }
        if (relativeBasisPoints < 0) {
            throw new IllegalArgumentException("relative tolerance cannot be negative");
        }
    }

    public boolean accepts(Money left, Money right) {
        left.requireSameCurrency(right);
        long difference = Math.abs(left.minorUnits() - right.minorUnits());
        if (difference <= absoluteMinorUnits) {
            return true;
        }
        long larger = Math.max(Math.abs(left.minorUnits()), Math.abs(right.minorUnits()));
        long allowed = (larger * relativeBasisPoints + 5_000L) / 10_000L;
        return difference <= allowed;
    }
}
