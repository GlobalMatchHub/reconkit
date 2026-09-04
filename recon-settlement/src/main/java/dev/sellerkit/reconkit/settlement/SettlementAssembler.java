package dev.sellerkit.reconkit.settlement;

import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Assembles a statement from a period's daily totals.
 *
 * <p>Two things here are decisions rather than arithmetic.
 *
 * <p>The holdback is taken on the settled net, and the release of an earlier period's
 * holdback is a separate line rather than a reduction of this one. A merchant reading a
 * statement has to be able to see both, and netting them into a single figure makes the
 * month a merchant was held back indistinguishable from the month they were paid back.
 *
 * <p>The count of open differences is carried onto the statement. A period can balance
 * to the won while three transactions are still unexplained, and shipping the statement
 * without saying so is how a dispute starts.
 */
public final class SettlementAssembler {

    public SettlementDraft assemble(Long counterpartyId,
                                    String statementNo,
                                    LocalDate periodStart,
                                    LocalDate periodEnd,
                                    String currency,
                                    SettlementTerms terms,
                                    PayoutCalendar calendar,
                                    Map<LocalDate, DailyTotals> dailyTotals,
                                    long adjustmentMinor,
                                    long holdbackReleaseMinor,
                                    int openDiscrepancyCount) {

        List<DailyTotals> lines = new ArrayList<>(dailyTotals.values());
        lines.sort(Comparator.comparing(DailyTotals::businessDate));

        long gross = 0L;
        long refund = 0L;
        long fee = 0L;
        long feeTax = 0L;
        int transactionCount = 0;
        for (DailyTotals line : lines) {
            gross += line.grossMinor();
            refund += line.refundMinor();
            fee += line.feeMinor();
            feeTax += line.feeTaxMinor();
            transactionCount += line.transactionCount();
        }

        long settledNet = gross - refund - fee - feeTax + adjustmentMinor;
        long holdback = Money.of(Math.max(0L, settledNet), currency)
                .timesBasisPoints(terms.getHoldbackBasisPoints())
                .minorUnits();
        long netPayable = settledNet - holdback + holdbackReleaseMinor;

        return new SettlementDraft(
                counterpartyId,
                statementNo,
                periodStart,
                periodEnd,
                calendar.payoutDateFor(periodEnd, terms),
                currency,
                lines,
                gross,
                refund,
                fee,
                feeTax,
                adjustmentMinor,
                holdback,
                holdbackReleaseMinor,
                netPayable,
                transactionCount,
                openDiscrepancyCount);
    }
}
