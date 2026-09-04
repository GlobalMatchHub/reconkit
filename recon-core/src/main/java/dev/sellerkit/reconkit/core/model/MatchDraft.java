package dev.sellerkit.reconkit.core.model;

import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import java.util.List;

/**
 * A match the engine decided on, before it has an identity in the database.
 *
 * <p>{@code matchKey} is not a debug string. It is shown to the operator as the reason
 * the two sides were tied together, so it is written for someone who has to defend the
 * number to a counterparty, not for a log file.
 */
public record MatchDraft(
        MatchType type,
        MatchPass pass,
        double confidence,
        String matchKey,
        List<Long> ledgerEntryIds,
        List<Long> statementEntryIds,
        long ledgerGrossMinor,
        long statementGrossMinor,
        long ledgerFeeMinor,
        long statementFeeMinor,
        String currency) {

    public long grossDelta() {
        return ledgerGrossMinor - statementGrossMinor;
    }
}
