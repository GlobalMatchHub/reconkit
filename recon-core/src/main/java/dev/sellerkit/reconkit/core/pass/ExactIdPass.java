package dev.sellerkit.reconkit.core.pass;

import dev.sellerkit.reconkit.core.MatchState;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass A. Equality on the counterparty's own transaction identifier.
 *
 * <p>This is the only key both sides agree on by construction, so it runs first and
 * takes everything it can. Where a key appears more than once on one side, the pass
 * pairs them off in arrival order and leaves the surplus open rather than guessing;
 * the duplicate detector has already flagged it, and inventing a pairing here would
 * hide the very thing the operator needs to see.
 */
public final class ExactIdPass implements MatchingPass {

    @Override
    public MatchPass id() {
        return MatchPass.A_EXACT_ID;
    }

    @Override
    public void apply(MatchState state) {
        Map<String, Deque<Long>> statementsByKey = new LinkedHashMap<>();
        for (Long id : state.openStatementIds()) {
            String key = state.statement(id).getExternalTxnId();
            if (key != null && !key.isBlank()) {
                statementsByKey.computeIfAbsent(key, k -> new ArrayDeque<>()).add(id);
            }
        }

        List<MatchDraft> drafts = new ArrayList<>();
        for (Long ledgerId : List.copyOf(state.openLedgerIds())) {
            LedgerEntry ledger = state.ledger(ledgerId);
            String key = ledger.getExternalTxnId();
            if (key == null || key.isBlank()) {
                continue;
            }
            Deque<Long> candidates = statementsByKey.get(key);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            Long statementId = candidates.poll();
            StatementEntry statement = state.statement(statementId);
            drafts.add(new MatchDraft(
                    MatchType.ONE_TO_ONE,
                    MatchPass.A_EXACT_ID,
                    1.0d,
                    "txn id " + key,
                    List.of(ledgerId),
                    List.of(statementId),
                    ledger.signedGross().minorUnits(),
                    statement.signedGross().minorUnits(),
                    ledger.getFeeMinorUnits(),
                    statement.getFeeMinorUnits(),
                    ledger.getCurrency()));
        }
        drafts.forEach(state::accept);
    }
}
