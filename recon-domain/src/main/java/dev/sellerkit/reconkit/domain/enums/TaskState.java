package dev.sellerkit.reconkit.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * A reprocess task's state machine.
 *
 * <p>An idempotency key alone does not stop double processing. It stops a second
 * <em>request</em>, but says nothing about a request that is already in flight.
 * Every transition below is applied as a conditional update guarded by the current
 * state, so two workers that claim the same task cannot both advance it.
 */
public enum TaskState {
    PENDING,
    CLAIMED,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    /** Failed and could not be compensated. A human has to look at it. */
    ESCALATED;

    private static final Set<TaskState> TERMINAL =
            EnumSet.of(SUCCEEDED, COMPENSATED, ESCALATED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(TaskState next) {
        return switch (this) {
            case PENDING -> next == CLAIMED || next == FAILED;
            case CLAIMED -> next == IN_FLIGHT || next == FAILED;
            case IN_FLIGHT -> next == SUCCEEDED || next == FAILED;
            case SUCCEEDED -> next == COMPENSATING;
            case FAILED -> next == CLAIMED || next == ESCALATED;
            case COMPENSATING -> next == COMPENSATED || next == ESCALATED;
            case COMPENSATED, ESCALATED -> false;
        };
    }
}
