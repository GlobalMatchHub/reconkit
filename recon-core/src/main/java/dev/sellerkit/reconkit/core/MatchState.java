package dev.sellerkit.reconkit.core;

import dev.sellerkit.reconkit.core.model.DiscrepancyDraft;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.core.model.ReconInput;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The working set of one run.
 *
 * <p>Rows leave the open sets the moment a pass claims them and never come back. That
 * single rule is what keeps the passes ordered: an exact identifier match is not allowed
 * to be reconsidered later by a scoring heuristic that happens to like a different pair
 * better. Cheap and certain wins, and it wins permanently.
 */
public final class MatchState {

    private final ReconInput input;
    private final Map<Long, LedgerEntry> ledgerById = new LinkedHashMap<>();
    private final Map<Long, StatementEntry> statementById = new LinkedHashMap<>();
    private final Set<Long> openLedger = new LinkedHashSet<>();
    private final Set<Long> openStatement = new LinkedHashSet<>();
    private final List<MatchDraft> matches = new ArrayList<>();
    private final List<DiscrepancyDraft> discrepancies = new ArrayList<>();
    private final Map<MatchPass, Integer> counters = new EnumMap<>(MatchPass.class);

    public MatchState(ReconInput input) {
        this.input = input;
        for (LedgerEntry entry : input.ledgerEntries()) {
            ledgerById.put(entry.getId(), entry);
            openLedger.add(entry.getId());
        }
        for (StatementEntry entry : input.statementEntries()) {
            statementById.put(entry.getId(), entry);
            openStatement.add(entry.getId());
        }
    }

    public ReconInput input() {
        return input;
    }

    public LedgerEntry ledger(Long id) {
        return ledgerById.get(id);
    }

    public StatementEntry statement(Long id) {
        return statementById.get(id);
    }

    public Set<Long> openLedgerIds() {
        return openLedger;
    }

    public Set<Long> openStatementIds() {
        return openStatement;
    }

    public boolean isOpen(MatchDraft draft) {
        return draft.ledgerEntryIds().stream().allMatch(openLedger::contains)
                && draft.statementEntryIds().stream().allMatch(openStatement::contains);
    }

    /** Claims every row in the draft. Throws if a row was already taken. */
    public void accept(MatchDraft draft) {
        for (Long id : draft.ledgerEntryIds()) {
            if (!openLedger.remove(id)) {
                throw new IllegalStateException("ledger entry " + id + " was already matched");
            }
        }
        for (Long id : draft.statementEntryIds()) {
            if (!openStatement.remove(id)) {
                throw new IllegalStateException("statement entry " + id + " was already matched");
            }
        }
        matches.add(draft);
        counters.merge(draft.pass(), 1, Integer::sum);
    }

    public void addDiscrepancy(DiscrepancyDraft draft) {
        discrepancies.add(draft);
    }

    public int indexOf(MatchDraft draft) {
        return matches.indexOf(draft);
    }

    public List<MatchDraft> matches() {
        return matches;
    }

    public List<DiscrepancyDraft> discrepancies() {
        return discrepancies;
    }

    public Map<MatchPass, Integer> counters() {
        return counters;
    }

    /**
     * Rows that belong to this run's date and ended up inside a match group.
     *
     * <p>Only in scope rows are counted. The window pulls in neighbouring dates so that a
     * late line can be matched, and counting those as well inflates every total by roughly
     * the width of the window: the reported match rate then depends on how wide the window
     * is rather than on how well the day reconciled.
     */
    public int matchedEntryCount() {
        int matched = 0;
        for (MatchDraft draft : matches) {
            for (Long id : draft.ledgerEntryIds()) {
                if (inScope(ledgerById.get(id))) {
                    matched++;
                }
            }
            matched += draft.statementEntryIds().size();
        }
        return matched;
    }

    public boolean inScope(dev.sellerkit.reconkit.domain.model.TransactionEntry entry) {
        return entry != null && entry.getBusinessDate().equals(input.businessDate());
    }

    public int inScopeLedgerCount() {
        return (int) ledgerById.values().stream().filter(this::inScope).count();
    }

    public long inScopeLedgerGross() {
        return ledgerById.values().stream()
                .filter(this::inScope)
                .mapToLong(entry -> entry.signedGross().minorUnits())
                .sum();
    }
}
