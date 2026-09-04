package dev.sellerkit.reconkit.ingest;

import dev.sellerkit.reconkit.domain.money.CurrencyScale;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Text to minor units.
 *
 * <p>{@link BigDecimal} appears here and nowhere else. Parsing is the one place a decimal
 * type earns its keep, because the file says "12,345.67" and something has to shift the
 * point without losing a cent. The moment the value is exact it becomes a long, and every
 * comparison downstream is integer arithmetic.
 *
 * <p>A value with more decimal places than the currency has is rejected, not rounded.
 * Silently rounding a third decimal place means accepting a file that is not in the
 * currency it claims to be.
 */
public final class AmountParser {

    private AmountParser() {
    }

    public static long toMinorUnits(String raw, String currency) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String cleaned = raw.trim()
                .replace(",", "")
                .replace("￦", "")
                .replace("₩", "")
                .replace(" ", "");
        boolean parenthesisNegative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (parenthesisNegative) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("not an amount: " + raw);
        }
        if (parenthesisNegative) {
            value = value.negate();
        }
        int exponent = CurrencyScale.exponentOf(currency);
        if (value.scale() > exponent) {
            BigDecimal stripped = value.stripTrailingZeros();
            if (stripped.scale() > exponent) {
                throw new IllegalArgumentException(
                        "%s has more decimal places than %s allows".formatted(raw, currency));
            }
            value = stripped;
        }
        return value.movePointRight(exponent).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
