package dev.sellerkit.reconkit.domain.enums;

public enum DiscrepancyType {
    /** We booked it, the counterparty never reported it. */
    MISSING_IN_STATEMENT,
    /** The counterparty reported it, we have no record. */
    MISSING_IN_LEDGER,
    /** The same transaction id appears more than once on the statement. */
    DUPLICATE_STATEMENT,
    /** The same transaction id appears more than once in our ledger. */
    DUPLICATE_LEDGER,
    /** Matched, but the gross amounts differ beyond tolerance. */
    AMOUNT_MISMATCH,
    /** Matched, but the fee the counterparty withheld is not what the contract says. */
    FEE_MISMATCH,
    /** Matched against a different business date than ours. */
    LATE_POSTING,
    /** Matched, but one side says refunded and the other says paid. */
    STATUS_MISMATCH,
    /** Matched, but the currencies differ. */
    CURRENCY_MISMATCH
}
