package dev.sellerkit.reconkit.reprocess;

import dev.sellerkit.reconkit.domain.enums.JobState;

public record JobOutcome(
        JobState state,
        int succeeded,
        int failed,
        int compensated,
        int escalated,
        String reason) {

    public boolean needsAttention() {
        return state == JobState.ESCALATED || state == JobState.FAILED;
    }
}
