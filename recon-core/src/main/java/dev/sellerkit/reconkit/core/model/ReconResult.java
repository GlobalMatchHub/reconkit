package dev.sellerkit.reconkit.core.model;

import dev.sellerkit.reconkit.domain.enums.MatchPass;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ReconResult(
        List<MatchDraft> matches,
        List<DiscrepancyDraft> discrepancies,
        Map<MatchPass, Integer> matchesByPass,
        int ledgerCount,
        int statementCount,
        int matchedEntryCount,
        long ledgerGrossMinor,
        long statementGrossMinor) {

    public int countFor(MatchPass pass) {
        return matchesByPass.getOrDefault(pass, 0);
    }

    public static Map<MatchPass, Integer> emptyCounters() {
        return new EnumMap<>(MatchPass.class);
    }
}
