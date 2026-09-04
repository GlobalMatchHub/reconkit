package dev.sellerkit.reconkit.domain.money;

import java.util.Map;

/** Minor unit exponents. Kept out of {@link Money} so the value stays a plain long. */
public final class CurrencyScale {

    private static final Map<String, Integer> EXPONENTS = Map.of(
            "KRW", 0,
            "JPY", 0,
            "USD", 2,
            "EUR", 2,
            "GBP", 2,
            "CNY", 2
    );

    private CurrencyScale() {
    }

    public static int exponentOf(String currency) {
        Integer exponent = EXPONENTS.get(currency.toUpperCase());
        if (exponent == null) {
            throw new IllegalArgumentException("unknown currency: " + currency);
        }
        return exponent;
    }

    public static String format(long minorUnits, String currency) {
        int exponent = exponentOf(currency);
        if (exponent == 0) {
            return String.format("%,d", minorUnits);
        }
        long divisor = (long) Math.pow(10, exponent);
        long whole = minorUnits / divisor;
        long fraction = Math.abs(minorUnits % divisor);
        return String.format("%,d.%0" + exponent + "d", whole, fraction);
    }
}
