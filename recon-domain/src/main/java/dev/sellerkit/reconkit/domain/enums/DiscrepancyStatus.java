package dev.sellerkit.reconkit.domain.enums;

public enum DiscrepancyStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    /** Written off: real money lost, accepted as a cost. */
    WRITTEN_OFF,
    /** Not a real difference. Timing or a known counterparty quirk. */
    ACCEPTED
}
