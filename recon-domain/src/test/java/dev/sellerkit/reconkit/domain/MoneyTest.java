package dev.sellerkit.reconkit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sellerkit.reconkit.domain.money.CurrencyMismatchException;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.money.Tolerance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("a rate in basis points rounds half up, in both directions")
    void basisPointRounding() {
        // 2.20% of 33,333 is 733.326, and of 33,336 is 733.392
        assertThat(Money.krw(33_333).timesBasisPoints(220)).isEqualTo(Money.krw(733));
        assertThat(Money.krw(33_336).timesBasisPoints(220)).isEqualTo(Money.krw(733));
        // and a refund rounds the same distance the other way rather than towards zero
        assertThat(Money.krw(-33_333).timesBasisPoints(220)).isEqualTo(Money.krw(-733));
    }

    @Test
    @DisplayName("mixing currencies fails loudly instead of adding the numbers")
    void currencyMismatch() {
        assertThatThrownBy(() -> Money.krw(1_000).plus(Money.of(1_000, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("an amount larger than a double can hold exactly still compares exactly")
    void largeAmountsAreExact() {
        Money left = Money.krw(9_007_199_254_740_993L);
        Money right = Money.krw(9_007_199_254_740_992L);
        assertThat(left).isNotEqualTo(right);
        assertThat(left.minus(right)).isEqualTo(Money.krw(1));
        // The same pair as doubles is indistinguishable, which is the reason for the long.
        assertThat((double) left.minorUnits()).isEqualTo((double) right.minorUnits());
    }

    @Test
    @DisplayName("tolerance takes the wider of the absolute floor and the relative band")
    void toleranceUsesBothKnobs() {
        Tolerance tolerance = new Tolerance(1L, 10);

        // small amounts are covered by the absolute floor
        assertThat(tolerance.accepts(Money.krw(33), Money.krw(34))).isTrue();
        assertThat(tolerance.accepts(Money.krw(33), Money.krw(35))).isFalse();

        // large amounts are covered by the relative band: 10bp of 1,000,000 is 1,000
        assertThat(tolerance.accepts(Money.krw(1_000_000), Money.krw(999_000))).isTrue();
        assertThat(tolerance.accepts(Money.krw(1_000_000), Money.krw(998_000))).isFalse();
    }

    @Test
    @DisplayName("a won is formatted without decimals and a dollar with two")
    void formatting() {
        assertThat(Money.krw(1_234_567).toString()).isEqualTo("1,234,567 KRW");
        assertThat(Money.of(1_234_567, "USD").toString()).isEqualTo("12,345.67 USD");
    }
}
