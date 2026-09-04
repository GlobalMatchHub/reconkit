package dev.sellerkit.reconkit.domain.enums;

/** Lifecycle of a single payment transaction, on either side of the books. */
public enum TxnStatus {
    PAID,
    PARTIAL_REFUND,
    REFUND,
    CANCEL,
    CHARGEBACK
}
