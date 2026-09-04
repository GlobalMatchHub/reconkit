package dev.sellerkit.reconkit.core;

import dev.sellerkit.reconkit.core.model.DiscrepancyDraft;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.core.model.ReconInput;
import dev.sellerkit.reconkit.core.model.ReconResult;
import dev.sellerkit.reconkit.core.pass.AggregatePass;
import dev.sellerkit.reconkit.core.pass.CompositeKeyPass;
import dev.sellerkit.reconkit.core.pass.ExactIdPass;
import dev.sellerkit.reconkit.core.pass.FuzzyAmountTimePass;
import dev.sellerkit.reconkit.core.pass.MatchingPass;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The engine. Rows in, matches and differences out, nothing else touched.
 *
 * <p>Given the same input it produces the same output, every time. That is not a
 * stylistic preference. A reconciliation result is something a counterparty may dispute
 * months later, and the only defence is being able to re run the exact night and get the
 * exact numbers back. Anything that reads the clock, queries a table, or depends on map
 * ordering would take that away.
 */
public final class ReconEngine {

    /** Bump when a pass changes behaviour. Stored on the run so old results stay readable. */
    public static final String RULE_VERSION = "2026.09.1";

    private static final Logger log = LoggerFactory.getLogger(ReconEngine.class);

    private final List<MatchingPass> passes;
    private final DiscrepancyClassifier classifier;

    public ReconEngine() {
        this(List.of(new ExactIdPass(), new CompositeKeyPass(), new FuzzyAmountTimePass(), new AggregatePass()),
                new DiscrepancyClassifier());
    }

    public ReconEngine(List<MatchingPass> passes, DiscrepancyClassifier classifier) {
        this.passes = List.copyOf(passes);
        this.classifier = classifier;
    }

    public ReconResult reconcile(ReconInput input) {
        MatchState state = new MatchState(input);

        // Duplicates are detected before any matching. A file loaded twice would
        // otherwise match itself pass by pass and report a clean night.
        classifier.classifyDuplicates(input.ledgerEntries(), true).forEach(state::addDiscrepancy);
        classifier.classifyDuplicates(input.statementEntries(), false).forEach(state::addDiscrepancy);

        for (MatchingPass pass : passes) {
            int before = state.matches().size();
            pass.apply(state);
            log.debug("pass {} matched {} groups, {} ledger and {} statement rows still open",
                    pass.id(), state.matches().size() - before,
                    state.openLedgerIds().size(), state.openStatementIds().size());
        }

        List<MatchDraft> matches = state.matches();
        for (int index = 0; index < matches.size(); index++) {
            classifier.classifyMatched(state, matches.get(index), index).forEach(state::addDiscrepancy);
        }
        classifier.classifyUnmatched(state).forEach(state::addDiscrepancy);

        List<DiscrepancyDraft> discrepancies = new ArrayList<>(state.discrepancies());

        return new ReconResult(
                List.copyOf(matches),
                List.copyOf(discrepancies),
                state.counters(),
                state.inScopeLedgerCount(),
                input.statementEntries().size(),
                state.matchedEntryCount(),
                state.inScopeLedgerGross(),
                input.statementEntries().stream().mapToLong(e -> e.signedGross().minorUnits()).sum());
    }
}
