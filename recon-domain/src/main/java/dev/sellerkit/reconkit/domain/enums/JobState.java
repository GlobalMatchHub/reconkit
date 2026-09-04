package dev.sellerkit.reconkit.domain.enums;

public enum JobState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    /** Some tasks succeeded, some failed, and the successes were rolled back. */
    COMPENSATED,
    /** Rollback itself failed. Escalated to an operator. */
    ESCALATED,
    FAILED
}
