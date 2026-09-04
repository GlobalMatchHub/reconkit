package dev.sellerkit.reconkit.ingest;

import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import java.util.List;

/**
 * What one file produced.
 *
 * <p>Rejected rows are returned rather than thrown, because a settlement file with three
 * bad rows out of forty thousand still has to be loaded tonight. The three are recorded
 * with their line numbers and the reason, and the rest goes in.
 */
public record IngestResult(
        String contentHash,
        List<TransactionEntry> entries,
        List<RejectedRow> rejected) {

    public record RejectedRow(int lineNumber, String reason, String rawLine) {
    }

    public int loadedCount() {
        return entries.size();
    }

    public int rejectedCount() {
        return rejected.size();
    }
}
