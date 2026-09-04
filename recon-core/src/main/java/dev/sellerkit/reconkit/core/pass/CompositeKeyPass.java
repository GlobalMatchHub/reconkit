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
import java.util.function.Function;

/**
 * Pass B. Composite keys, for rows where the transaction identifier is absent or was
 * regenerated on one side.
 *
 * <p>Two keys, tried in order. The approval number survives a retry that produced a new
 * order id, which is the common case after a network timeout at the terminal. The order
 * id survives a gateway that reissued its own identifier during a partial cancellation.
 * Both are paired with the amount, because either one alone will happily match a
 * transaction and its own refund.
 */
public final class CompositeKeyPass implements MatchingPass {

    @Override
    public MatchPass id() {
        return MatchPass.B_COMPOSITE_KEY;
    }

    @Override
    public void apply(MatchState state) {
        matchOn(state,
                e -> keyOf(e.getApprovalNo(), e.signedGross().minorUnits()),
                "approval no + amount");
        matchOn(state,
                e -> keyOf(e.getOrderId(), e.signedGross().minorUnits()),
                "order id + amount");
    }

    private static String keyOf(String part, long amount) {
        return (part == null || part.isBlank()) ? null : part + "|" + amount;
    }

    private void matchOn(MatchState state,
                         Function<dev.sellerkit.reconkit.domain.model.TransactionEntry, String> keyFn,
                         String label) {
        Map<String, Deque<Long>> statementsByKey = new LinkedHashMap<>();
        for (Long id : state.openStatementIds()) {
            String key = keyFn.apply(state.statement(id));
            if (key != null) {
                statementsByKey.computeIfAbsent(key, k -> new ArrayDeque<>()).add(id);
            }
        }

        List<MatchDraft> drafts = new ArrayList<>();
        for (Long ledgerId : List.copyOf(state.openLedgerIds())) {
            LedgerEntry ledger = state.ledger(ledgerId);
            String key = keyFn.apply(ledger);
            if (key == null) {
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
                    MatchPass.B_COMPOSITE_KEY,
                    0.95d,
                    label + " " + key,
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
