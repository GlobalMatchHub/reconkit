package dev.sellerkit.reconkit.domain.policy;

import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.money.Money;

/**
 * Works out what the counterparty should have withheld.
 *
 * <p>Order matters and is fixed here: rate first, flat fee second, tax on the sum of the
 * two. Applying the flat fee before the rate, or taxing only the rate portion, moves the
 * result by a few units per transaction, which is invisible on one row and is a
 * five figure argument at the end of the month. The same routine computes the expected
 * fee for reconciliation and the billed fee on the settlement statement, so the number
 * the engine complains about is the number the statement pays.
 */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    /** Commission before tax. */
    public static Money commission(Money gross, SettlementTerms terms) {
        Money absolute = gross.abs();
        Money rated = absolute.timesBasisPoints(terms.getFeeRateBasisPoints());
        Money flat = Money.of(terms.getFixedFeeMinorUnits(), gross.currency());
        Money total = rated.plus(flat);
        return gross.isNegative() ? total.negate() : total;
    }

    /** Tax charged on the commission itself. */
    public static Money commissionTax(Money gross, SettlementTerms terms) {
        return commission(gross, terms).timesBasisPoints(terms.getFeeVatBasisPoints());
    }

    /** Commission including tax. This is what a statement line actually withholds. */
    public static Money totalFee(Money gross, SettlementTerms terms) {
        return commission(gross, terms).plus(commissionTax(gross, terms));
    }

    public static Money netOf(Money gross, SettlementTerms terms) {
        return gross.minus(totalFee(gross, terms));
    }
}
