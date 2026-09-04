package dev.sellerkit.reconkit.domain.enums;

/** Which set of books a row came from. */
public enum EntrySide {
    /** Our own record, produced by the system that took the payment. */
    LEDGER,
    /** The counterparty's statement line. */
    STATEMENT
}
