package dev.sellerkit.reconkit.core.pass;

import dev.sellerkit.reconkit.core.MatchState;
import dev.sellerkit.reconkit.core.model.MatchDraft;
import dev.sellerkit.reconkit.domain.enums.MatchPass;
import dev.sellerkit.reconkit.domain.enums.MatchType;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.money.Money;
import dev.sellerkit.reconkit.domain.money.Tolerance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pass D. Many rows on one side against one row on the other.
 *
 * <p>Marketplaces and app stores do not report per transaction. They report a payout,
 * and the payout is the sum of a batch that only they know the membership of. Everything
 * in such a file looks missing to a one to one matcher, so the day fails reconciliation
 * while being perfectly correct.
 *
 * <p>The obvious approach, searching for any subset of the open rows that adds up to the
 * payout, is the subset sum problem and is both exponential and wrong: with enough small
 * transactions some subset always adds up, and the matcher starts inventing groupings.
 * So the search is not over subsets. Rows are grouped by an identifier both sides carry,
 * the order id or the merchant number, and only a complete group is compared against a
 * single line. That makes the pass linear, and more importantly it makes every match it
 * produces explainable by something other than arithmetic coincidence.
 */
public final class AggregatePass implements MatchingPass {

    private static final int MAX_GROUP_SIZE = 50;

    @Override
    public MatchPass id() {
        return MatchPass.D_AGGREGATE;
    }

    @Override
    public void apply(MatchState state) {
        Tolerance tolerance = state.input().terms().amountTolerance();

        collapse(state, tolerance, TransactionEntry::getOrderId, "order id");
        collapse(state, tolerance, TransactionEntry::getMerchantNo, "merchant no");
    }

    private void collapse(MatchState state, Tolerance tolerance,
                          Function<TransactionEntry, String> groupKey, String label) {
        // Many ledger rows to one statement line.
        Map<String, List<Long>> ledgerGroups = groupOpen(state, true, groupKey);
        Map<String, Long> statementSingles = singlesByKey(state, false, groupKey);
        List<MatchDraft> drafts = new ArrayList<>();

        for (Map.Entry<String, List<Long>> group : ledgerGroups.entrySet()) {
            if (group.getValue().size() < 2 || group.getValue().size() > MAX_GROUP_SIZE) {
                continue;
            }
            Long statementId = statementSingles.get(group.getKey());
            if (statementId == null) {
                continue;
            }
            long ledgerSum = sum(state, group.getValue(), true);
            long statementAmount = state.statement(statementId).signedGross().minorUnits();
            String currency = state.statement(statementId).getCurrency();
            if (!tolerance.accepts(Money.of(ledgerSum, currency), Money.of(statementAmount, currency))) {
                continue;
            }
            drafts.add(new MatchDraft(
                    MatchType.MANY_TO_ONE,
                    MatchPass.D_AGGREGATE,
                    0.90d,
                    "%d ledger rows sum to one statement line, grouped by %s %s"
                            .formatted(group.getValue().size(), label, group.getKey()),
                    List.copyOf(group.getValue()),
                    List.of(statementId),
                    ledgerSum,
                    statementAmount,
                    sumFee(state, group.getValue(), true),
                    state.statement(statementId).getFeeMinorUnits(),
                    currency));
        }
        drafts.forEach(state::accept);

        // One ledger row against many statement lines: instalments, split captures.
        Map<String, List<Long>> statementGroups = groupOpen(state, false, groupKey);
        Map<String, Long> ledgerSingles = singlesByKey(state, true, groupKey);
        List<MatchDraft> reverse = new ArrayList<>();

        for (Map.Entry<String, List<Long>> group : statementGroups.entrySet()) {
            if (group.getValue().size() < 2 || group.getValue().size() > MAX_GROUP_SIZE) {
                continue;
            }
            Long ledgerId = ledgerSingles.get(group.getKey());
            if (ledgerId == null) {
                continue;
            }
            long statementSum = sum(state, group.getValue(), false);
            long ledgerAmount = state.ledger(ledgerId).signedGross().minorUnits();
            String currency = state.ledger(ledgerId).getCurrency();
            if (!tolerance.accepts(Money.of(ledgerAmount, currency), Money.of(statementSum, currency))) {
                continue;
            }
            reverse.add(new MatchDraft(
                    MatchType.ONE_TO_MANY,
                    MatchPass.D_AGGREGATE,
                    0.90d,
                    "one ledger row against %d statement lines, grouped by %s %s"
                            .formatted(group.getValue().size(), label, group.getKey()),
                    List.of(ledgerId),
                    List.copyOf(group.getValue()),
                    ledgerAmount,
                    statementSum,
                    state.ledger(ledgerId).getFeeMinorUnits(),
                    sumFee(state, group.getValue(), false),
                    currency));
        }
        reverse.forEach(state::accept);
    }

    private Map<String, List<Long>> groupOpen(MatchState state, boolean ledgerSide,
                                              Function<TransactionEntry, String> groupKey) {
        Map<String, List<Long>> groups = new LinkedHashMap<>();
        for (Long id : ledgerSide ? state.openLedgerIds() : state.openStatementIds()) {
            TransactionEntry entry = ledgerSide ? state.ledger(id) : state.statement(id);
            String key = groupKey.apply(entry);
            if (key != null && !key.isBlank()) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
            }
        }
        return groups;
    }

    /** Keys that appear exactly once on the given side. A group only matches a single line. */
    private Map<String, Long> singlesByKey(MatchState state, boolean ledgerSide,
                                           Function<TransactionEntry, String> groupKey) {
        Map<String, List<Long>> groups = groupOpen(state, ledgerSide, groupKey);
        Map<String, Long> singles = new LinkedHashMap<>();
        groups.forEach((key, ids) -> {
            if (ids.size() == 1) {
                singles.put(key, ids.get(0));
            }
        });
        return singles;
    }

    private long sum(MatchState state, List<Long> ids, boolean ledgerSide) {
        long total = 0L;
        for (Long id : ids) {
            total += (ledgerSide ? state.ledger(id) : state.statement(id)).signedGross().minorUnits();
        }
        return total;
    }

    private long sumFee(MatchState state, List<Long> ids, boolean ledgerSide) {
        long total = 0L;
        for (Long id : ids) {
            total += (ledgerSide ? state.ledger(id) : state.statement(id)).getFeeMinorUnits();
        }
        return total;
    }
}
