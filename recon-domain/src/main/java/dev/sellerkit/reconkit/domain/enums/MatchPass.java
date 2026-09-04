package dev.sellerkit.reconkit.domain.enums;

/**
 * Passes run in declaration order. A row consumed by an earlier pass never
 * reaches a later one, so a cheap exact match always wins over a fuzzy guess.
 */
public enum MatchPass {
    /** Exact match on the counterparty transaction id. */
    A_EXACT_ID,
    /** Composite key: approval number + amount, or order id + amount. */
    B_COMPOSITE_KEY,
    /** Amount within tolerance and timestamp within a window, best-score assignment. */
    C_FUZZY_AMOUNT_TIME,
    /** N ledger rows summing to one statement line, bounded by a grouping key. */
    D_AGGREGATE,
    /** Nothing matched. Handed to the discrepancy classifier. */
    UNMATCHED
}
