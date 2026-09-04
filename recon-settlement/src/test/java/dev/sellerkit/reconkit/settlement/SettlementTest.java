package dev.sellerkit.reconkit.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sellerkit.reconkit.domain.enums.SettlementCycle;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.policy.FeeCalculator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementTest {

    private static SettlementTerms terms(int rateBp, int vatBp, long flat, int holdbackBp) {
        SettlementTerms terms = new SettlementTerms();
        terms.setFeeRateBasisPoints(rateBp);
        terms.setFeeVatBasisPoints(vatBp);
        terms.setFixedFeeMinorUnits(flat);
        terms.setHoldbackBasisPoints(holdbackBp);
        terms.setCycle(SettlementCycle.DAILY_T_PLUS_N);
        terms.setSettleAfterDays(2);
        return terms;
    }

    @Test
    @DisplayName("the fee is the rate, then the flat charge, then tax on both")
    void feeOrdering() {
        SettlementTerms terms = terms(220, 1_000, 100L, 0);
        Money gross = Money.krw(100_000);

        assertThat(FeeCalculator.commission(gross, terms)).isEqualTo(Money.krw(2_300));
        assertThat(FeeCalculator.commissionTax(gross, terms)).isEqualTo(Money.krw(230));
        assertThat(FeeCalculator.totalFee(gross, terms)).isEqualTo(Money.krw(2_530));
        assertThat(FeeCalculator.netOf(gross, terms)).isEqualTo(Money.krw(97_470));
    }

    @Test
    @DisplayName("a refund gives the fee back, so the withheld amount is negative")
    void refundReversesTheFee() {
        SettlementTerms terms = terms(220, 1_000, 0L, 0);
        assertThat(FeeCalculator.totalFee(Money.krw(-50_000), terms))
                .isEqualTo(FeeCalculator.totalFee(Money.krw(50_000), terms).negate());
    }

    @Test
    @DisplayName("summing per transaction fees is not the same as one fee on the day's total")
    void perTransactionRoundingIsNotTheSameAsRoundingOnce() {
        SettlementTerms terms = terms(220, 0, 0L, 0);
        // 2.20% of 1,025 is 22.55, which the counterparty rounds up to 23 on every one of
        // the day's transactions. Applying the same rate once to the day's total rounds
        // once instead of five hundred times.
        long amount = 1_025L;
        int transactions = 500;
        long perTransaction =
                FeeCalculator.commission(Money.krw(amount), terms).minorUnits() * transactions;
        long onTheTotal = Money.krw(amount * transactions).timesBasisPoints(220).minorUnits();

        assertThat(perTransaction).isEqualTo(11_500L);
        assertThat(onTheTotal).isEqualTo(11_275L);
        // 225 won a day, every day, for a reason nobody can find in the totals.
        assertThat(perTransaction - onTheTotal).isEqualTo(225L);
    }

    @Test
    @DisplayName("a flat per transaction fee is charged per transaction, not once per day")
    void flatFeeIsPerTransaction() {
        SettlementTerms terms = terms(0, 0, 100L, 0);
        long perTransaction = 0L;
        for (int index = 0; index < 500; index++) {
            perTransaction += FeeCalculator.commission(Money.krw(10_000), terms).minorUnits();
        }
        assertThat(perTransaction).isEqualTo(50_000L);
        assertThat(FeeCalculator.commission(Money.krw(5_000_000), terms).minorUnits()).isEqualTo(100L);
    }

    @Test
    @DisplayName("T plus two means two business days, so Friday pays on Tuesday")
    void payoutSkipsWeekends() {
        PayoutCalendar calendar = PayoutCalendar.weekendsOnly();
        LocalDate friday = LocalDate.of(2026, 8, 21);
        assertThat(friday.getDayOfWeek().toString()).isEqualTo("FRIDAY");
        assertThat(calendar.addBusinessDays(friday, 2)).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("a holiday moves the payout further out without changing the count of business days")
    void payoutSkipsHolidays() {
        PayoutCalendar calendar = new PayoutCalendar(Set.of(LocalDate.of(2026, 8, 25)));
        assertThat(calendar.addBusinessDays(LocalDate.of(2026, 8, 21), 2))
                .isEqualTo(LocalDate.of(2026, 8, 26));
    }

    @Test
    @DisplayName("the statement adds up, and the holdback is shown as its own line")
    void statementAddsUp() {
        SettlementTerms terms = terms(220, 1_000, 0L, 300);
        Map<LocalDate, DailyTotals> daily = new LinkedHashMap<>();
        LocalDate start = LocalDate.of(2026, 8, 17);
        for (int day = 0; day < 3; day++) {
            DailyTotals totals = new DailyTotals(start.plusDays(day), "KRW");
            totals.add(Money.krw(100_000), terms);
            totals.add(Money.krw(-20_000), terms);
            daily.put(start.plusDays(day), totals);
        }

        SettlementDraft draft = new SettlementAssembler().assemble(
                1L, "ST-1", start, start.plusDays(2), "KRW", terms,
                PayoutCalendar.weekendsOnly(), daily, 0L, 5_000L, 2);

        assertThat(draft.grossMinor()).isEqualTo(300_000L);
        assertThat(draft.refundMinor()).isEqualTo(60_000L);
        long settledNet = draft.grossMinor() - draft.refundMinor() - draft.feeMinor() - draft.feeTaxMinor();
        assertThat(draft.holdbackMinor()).isEqualTo(Money.krw(settledNet).timesBasisPoints(300).minorUnits());
        assertThat(draft.netPayableMinor())
                .isEqualTo(settledNet - draft.holdbackMinor() + draft.holdbackReleaseMinor());
        assertThat(draft.openDiscrepancyCount()).isEqualTo(2);
        assertThat(draft.transactionCount()).isEqualTo(6);
    }
}
