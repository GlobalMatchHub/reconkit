package dev.sellerkit.reconkit.settlement;

import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.policy.FeeCalculator;
import java.time.LocalDate;

/**
 * One day's numbers, accumulated transaction by transaction.
 *
 * <p>The fee is computed per transaction and then summed, never by applying the rate to
 * the day's total. Rounding once on a large number is not the same as rounding many times
 * on small ones, and the counterparty rounds per transaction. Taking the shortcut here
 * produces a statement that disagrees with the gateway by a few hundred won a day, every
 * day, for reasons nobody can find.
 */
public final class DailyTotals {

    private final LocalDate businessDate;
    private final String currency;
    private int transactionCount;
    private long gross;
    private long refund;
    private long commission;
    private long commissionTax;

    public DailyTotals(LocalDate businessDate, String currency) {
        this.businessDate = businessDate;
        this.currency = currency;
    }

    public void add(Money signedGross, SettlementTerms terms) {
        transactionCount++;
        if (signedGross.isNegative()) {
            refund += Math.abs(signedGross.minorUnits());
        } else {
            gross += signedGross.minorUnits();
        }
        commission += FeeCalculator.commission(signedGross, terms).minorUnits();
        commissionTax += FeeCalculator.commissionTax(signedGross, terms).minorUnits();
    }

    public LocalDate businessDate() {
        return businessDate;
    }

    public String currency() {
        return currency;
    }

    public int transactionCount() {
        return transactionCount;
    }

    public long grossMinor() {
        return gross;
    }

    public long refundMinor() {
        return refund;
    }

    public long feeMinor() {
        return commission;
    }

    public long feeTaxMinor() {
        return commissionTax;
    }

    public long netMinor() {
        return gross - refund - commission - commissionTax;
    }
}
