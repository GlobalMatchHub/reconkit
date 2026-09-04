package dev.sellerkit.reconkit.core.model;

import dev.sellerkit.reconkit.domain.enums.DiscrepancyType;
import dev.sellerkit.reconkit.domain.enums.Severity;

/**
 * A difference, before persistence. {@code matchDraftIndex} points at the position of
 * the related match in the result list, since neither side has an id yet.
 */
public record DiscrepancyDraft(
        DiscrepancyType type,
        Severity severity,
        long deltaMinor,
        String currency,
        Long ledgerEntryId,
        Long statementEntryId,
        Integer matchDraftIndex,
        String detail) {
}
