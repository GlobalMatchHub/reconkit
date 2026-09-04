package dev.sellerkit.reconkit.core.model;

import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.SettlementTerms;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Everything one run needs, handed over in full.
 *
 * <p>The engine does not fetch anything. A matcher that reaches back into a repository
 * mid pass cannot be reasoned about, cannot be replayed, and produces a different answer
 * depending on what else was writing at the time. Reconciliation of a closed business
 * date is a pure function of the rows and the contract, and it is written that way here.
 */
public record ReconInput(
        Long counterpartyId,
        LocalDate businessDate,
        ZoneId zone,
        String currency,
        SettlementTerms terms,
        List<LedgerEntry> ledgerEntries,
        List<StatementEntry> statementEntries) {
}
