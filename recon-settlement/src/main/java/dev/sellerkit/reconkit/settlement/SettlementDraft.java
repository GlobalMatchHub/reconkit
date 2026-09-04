package dev.sellerkit.reconkit.settlement;

import java.time.LocalDate;
import java.util.List;

public record SettlementDraft(
        Long counterpartyId,
        String statementNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payoutDate,
        String currency,
        List<DailyTotals> lines,
        long grossMinor,
        long refundMinor,
        long feeMinor,
        long feeTaxMinor,
        long adjustmentMinor,
        long holdbackMinor,
        long holdbackReleaseMinor,
        long netPayableMinor,
        int transactionCount,
        int openDiscrepancyCount) {
}
